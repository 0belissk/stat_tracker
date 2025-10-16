package com.vsm.api.config;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

@TestConfiguration
public class TestAwsClientsConfig {

  @Bean
  @Primary
  public S3Client testS3Client() {
    return mock(S3Client.class);
  }
}
