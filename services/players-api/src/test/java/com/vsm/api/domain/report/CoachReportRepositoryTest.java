package com.vsm.api.domain.report;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

class CoachReportRepositoryTest {

  public static void main(String[] args) {
    CapturingDynamoDbClient dynamoDbClient = new CapturingDynamoDbClient();
    CoachReportRepository repository = new CoachReportRepository(dynamoDbClient, "coach_reports");

    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "great"),
            Instant.parse("2024-01-01T00:00:00Z"),
            "2024-01-01T00:00:00Z",
            "coach-123");

    repository.save(report);

    PutItemRequest request = dynamoDbClient.lastRequest;
    if (request == null) {
      throw new AssertionError("Expected save() to call DynamoDbClient#putItem");
    }

    if (!"coach_reports".equals(request.tableName())) {
      throw new AssertionError("Unexpected table name: " + request.tableName());
    }
    if (!CoachReportRepository.REPORT_SORT_KEY_NOT_EXISTS_CONDITION
        .equals(request.conditionExpression())) {
      throw new AssertionError("Unexpected condition expression: " + request.conditionExpression());
    }

    Map<String, AttributeValue> item = new HashMap<>(request.item());
    expectEqual("PK", "PLAYER#player-1", item.remove("PK").s());
    expectEqual("SK", "REPORT#2024-01-01T00:00:00Z", item.remove("SK").s());
    expectEqual("reportId", "2024-01-01T00:00:00Z", item.remove("reportId").s());
    expectEqual("coachId", "coach-123", item.remove("coachId").s());
    expectEqual("playerEmail", "player@example.com", item.remove("playerEmail").s());

    AttributeValue createdAt = item.remove("createdAt");
    if (createdAt == null || createdAt.s() == null || createdAt.s().isBlank()) {
      throw new AssertionError("Expected createdAt to contain a timestamp");
    }

    AttributeValue categories = item.remove("categories");
    if (categories == null || categories.m() == null) {
      throw new AssertionError("Expected categories to contain a map");
    }
    expectEqual("categories.serving", "great", categories.m().get("serving").s());

    if (!item.isEmpty()) {
      throw new AssertionError("Unexpected attributes in item: " + item.keySet());
    }

    System.out.println("CoachReportRepositoryTest passed");
  }

  private static void expectEqual(String field, String expected, String actual) {
    if (!expected.equals(actual)) {
      throw new AssertionError(
          "Unexpected value for " + field + ": expected=" + expected + ", actual=" + actual);
    }
  }

  private static final class CapturingDynamoDbClient implements DynamoDbClient {
    private PutItemRequest lastRequest;

    @Override
    public void putItem(PutItemRequest request) {
      this.lastRequest = request;
    }
  }
}
