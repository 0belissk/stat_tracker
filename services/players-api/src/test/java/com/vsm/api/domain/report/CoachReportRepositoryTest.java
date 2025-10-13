package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    assertEquals("attribute_not_exists(PK)", request.conditionExpression());

    Map<String, AttributeValue> item = request.item();
    assertEquals("PLAYER#player-1", item.get("PK").s());
    assertEquals("REPORT#2024-01-01T00:00:00Z", item.get("SK").s());
    assertEquals("2024-01-01T00:00:00Z", item.get("reportId").s());
    assertEquals("coach-123", item.get("coachId").s());
    assertEquals("player@example.com", item.get("playerEmail").s());
    assertNotNull(item.get("createdAt").s());
    assertEquals("great", item.get("categories").m().get("serving").s());
  }
}
