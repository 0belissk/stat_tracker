package com.vsm.api.infrastructure.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.vsm.api.config.CorrelationIdFilter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.MDC;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;

class ReportMetricsPublisherTest {

  @Test
  void recordsMetricWithCorrelationId() {
    CloudWatchClient client = Mockito.mock(CloudWatchClient.class);
    ReportMetricsPublisher publisher =
        new ReportMetricsPublisher(client, "custom/ns", "players-api", "dev");

    MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "corr-123");
    try {
      publisher.recordReportCreate(Duration.ofMillis(250), "success");
    } finally {
      MDC.clear();
    }

    ArgumentCaptor<PutMetricDataRequest> captor =
        ArgumentCaptor.forClass(PutMetricDataRequest.class);
    verify(client).putMetricData(captor.capture());
    PutMetricDataRequest request = captor.getValue();
    assertEquals("custom/ns", request.namespace());
    assertEquals(2, request.metricData().size());

    MetricDatum primary = request.metricData().get(0);
    assertEquals("report_create_latency", primary.metricName());
    Dimension service =
        primary.dimensions().stream()
            .filter(d -> "Service".equals(d.name()))
            .findFirst()
            .orElseThrow();
    assertEquals("players-api", service.value());

    MetricDatum detailed = request.metricData().get(1);
    Dimension correlation =
        detailed.dimensions().stream()
            .filter(d -> "CorrelationId".equals(d.name()))
            .findFirst()
            .orElseThrow();
    assertEquals("corr-123", correlation.value());
  }
}
