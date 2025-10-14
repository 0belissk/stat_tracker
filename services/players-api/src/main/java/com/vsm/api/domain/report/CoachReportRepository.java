package com.vsm.api.domain.report;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
public class CoachReportRepository {

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public CoachReportRepository(
      DynamoDbClient dynamoDbClient, @Value("${app.reports.table-name}") String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  public void save(CoachReport report, String s3Key) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("PK", AttributeValue.builder().s("PLAYER#" + report.playerId()).build());
    item.put("SK", AttributeValue.builder().s("REPORT#" + report.reportTimestamp()).build());
    item.put("reportId", AttributeValue.builder().s(report.reportId()).build());
    item.put("coachId", AttributeValue.builder().s(report.coachId()).build());
    item.put("playerEmail", AttributeValue.builder().s(report.playerEmail()).build());
    item.put("createdAt", AttributeValue.builder().s(Instant.now().toString()).build());
    item.put("s3Key", AttributeValue.builder().s(s3Key).build());

    Map<String, AttributeValue> categories = new HashMap<>();
    report.categories()
        .forEach((key, value) -> categories.put(key, AttributeValue.builder().s(value).build()));
    item.put("categories", AttributeValue.builder().m(categories).build());

    PutItemRequest request =
        PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .conditionExpression("attribute_not_exists(SK)")
            .build();

    dynamoDbClient.putItem(request);
  }

  public void saveAuditEntry(CoachReport report, Instant auditTimestamp) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("PK", AttributeValue.builder().s("REPORT#" + report.reportId()).build());
    item.put(
        "SK",
        AttributeValue.builder()
            .s("AUDIT#" + auditTimestamp.toString() + "#SENT")
            .build());
    item.put("actorId", AttributeValue.builder().s(report.coachId()).build());
    item.put("actorRole", AttributeValue.builder().s("COACH").build());
    item.put("createdAt", AttributeValue.builder().s(auditTimestamp.toString()).build());

    PutItemRequest request =
        PutItemRequest.builder().tableName(tableName).item(item).build();

    dynamoDbClient.putItem(request);
  }
}
