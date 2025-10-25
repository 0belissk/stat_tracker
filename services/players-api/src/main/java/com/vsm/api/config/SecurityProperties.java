package com.vsm.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
  private long maxPayloadBytes = 1_048_576L;

  public long getMaxPayloadBytes() {
    return maxPayloadBytes;
  }

  public void setMaxPayloadBytes(long maxPayloadBytes) {
    this.maxPayloadBytes = Math.max(0, maxPayloadBytes);
  }
}
