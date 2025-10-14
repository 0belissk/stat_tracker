package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

class AuditRepositoryTest {

  private final DynamoDbClient dynamoDbClient = Mockito.mock(DynamoDbClient.class);

  @Test
  void writeReportSentCreatesCorrectAuditItem() {
    AuditRepository repository = new AuditRepository(dynamoDbClient, "test-table");

    Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
    repository.writeReportSent("report-123", "coach-456", timestamp);

    ArgumentCaptor<PutItemRequest> requestCaptor = 
        ArgumentCaptor.forClass(PutItemRequest.class);
    verify(dynamoDbClient).putItem(requestCaptor.capture());

    PutItemRequest request = requestCaptor.getValue();
    assertEquals("test-table", request.tableName());
    
    // Verify PK
    assertEquals("REPORT#report-123", request.item().get("PK").s());
    
    // Verify SK contains timestamp and SENT
    String sk = request.item().get("SK").s();
    assertEquals("AUDIT#2024-01-15T10:30:00Z#SENT", sk);
    
    // Verify actor attributes
    assertEquals("coach-456", request.item().get("actorId").s());
    assertEquals("COACH", request.item().get("actorRole").s());
  }
}
