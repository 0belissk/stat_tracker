package com.vsm.api.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class XRayConfig {

  private final String daemonAddress;
  private final String serviceName;
  private final String samplingStrategyLocation;

  public XRayConfig(
      @Value("${AWS_XRAY_DAEMON_ADDRESS:127.0.0.1:2000}") String daemonAddress,
      @Value("${XRAY_SERVICE_NAME:players-api}") String serviceName,
      @Value("${XRAY_SAMPLING_STRATEGY:default}") String samplingStrategyLocation) {
    this.daemonAddress = daemonAddress;
    this.serviceName = serviceName;
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
  public FilterRegistrationBean<AWSXRayServletFilter> awsXRayFilter(AWSXRayRecorder recorder) {
    AWSXRayServletFilter filter = new AWSXRayServletFilter(serviceName, recorder);
    FilterRegistrationBean<AWSXRayServletFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
