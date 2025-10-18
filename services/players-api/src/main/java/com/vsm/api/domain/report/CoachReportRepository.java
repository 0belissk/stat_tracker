package com.vsm.api.domain.report;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
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

  public PlayerReportPage listReports(String playerId, int limit, String cursor) {
    QueryRequest.Builder request =
        QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("PK = :pk AND begins_with(SK, :skprefix)")
            .expressionAttributeValues(
                Map.of(
                    ":pk", AttributeValue.fromS("PLAYER#" + playerId),
                    ":skprefix", AttributeValue.fromS("REPORT#")))
            .scanIndexForward(false)
            .limit(limit);

    if (cursor != null && !cursor.isBlank()) {
      request =
          request.exclusiveStartKey(
              Map.of(
                  "PK", AttributeValue.fromS("PLAYER#" + playerId),
                  "SK", AttributeValue.fromS("REPORT#" + cursor)));
    }

    QueryResponse response = dynamoDbClient.query(request.build());
    List<Map<String, AttributeValue>> rawItems = response.hasItems() ? response.items() : List.of();
    List<PlayerReportSummary> items = rawItems.stream().map(this::toSummary).toList();

    String nextCursor = null;
    if (response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()) {
      AttributeValue sk = response.lastEvaluatedKey().get("SK");
      if (sk != null && sk.s() != null && sk.s().startsWith("REPORT#")) {
        nextCursor = sk.s().substring("REPORT#".length());
      }
    }

    return new PlayerReportPage(items, nextCursor);
  }

  private PlayerReportSummary toSummary(Map<String, AttributeValue> item) {
    String reportId = stringValue(item.get("reportId"));
    String sk = stringValue(item.get("SK"));
    Instant reportTimestamp = Instant.parse(sk.substring("REPORT#".length()));
    Instant createdAt = reportTimestamp;
    String createdAtValue = stringValue(item.get("createdAt"));
    if (createdAtValue != null) {
      createdAt = Instant.parse(createdAtValue);
    }
    String coachId = stringValue(item.get("coachId"));
    String s3Key = stringValue(item.get("s3Key"));
    return new PlayerReportSummary(reportId, reportTimestamp, createdAt, coachId, s3Key);
  }

  private String stringValue(AttributeValue value) {
    return value == null ? null : value.s();
  }
}
