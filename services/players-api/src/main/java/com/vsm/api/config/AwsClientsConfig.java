package com.vsm.api.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClientBuilder;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Shared AWS SDK v2 client wiring with configurable HTTP pools, retries, and timeouts. */
@Configuration
@EnableConfigurationProperties(AwsClientTuningProperties.class)
public class AwsClientsConfig {

  private final AwsClientTuningProperties tuning;

  public AwsClientsConfig(AwsClientTuningProperties tuning) {
    this.tuning = tuning;
  }

  @Bean(destroyMethod = "close")
  SdkHttpClient awsSdkHttpClient() {
    AwsClientTuningProperties.Http http = tuning.getHttp();
    ApacheHttpClient.Builder builder =
        ApacheHttpClient.builder()
            .maxConnections(http.getMaxConnections())
            .connectionMaxIdleTime(http.getConnectionMaxIdle())
            .connectionTimeout(http.getConnectionTimeout())
            .connectionAcquisitionTimeout(http.getConnectionAcquisitionTimeout())
            .socketTimeout(http.getSocketTimeout())
            .tcpKeepAlive(http.isTcpKeepAlive());
    if (http.getConnectionTimeToLive() != null) {
      builder.connectionTimeToLive(http.getConnectionTimeToLive());
    }
    return builder.build();
  }

  @Bean
  ClientOverrideConfiguration awsClientOverrideConfiguration() {
    AwsClientTuningProperties.Timeouts timeouts = tuning.getTimeouts();
    AwsClientTuningProperties.Retry retry = tuning.getRetry();
    RetryMode retryMode = resolveRetryMode(retry.getMode());

    ClientOverrideConfiguration.Builder builder = ClientOverrideConfiguration.builder();
    if (isPositive(timeouts.getApiCall())) {
      builder.apiCallTimeout(timeouts.getApiCall());
    }
    if (isPositive(timeouts.getApiAttempt())) {
      builder.apiCallAttemptTimeout(timeouts.getApiAttempt());
    }

    builder.retryPolicy(retryMode);
    builder.retryPolicy(buildRetryPolicy(retry));
    return builder.build();
  }

  @Bean
  S3Client s3Client(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.s3-endpoint:}") String s3Endpoint,
      SdkHttpClient httpClient,
      ClientOverrideConfiguration overrideConfiguration) {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClient(httpClient)
            .overrideConfiguration(overrideConfiguration);
    if (s3Endpoint != null && !s3Endpoint.isBlank()) {
      builder.endpointOverride(URI.create(s3Endpoint));
    }
    return builder.build();
  }

  @Bean
  S3Presigner s3Presigner(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.s3-endpoint:}") String s3Endpoint) {
    S3Presigner.Builder builder =
        S3Presigner.builder()
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
      @Value("${app.aws.eventbridge-endpoint:}") String ebEndpoint,
      SdkHttpClient httpClient,
      ClientOverrideConfiguration overrideConfiguration) {
    EventBridgeClientBuilder builder =
        EventBridgeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClient(httpClient)
            .overrideConfiguration(overrideConfiguration);
    if (ebEndpoint != null && !ebEndpoint.isBlank()) {
      builder.endpointOverride(URI.create(ebEndpoint));
    }
    return builder.build();
  }

  @Bean
  CloudWatchClient cloudWatchClient(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.cloudwatch-endpoint:}") String cloudWatchEndpoint,
      SdkHttpClient httpClient,
      ClientOverrideConfiguration overrideConfiguration) {
    CloudWatchClientBuilder builder =
        CloudWatchClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClient(httpClient)
            .overrideConfiguration(overrideConfiguration);
    if (cloudWatchEndpoint != null && !cloudWatchEndpoint.isBlank()) {
      builder.endpointOverride(URI.create(cloudWatchEndpoint));
    }
    return builder.build();
  }

  private RetryPolicy buildRetryPolicy(AwsClientTuningProperties.Retry retry) {
    FullJitterBackoffStrategy backoffStrategy =
        FullJitterBackoffStrategy.builder()
            .baseDelay(retry.getBackoffBase())
            .maxBackoffTime(retry.getBackoffMax())
            .build();

    RetryPolicy.Builder builder = RetryPolicy.builder();
    builder.numRetries(Math.max(0, retry.getMaxAttempts() - 1));
    builder.backoffStrategy(backoffStrategy);
    builder.throttlingBackoffStrategy(
        retry.isEnableThrottledBackoff() ? backoffStrategy : BackoffStrategy.none());
    return builder.build();
  }

  private RetryMode resolveRetryMode(String mode) {
    if (mode == null || mode.isBlank()) {
      return RetryMode.STANDARD;
    }
    try {
      return RetryMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return RetryMode.STANDARD;
    }
  }

  private boolean isPositive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
