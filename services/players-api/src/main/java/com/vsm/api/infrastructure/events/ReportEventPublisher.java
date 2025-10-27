package com.vsm.api.infrastructure.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vsm.api.config.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
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
  private final Clock clock;

  public ReportEventPublisher(
      EventBridgeClient eb,
      @Value("${app.events.busName}") String busName,
      @Value("${app.events.source}") String source,
      @Value("${app.events.detailType.reportCreated}") String dt,
      @Nullable Clock clock) {
    this.eb = eb;
    this.busName = busName;
    this.source = source;
    this.detailTypeReportCreated = dt;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public void publishReportCreated(String playerId, String reportId, String s3Key) {
    Instant now = Instant.now(clock);
    Map<String, Object> detailMap = new HashMap<>();
    detailMap.put("playerId", playerId);
    detailMap.put("reportId", reportId);
    detailMap.put("s3Key", s3Key);
    detailMap.put("ingestStartedAt", now.toString());

    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    if (correlationId != null && !correlationId.isBlank()) {
      detailMap.put("correlationId", correlationId);
    }

    String traceHeader = MDC.get(CorrelationIdFilter.XRAY_TRACE_MDC_KEY);
    if (traceHeader != null && !traceHeader.isBlank()) {
      detailMap.put("traceHeader", traceHeader);
    }

    String detail = toJson(detailMap);
    PutEventsRequestEntry entry =
        PutEventsRequestEntry.builder()
            .eventBusName(busName)
            .source(source)
            .detailType(detailTypeReportCreated)
            .time(now)
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
