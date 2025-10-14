package com.vsm.api.domain.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

/**
 * Publishes report.created events to EventBridge.
 */
@Component
public class ReportEventPublisher {

  private final EventBridgeClient eventBridgeClient;
  private final String busName;
  private final String source;
  private final String detailTypeReportCreated;

  public ReportEventPublisher(
      EventBridgeClient eventBridgeClient,
      @Value("${app.events.busName}") String busName,
      @Value("${app.events.source}") String source,
      @Value("${app.events.detailType.reportCreated}") String detailTypeReportCreated) {
    this.eventBridgeClient = eventBridgeClient;
    this.busName = busName;
    this.source = source;
    this.detailTypeReportCreated = detailTypeReportCreated;
  }

  /**
   * Publishes a report.created event to EventBridge.
   *
   * @param playerId the player ID
   * @param reportId the report ID
   * @param s3Key the S3 key where the report is stored
   */
  public void publishReportCreated(String playerId, String reportId, String s3Key) {
    String detail = String.format(
        "{\"playerId\":\"%s\",\"reportId\":\"%s\",\"s3Key\":\"%s\"}",
        playerId, reportId, s3Key);

    PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
        .eventBusName(busName)
        .source(source)
        .detailType(detailTypeReportCreated)
        .detail(detail)
        .build();

    PutEventsRequest request = PutEventsRequest.builder()
        .entries(entry)
        .build();

    eventBridgeClient.putEvents(request);
  }
}
