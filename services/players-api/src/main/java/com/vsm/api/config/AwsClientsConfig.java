package com.vsm.api.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS SDK v2 clients (S3, EventBridge). Region comes from app.aws.region.
 * Optional endpoint overrides (for local testing) left empty in normal environments.
 */
@Configuration
public class AwsClientsConfig {

  @Bean
  S3Client s3Client(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.s3-endpoint:}") String s3Endpoint) {
    S3Client.Builder builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create());
    if (s3Endpoint != null && !s3Endpoint.isBlank()) {
      builder.endpointOverride(URI.create(s3Endpoint));
    }
    return builder.build();
  }

  @Bean
  EventBridgeClient eventBridgeClient(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.eventbridge-endpoint:}") String ebEndpoint) {
    EventBridgeClient.Builder builder =
        EventBridgeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create());
    if (ebEndpoint != null && !ebEndpoint.isBlank()) {
      builder.endpointOverride(URI.create(ebEndpoint));
    }
    return builder.build();
  }
}
