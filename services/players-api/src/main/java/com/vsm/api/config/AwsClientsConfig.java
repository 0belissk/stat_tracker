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

/**
 * AWS SDK v2 client configuration for S3 and EventBridge services.
 */
@Configuration
public class AwsClientsConfig {

  @Bean
  S3Client s3Client(
      @Value("${app.aws.region}") String region,
      @Value("${S3_ENDPOINT:}") String endpointOverride) {
    S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
    if (endpointOverride != null && !endpointOverride.isBlank()) {
      builder.endpointOverride(URI.create(endpointOverride));
    }
    return builder.build();
  }

  @Bean
  EventBridgeClient eventBridgeClient(
      @Value("${app.aws.region}") String region,
      @Value("${EVENTBRIDGE_ENDPOINT:}") String endpointOverride) {
    EventBridgeClientBuilder builder = EventBridgeClient.builder().region(Region.of(region));
    if (endpointOverride != null && !endpointOverride.isBlank()) {
      builder.endpointOverride(URI.create(endpointOverride));
    }
    return builder.build();
  }
}
