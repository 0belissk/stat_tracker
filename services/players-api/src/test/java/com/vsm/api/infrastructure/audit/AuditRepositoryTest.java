package com.vsm.api.infrastructure.audit;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

class AuditRepositoryTest {

  @Test
  void writesAuditSent() {
    DynamoDbClient ddb = Mockito.mock(DynamoDbClient.class);
    AuditRepository repo = new AuditRepository(ddb, "vsm-main");
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    repo.writeSent("r1", "c1", now);
    ArgumentCaptor<PutItemRequest> cap = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(ddb).putItem(cap.capture());
    PutItemRequest r = cap.getValue();
    assert "REPORT#r1".equals(r.item().get("PK").s());
    assert r.item().get("SK").s().startsWith("AUDIT#2025-01-01T00:00:00Z#SENT");
  }
}
