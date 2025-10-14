package com.vsm.api.infrastructure.events;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;

class ReportEventPublisherTest {

  @Test
  void publishesReportCreated() {
    EventBridgeClient eb = Mockito.mock(EventBridgeClient.class);
    ReportEventPublisher pub = new ReportEventPublisher(eb, "bus", "src", "report.created");
    pub.publishReportCreated("p1", "r1", "k1");
    ArgumentCaptor<PutEventsRequest> cap = ArgumentCaptor.forClass(PutEventsRequest.class);
    verify(eb).putEvents(cap.capture());
    String detail = cap.getValue().entries().get(0).detail();
    assert detail.contains("\"playerId\":\"p1\"");
    assert detail.contains("\"reportId\":\"r1\"");
    assert detail.contains("\"s3Key\":\"k1\"");
  }
}
