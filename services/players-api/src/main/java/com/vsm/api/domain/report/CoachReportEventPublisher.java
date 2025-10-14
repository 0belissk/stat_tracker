package com.vsm.api.domain.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@Component
public class CoachReportEventPublisher {

  private final EventBridgeClient eventBridgeClient;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final String eventBusName;
  private final String source;

  public CoachReportEventPublisher(
      EventBridgeClient eventBridgeClient,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${app.reports.event-bus-name}") String eventBusName,
      @Value("${app.reports.event-source:players-api.reports}") String source) {
    this.eventBridgeClient = eventBridgeClient;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.eventBusName = eventBusName;
    this.source = source;
  }

  public void publishReportCreated(CoachReport report, String s3Key) {
    ReportCreatedDetail detail = new ReportCreatedDetail(report.playerId(), report.reportId(), s3Key);
    String detailJson = toJson(detail);

    PutEventsRequestEntry entry =
        PutEventsRequestEntry.builder()
            .eventBusName(eventBusName)
            .detailType("report.created")
            .source(source)
            .time(Instant.now(clock))
            .detail(detailJson)
            .build();

    PutEventsResponse response =
        eventBridgeClient.putEvents(PutEventsRequest.builder().entries(entry).build());

    if (response.failedEntryCount() != null && response.failedEntryCount() > 0) {
      throw new IllegalStateException("Failed to publish report.created event");
    }
  }

  private String toJson(ReportCreatedDetail detail) {
    try {
      return objectMapper.writeValueAsString(detail);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to serialize event detail", ex);
    }
  }

  private record ReportCreatedDetail(String playerId, String reportId, String s3Key) {}
}
