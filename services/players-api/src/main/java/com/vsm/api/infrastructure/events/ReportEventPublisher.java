package com.vsm.api.infrastructure.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

/** Publishes report.created events to EventBridge. */
@Component
public class ReportEventPublisher {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final EventBridgeClient eb;
  private final String busName;
  private final String source;
  private final String detailTypeReportCreated;

  public ReportEventPublisher(
      EventBridgeClient eb,
      @Value("${app.events.busName}") String busName,
      @Value("${app.events.source}") String source,
      @Value("${app.events.detailType.reportCreated}") String dt) {
    this.eb = eb;
    this.busName = busName;
    this.source = source;
    this.detailTypeReportCreated = dt;
  }

  public void publishReportCreated(String playerId, String reportId, String s3Key) {
    String detail = toJson(Map.of("playerId", playerId, "reportId", reportId, "s3Key", s3Key));
    PutEventsRequestEntry entry =
        PutEventsRequestEntry.builder()
            .eventBusName(busName)
            .source(source)
            .detailType(detailTypeReportCreated)
            .detail(detail)
            .build();
    eb.putEvents(PutEventsRequest.builder().entries(entry).build());
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      return "{\"serialization\":true}";
    }
  }
}
