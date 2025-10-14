package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

class ReportEventPublisherTest {

  private final EventBridgeClient eventBridgeClient = Mockito.mock(EventBridgeClient.class);

  @Test
  void publishReportCreatedSendsCorrectEvent() {
    ReportEventPublisher publisher = new ReportEventPublisher(
        eventBridgeClient,
        "my-event-bus",
        "com.vsm.reports",
        "report.created"
    );

    publisher.publishReportCreated("player-123", "report-456", "path/to/report.txt");

    ArgumentCaptor<PutEventsRequest> requestCaptor = 
        ArgumentCaptor.forClass(PutEventsRequest.class);
    verify(eventBridgeClient).putEvents(requestCaptor.capture());

    PutEventsRequest request = requestCaptor.getValue();
    assertEquals(1, request.entries().size());

    PutEventsRequestEntry entry = request.entries().get(0);
    assertEquals("my-event-bus", entry.eventBusName());
    assertEquals("com.vsm.reports", entry.source());
    assertEquals("report.created", entry.detailType());
    
    // Verify detail JSON contains expected fields
    String detail = entry.detail();
    assertTrue(detail.contains("\"playerId\":\"player-123\""));
    assertTrue(detail.contains("\"reportId\":\"report-456\""));
    assertTrue(detail.contains("\"s3Key\":\"path/to/report.txt\""));
  }
}
