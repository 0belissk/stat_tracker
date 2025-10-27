package com.vsm.api.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

@Configuration
public class DynamoConfig {

  @Bean
  DynamoDbClient dynamoDbClient(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.dynamodb-endpoint:}") String endpointOverride,
      SdkHttpClient httpClient,
      ClientOverrideConfiguration overrideConfiguration) {
    DynamoDbClientBuilder builder =
        DynamoDbClient.builder()
            .region(Region.of(region))
            .httpClient(httpClient)
            .overrideConfiguration(overrideConfiguration);
    if (endpointOverride != null && !endpointOverride.isBlank()) {
      builder.endpointOverride(URI.create(endpointOverride));
    }
    return builder.build();
  }
}
