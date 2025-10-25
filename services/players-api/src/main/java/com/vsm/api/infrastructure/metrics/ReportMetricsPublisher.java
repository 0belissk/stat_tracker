package com.vsm.api.infrastructure.metrics;

import com.vsm.api.config.CorrelationIdFilter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/** Publishes custom CloudWatch metrics for API operations. */
@Component
public class ReportMetricsPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReportMetricsPublisher.class);
  private static final String METRIC_REPORT_CREATE_LATENCY = "report_create_latency";
  private static final String METRIC_REPORT_CREATE_LATENCY_DETAILED =
      "report_create_latency_by_outcome";

  private final CloudWatchClient cloudWatchClient;
  private final String namespace;
  private final String service;
  private final String stage;

  public ReportMetricsPublisher(
      CloudWatchClient cloudWatchClient,
      @Value("${app.metrics.namespace}") String namespace,
      @Value("${app.metrics.service}") String service,
      @Value("${app.metrics.stage}") String stage) {
    this.cloudWatchClient = cloudWatchClient;
    this.namespace = namespace;
    this.service = service;
    this.stage = stage;
  }

  public void recordReportCreate(Duration duration, String outcome) {
    if (duration == null || duration.isNegative()) {
      return;
    }

    double millis = duration.toMillis();
    if (millis < 0) {
      return;
    }

    List<Dimension> baseDimensions = new ArrayList<>();
    if (StringUtils.hasText(service)) {
      baseDimensions.add(Dimension.builder().name("Service").value(service).build());
    }
    if (StringUtils.hasText(stage)) {
      baseDimensions.add(Dimension.builder().name("Stage").value(stage).build());
    }

    MetricDatum primaryDatum =
        MetricDatum.builder()
            .metricName(METRIC_REPORT_CREATE_LATENCY)
            .unit(StandardUnit.MILLISECONDS)
            .value(millis)
            .dimensions(baseDimensions)
            .build();

    List<MetricDatum> data = new ArrayList<>();
    data.add(primaryDatum);

    List<Dimension> detailedDimensions = new ArrayList<>(baseDimensions);
    boolean hasDetailDimensions = false;

    if (StringUtils.hasText(outcome)) {
      detailedDimensions.add(Dimension.builder().name("Outcome").value(outcome).build());
      hasDetailDimensions = true;
    }

    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    if (StringUtils.hasText(correlationId)) {
      detailedDimensions.add(
          Dimension.builder().name("CorrelationId").value(correlationId).build());
      hasDetailDimensions = true;
    }

    if (hasDetailDimensions) {
      data.add(
          MetricDatum.builder()
              .metricName(METRIC_REPORT_CREATE_LATENCY_DETAILED)
              .unit(StandardUnit.MILLISECONDS)
              .value(millis)
              .dimensions(detailedDimensions)
              .build());
    }

    PutMetricDataRequest request =
        PutMetricDataRequest.builder().namespace(namespace).metricData(data).build();

    try {
      cloudWatchClient.putMetricData(request);
    } catch (Exception ex) {
      LOGGER.warn(
          "Failed to publish {} metric: {}", METRIC_REPORT_CREATE_LATENCY, ex.getMessage(), ex);
    }
  }
}
