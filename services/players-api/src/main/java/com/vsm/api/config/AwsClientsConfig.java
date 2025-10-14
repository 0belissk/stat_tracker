package com.vsm.api.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class AwsClientsConfig {

  @Bean
  S3Client s3Client(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.s3-endpoint:}") String s3EndpointOverride) {
    S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
    if (s3EndpointOverride != null && !s3EndpointOverride.isBlank()) {
      builder.endpointOverride(URI.create(s3EndpointOverride));
    }
    return builder.build();
  }

  @Bean
  EventBridgeClient eventBridgeClient(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.eventbridge-endpoint:}") String eventBridgeEndpoint) {
    EventBridgeClientBuilder builder = EventBridgeClient.builder().region(Region.of(region));
    if (eventBridgeEndpoint != null && !eventBridgeEndpoint.isBlank()) {
      builder.endpointOverride(URI.create(eventBridgeEndpoint));
    }
    return builder.build();
  }
}
