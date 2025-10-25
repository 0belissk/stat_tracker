package com.vsm.api.infrastructure.events;

import static org.mockito.Mockito.verify;

import com.vsm.api.config.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.MDC;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;

class ReportEventPublisherTest {

  @Test
  void publishesReportCreated() {
    EventBridgeClient eb = Mockito.mock(EventBridgeClient.class);
    Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "corr-123");
    try {
      ReportEventPublisher pub =
          new ReportEventPublisher(eb, "bus", "src", "report.created", clock);
      pub.publishReportCreated("p1", "r1", "k1");
    } finally {
      MDC.clear();
    }
    ArgumentCaptor<PutEventsRequest> cap = ArgumentCaptor.forClass(PutEventsRequest.class);
    verify(eb).putEvents(cap.capture());
    String detail = cap.getValue().entries().get(0).detail();
    assert detail.contains("\"playerId\":\"p1\"");
    assert detail.contains("\"reportId\":\"r1\"");
    assert detail.contains("\"s3Key\":\"k1\"");
    assert detail.contains("\"ingestStartedAt\":\"2024-01-01T00:00:00Z\"");
    assert detail.contains("\"correlationId\":\"corr-123\"");
  }
}
