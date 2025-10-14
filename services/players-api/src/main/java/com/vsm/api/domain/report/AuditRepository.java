package com.vsm.api.domain.report;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * Repository for writing audit log entries to DynamoDB.
 * Audit entries track report lifecycle events.
 */
@Repository
public class AuditRepository {

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public AuditRepository(
      DynamoDbClient dynamoDbClient,
      @Value("${app.reports.table-name}") String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  /**
   * Writes an audit log entry when a report is created and sent.
   *
   * @param reportId the report ID
   * @param coachId the coach ID (actor)
   * @param timestamp the event timestamp
   */
  public void writeReportSent(String reportId, String coachId, Instant timestamp) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("PK", AttributeValue.builder().s("REPORT#" + reportId).build());
    item.put("SK", AttributeValue.builder().s("AUDIT#" + timestamp + "#SENT").build());
    item.put("actorId", AttributeValue.builder().s(coachId).build());
    item.put("actorRole", AttributeValue.builder().s("COACH").build());

    PutItemRequest request = PutItemRequest.builder()
        .tableName(tableName)
        .item(item)
        .build();

    dynamoDbClient.putItem(request);
  }
}
