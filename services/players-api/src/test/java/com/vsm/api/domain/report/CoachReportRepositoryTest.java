package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

class CoachReportRepositoryTest {

  private final DynamoDbClient dynamoDbClient = Mockito.mock(DynamoDbClient.class);

  private final CoachReportRepository repository =
      new CoachReportRepository(dynamoDbClient, "coach_reports");

  @Test
  void saveBuildsCorrectPutItemRequest() {
    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "great"),
            Instant.parse("2024-01-01T00:00:00Z"),
            "2024-01-01T00:00:00Z",
            "coach-123");

    repository.save(report);

    ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(dynamoDbClient).putItem(captor.capture());

    PutItemRequest request = captor.getValue();
    assertEquals("coach_reports", request.tableName());
    assertEquals(
        "attribute_not_exists(PK) AND attribute_not_exists(SK)",
        request.conditionExpression());

    Map<String, AttributeValue> item = request.item();
    assertEquals("PLAYER#player-1", item.get("PK").s());
    assertEquals("REPORT#20240101T000000#2024-01-01T00:00:00Z", item.get("SK").s());
    assertEquals("2024-01-01T00:00:00Z", item.get("reportId").s());
    assertEquals("player-1", item.get("playerId").s());
    assertEquals("2024-01-01T00:00:00Z", item.get("reportTimestamp").s());
    assertEquals("20240101T000000", item.get("reportTimestampKey").s());
    assertEquals("coach-123", item.get("coachId").s());
    assertEquals("player@example.com", item.get("playerEmail").s());
    assertNotNull(item.get("createdAt").s());
    assertEquals("great", item.get("categories").m().get("serving").s());
    assertFalse(item.containsKey("s3Key"));
  }

  @Test
  void updateS3KeySetsValueIfMissing() {
    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "great"),
            Instant.parse("2024-01-01T00:00:00Z"),
            "2024-01-01T00:00:00Z",
            "coach-123");

    repository.updateS3Key(
        report.playerId(), report.reportTimestamp(), report.reportId(), "reports/player-1/report.txt");

    ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
    verify(dynamoDbClient).updateItem(captor.capture());

    UpdateItemRequest request = captor.getValue();
    assertEquals("coach_reports", request.tableName());
    assertEquals("PLAYER#player-1", request.key().get("PK").s());
    assertEquals(
        "REPORT#20240101T000000#2024-01-01T00:00:00Z", request.key().get("SK").s());
    assertEquals("SET s3Key = if_not_exists(s3Key, :s)", request.updateExpression());
    assertEquals("reports/player-1/report.txt", request.expressionAttributeValues().get(":s").s());
  }

  @Test
  void listReportsQueriesInDescendingOrderAndMapsResponse() {
    when(dynamoDbClient.query(Mockito.any(QueryRequest.class)))
        .thenReturn(
            QueryResponse.builder()
                .items(
                    Map.of(
                        "PK", AttributeValue.fromS("PLAYER#player-1"),
                        "SK",
                        AttributeValue.fromS("REPORT#20240101T000000#2024-01-01T00:00:00Z"),
                        "reportId", AttributeValue.fromS("2024-01-01T00:00:00Z"),
                        "reportTimestamp", AttributeValue.fromS("2024-01-01T00:00:00Z"),
                        "reportTimestampKey", AttributeValue.fromS("20240101T000000"),
                        "coachId", AttributeValue.fromS("coach-123"),
                        "createdAt", AttributeValue.fromS("2024-01-01T00:05:00Z"),
                        "s3Key", AttributeValue.fromS("reports/player-1/report.txt")))
                .lastEvaluatedKey(
                    Map.of(
                        "PK", AttributeValue.fromS("PLAYER#player-1"),
                        "SK",
                        AttributeValue.fromS("REPORT#20240101T000000#2024-01-01T00:00:00Z")))
                .build());

    PlayerReportPage page = repository.listReports("player-1", 10, "2024-01-03T00:00:00Z");

    ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
    verify(dynamoDbClient).query(captor.capture());
    QueryRequest request = captor.getValue();
    assertEquals("coach_reports", request.tableName());
    assertEquals("PK = :pk AND begins_with(SK, :skprefix)", request.keyConditionExpression());
    assertEquals("PLAYER#player-1", request.expressionAttributeValues().get(":pk").s());
    assertEquals("REPORT#", request.expressionAttributeValues().get(":skprefix").s());
    assertFalse(request.scanIndexForward());
    assertEquals(10, request.limit().intValue());
    assertEquals(
        "REPORT#20240103T000000#2024-01-03T00:00:00Z",
        request.exclusiveStartKey().get("SK").s());

    assertEquals(1, page.items().size());
    PlayerReportSummary summary = page.items().get(0);
    assertEquals("2024-01-01T00:00:00Z", summary.reportId());
    assertEquals(Instant.parse("2024-01-01T00:00:00Z"), summary.reportTimestamp());
    assertEquals(Instant.parse("2024-01-01T00:05:00Z"), summary.createdAt());
    assertEquals("coach-123", summary.coachId());
    assertEquals("reports/player-1/report.txt", summary.s3Key());
    assertEquals("2024-01-01T00:00:00Z#2024-01-01T00:00:00Z", page.nextCursor());
  }
}
