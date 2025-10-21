package com.vsm.api.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Segment;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class XRayConfig {

  private final String daemonAddress;
  private final String segmentName;
  private final String samplingStrategyLocation;

  public XRayConfig(
      @Value("${AWS_XRAY_DAEMON_ADDRESS:127.0.0.1:2000}") String daemonAddress,
      @Value("${XRAY_SERVICE_NAME:players-api}") String segmentName,
      @Value("${XRAY_SAMPLING_STRATEGY:default}") String samplingStrategyLocation) {
    this.daemonAddress = daemonAddress;
    this.segmentName = segmentName;
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
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new XRayTracingFilter(recorder, segmentName));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }

  private static final class XRayTracingFilter implements Filter {

    private final AWSXRayRecorder recorder;
    private final String segmentName;

    private XRayTracingFilter(AWSXRayRecorder recorder, String segmentName) {
      this.recorder = recorder;
      this.segmentName = segmentName;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      Segment segment = null;
      try {
        segment = recorder.beginSegment(segmentName);
        chain.doFilter(request, response);
      } finally {
        if (segment != null) {
          recorder.endSegment();
        }
      }
    }
  }
}
