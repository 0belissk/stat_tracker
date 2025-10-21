package com.vsm.api.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.strategy.FixedSegmentNamingStrategy;
import com.amazonaws.xray.strategy.SegmentNamingStrategy;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class XRayConfig {

  private final String daemonAddress;
  private final SegmentNamingStrategy segmentNamingStrategy;
  private final String samplingStrategyLocation;

  public XRayConfig(
      @Value("${AWS_XRAY_DAEMON_ADDRESS:127.0.0.1:2000}") String daemonAddress,
      @Value("${XRAY_SERVICE_NAME:players-api}") String segmentName,
      @Value("${XRAY_SAMPLING_STRATEGY:default}") String samplingStrategyLocation) {
    this.daemonAddress = daemonAddress;
    this.segmentNamingStrategy = new FixedSegmentNamingStrategy(segmentName);
    this.samplingStrategyLocation = samplingStrategyLocation;
  }

  @Bean
  public AWSXRayRecorder awsXRayRecorder() {
    System.setProperty("com.amazonaws.xray.emitters.daemonAddress", daemonAddress);
    if (!"default".equalsIgnoreCase(samplingStrategyLocation)) {
      System.setProperty(
          "com.amazonaws.xray.strategy.samplingStrategyFile", samplingStrategyLocation);
    }
    AWSXRayRecorder recorder = AWSXRayRecorderBuilder.standard().build();
    AWSXRay.setGlobalRecorder(recorder);
    return recorder;
  }

  @Bean
  public FilterRegistrationBean<Filter> awsXRayFilter(AWSXRayRecorder recorder) {
    AWSXRayServletFilter filter = new AWSXRayServletFilter(segmentNamingStrategy, recorder);
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
