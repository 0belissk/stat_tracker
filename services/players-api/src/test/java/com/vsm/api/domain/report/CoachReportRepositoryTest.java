package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
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
    assertEquals("attribute_not_exists(SK)", request.conditionExpression());

    Map<String, AttributeValue> item = request.item();
    assertEquals("PLAYER#player-1", item.get("PK").s());
    assertEquals("REPORT#2024-01-01T00:00:00Z", item.get("SK").s());
    assertEquals("2024-01-01T00:00:00Z", item.get("reportId").s());
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
        report.playerId(), report.reportTimestamp(), "reports/player-1/report.txt");

    ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
    verify(dynamoDbClient).updateItem(captor.capture());

    UpdateItemRequest request = captor.getValue();
    assertEquals("coach_reports", request.tableName());
    assertEquals("PLAYER#player-1", request.key().get("PK").s());
    assertEquals("REPORT#2024-01-01T00:00:00Z", request.key().get("SK").s());
    assertEquals("SET s3Key = if_not_exists(s3Key, :s)", request.updateExpression());
    assertEquals(
        "reports/player-1/report.txt",
        request.expressionAttributeValues().get(":s").s());
  }
}
