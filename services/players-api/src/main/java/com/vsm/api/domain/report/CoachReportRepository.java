package com.vsm.api.domain.report;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

  private static final String REPORT_SORT_KEY_PREFIX = "REPORT#";
  private static final DateTimeFormatter SORT_KEY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SORT_KEY_PARSER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

  public CoachReportRepository(
      DynamoDbClient dynamoDbClient, @Value("${app.reports.table-name}") String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  public void save(CoachReport report) {
    Map<String, AttributeValue> item = new HashMap<>();
    String reportTimestampIso = report.reportTimestamp().toString();
    String reportTimestampKey = toSortKeyTimestamp(report.reportTimestamp());
    item.put("PK", AttributeValue.fromS("PLAYER#" + report.playerId()));
    item.put(
        "SK",
        AttributeValue.fromS(buildReportSortKey(report.reportTimestamp(), report.reportId())));
    item.put("reportId", AttributeValue.fromS(report.reportId()));
    item.put("playerId", AttributeValue.fromS(report.playerId()));
    item.put("reportTimestamp", AttributeValue.fromS(reportTimestampIso));
    item.put("reportTimestampKey", AttributeValue.fromS(reportTimestampKey));
    item.put("coachId", AttributeValue.fromS(report.coachId()));
    item.put("playerEmail", AttributeValue.fromS(report.playerEmail()));
    item.put("createdAt", AttributeValue.fromS(Instant.now().toString()));
    item.put("entityType", AttributeValue.fromS("REPORT"));
    item.put("GSI1PK", AttributeValue.fromS("REPORT#" + report.reportId()));
    item.put("GSI1SK", AttributeValue.fromS("REPORT#" + report.reportId()));
    Map<String, AttributeValue> cats = new HashMap<>();
    report.categories().forEach((k, v) -> cats.put(k, AttributeValue.fromS(v)));
    item.put("categories", AttributeValue.fromM(cats));

    PutItemRequest request =
        PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
            .build();
    dynamoDbClient.putItem(request);
  }

  /** Idempotent update to set s3Key only if absent. */
  public void updateS3Key(String playerId, Instant reportTimestamp, String reportId, String s3Key) {
    Map<String, AttributeValue> key =
        Map.of(
            "PK", AttributeValue.fromS("PLAYER#" + playerId),
            "SK",
            AttributeValue.fromS(buildReportSortKey(reportTimestamp, reportId)));
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
      String trimmed = cursor.trim();
      String sortKey;
      String[] parts = trimmed.split("#", 2);
      if (parts.length == 2) {
        Instant instant = Instant.parse(parts[0]);
        sortKey = buildReportSortKey(instant, parts[1]);
      } else {
        Instant instant = Instant.parse(trimmed);
        sortKey = buildReportSortKey(instant, trimmed);
      }
      request =
          request.exclusiveStartKey(
              Map.of(
                  "PK", AttributeValue.fromS("PLAYER#" + playerId),
                  "SK", AttributeValue.fromS(sortKey)));
    }

    QueryResponse response = dynamoDbClient.query(request.build());
    List<Map<String, AttributeValue>> rawItems = response.hasItems() ? response.items() : List.of();
    List<PlayerReportSummary> items = rawItems.stream().map(this::toSummary).toList();

    String nextCursor = null;
    if (response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()) {
      if (!items.isEmpty()) {
        PlayerReportSummary last = items.get(items.size() - 1);
        nextCursor = "%s#%s".formatted(last.reportTimestamp().toString(), last.reportId());
      } else {
        AttributeValue sk = response.lastEvaluatedKey().get("SK");
        if (sk != null && sk.s() != null && sk.s().startsWith(REPORT_SORT_KEY_PREFIX)) {
          String suffix = sk.s().substring(REPORT_SORT_KEY_PREFIX.length());
          String[] parts = suffix.split("#", 2);
          if (parts.length == 2) {
            Instant instant = fromSortKeyTimestamp(parts[0]);
            nextCursor = "%s#%s".formatted(instant.toString(), parts[1]);
          } else if (parts.length == 1 && !parts[0].isBlank()) {
            Instant instant = Instant.parse(parts[0]);
            nextCursor = "%s#%s".formatted(instant.toString(), parts[0]);
          }
        }
      }
    }

    return new PlayerReportPage(items, nextCursor);
  }

  private PlayerReportSummary toSummary(Map<String, AttributeValue> item) {
    String reportId = stringValue(item.get("reportId"));
    Instant reportTimestamp = null;
    String reportTimestampValue = stringValue(item.get("reportTimestamp"));
    if (reportTimestampValue != null) {
      reportTimestamp = Instant.parse(reportTimestampValue);
    }
    if (reportTimestamp == null) {
      reportTimestamp = parseReportTimestampFromSk(stringValue(item.get("SK")));
    }
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

  private String toSortKeyTimestamp(Instant instant) {
    return SORT_KEY_FORMATTER.format(instant);
  }

  private Instant fromSortKeyTimestamp(String timestampKey) {
    LocalDateTime localDateTime = LocalDateTime.parse(timestampKey, SORT_KEY_PARSER);
    return localDateTime.toInstant(ZoneOffset.UTC);
  }

  private String buildReportSortKey(Instant reportTimestamp, String reportId) {
    return REPORT_SORT_KEY_PREFIX + toSortKeyTimestamp(reportTimestamp) + "#" + reportId;
  }

  private Instant parseReportTimestampFromSk(String sk) {
    if (sk == null || !sk.startsWith(REPORT_SORT_KEY_PREFIX)) {
      return Instant.EPOCH;
    }
    String suffix = sk.substring(REPORT_SORT_KEY_PREFIX.length());
    String[] parts = suffix.split("#", 2);
    String candidate = parts[0];
    if (candidate.contains(":")) {
      return Instant.parse(candidate);
    }
    return fromSortKeyTimestamp(candidate);
  }
}
