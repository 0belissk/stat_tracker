package com.vsm.api.domain.report;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
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
    item.put("PK", AttributeValue.builder().s("PLAYER#" + report.playerId()).build());
    item.put("SK", AttributeValue.builder().s("REPORT#" + report.reportTimestamp()).build());
    item.put("reportId", AttributeValue.builder().s(report.reportId()).build());
    item.put("coachId", AttributeValue.builder().s(report.coachId()).build());
    item.put("playerEmail", AttributeValue.builder().s(report.playerEmail()).build());
    item.put("createdAt", AttributeValue.builder().s(Instant.now().toString()).build());

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

  /**
   * Updates the s3Key attribute for an existing report.
   * Uses UpdateItem to set the s3Key, tolerating if already set.
   *
   * @param playerId the player ID
   * @param reportTimestamp the report timestamp
   * @param s3Key the S3 key to set
   */
  public void updateS3Key(String playerId, Instant reportTimestamp, String s3Key) {
    Map<String, AttributeValue> key = new HashMap<>();
    key.put("PK", AttributeValue.builder().s("PLAYER#" + playerId).build());
    key.put("SK", AttributeValue.builder().s("REPORT#" + reportTimestamp).build());

    Map<String, AttributeValueUpdate> updates = new HashMap<>();
    updates.put("s3Key", AttributeValueUpdate.builder()
        .value(AttributeValue.builder().s(s3Key).build())
        .action(AttributeAction.PUT)
        .build());

    UpdateItemRequest request = UpdateItemRequest.builder()
        .tableName(tableName)
        .key(key)
        .attributeUpdates(updates)
        .build();

    dynamoDbClient.updateItem(request);
  }
}
