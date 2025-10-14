package com.vsm.api.domain.report;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class CoachReportRepository {
  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public CoachReportRepository(
      DynamoDbClient dynamoDbClient, @Value("${app.reports.table-name}") String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  public void save(CoachReport report) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("PK", AttributeValue.fromS("PLAYER#" + report.playerId()));
    item.put("SK", AttributeValue.fromS("REPORT#" + report.reportTimestamp()));
    item.put("reportId", AttributeValue.fromS(report.reportId()));
    item.put("coachId", AttributeValue.fromS(report.coachId()));
    item.put("playerEmail", AttributeValue.fromS(report.playerEmail()));
    item.put("createdAt", AttributeValue.fromS(Instant.now().toString()));
    Map<String, AttributeValue> cats = new HashMap<>();
    report.categories().forEach((k, v) -> cats.put(k, AttributeValue.fromS(v)));
    item.put("categories", AttributeValue.fromM(cats));

    PutItemRequest request =
        PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .conditionExpression("attribute_not_exists(SK)")
            .build();
    dynamoDbClient.putItem(request);
  }

  /** Idempotent update to set s3Key only if absent. */
  public void updateS3Key(String playerId, Instant reportTimestamp, String s3Key) {
    Map<String, AttributeValue> key =
        Map.of(
            "PK", AttributeValue.fromS("PLAYER#" + playerId),
            "SK", AttributeValue.fromS("REPORT#" + reportTimestamp));
    UpdateItemRequest req =
        UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression("SET s3Key = if_not_exists(s3Key, :s)")
            .expressionAttributeValues(Map.of(":s", AttributeValue.fromS(s3Key)))
            .build();
    dynamoDbClient.updateItem(req);
  }
}
