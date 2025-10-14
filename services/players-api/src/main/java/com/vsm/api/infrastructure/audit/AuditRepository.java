package com.vsm.api.infrastructure.audit;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/** Writes AUDIT entries (SENT). */
@Repository
public class AuditRepository {
  private final DynamoDbClient ddb;
  private final String table;

  public AuditRepository(DynamoDbClient ddb, @Value("${app.reports.table-name}") String table) {
    this.ddb = ddb;
    this.table = table;
  }

  public void writeSent(String reportId, String coachId, Instant at) {
    String pk = "REPORT#" + reportId;
    String sk = "AUDIT#" + at + "#SENT";
    PutItemRequest req =
        PutItemRequest.builder()
            .tableName(table)
            .item(
                Map.of(
                    "PK", AttributeValue.fromS(pk),
                    "SK", AttributeValue.fromS(sk),
                    "actorId", AttributeValue.fromS(coachId),
                    "actorRole", AttributeValue.fromS("COACH"),
                    "createdAt", AttributeValue.fromS(at.toString())))
            .build();
    ddb.putItem(req);
  }
}
