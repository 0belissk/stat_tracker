package com.vsm.api.config;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws.sdk")
public class AwsClientTuningProperties {

  private final Http http = new Http();
  private final Timeouts timeouts = new Timeouts();
  private final Retry retry = new Retry();

  public Http getHttp() {
    return http;
  }

  public Timeouts getTimeouts() {
    return timeouts;
  }

  public Retry getRetry() {
    return retry;
  }

  public static class Http {
    private int maxConnections = 64;
    private Duration connectionMaxIdle = Duration.ofSeconds(30);
    private Duration connectionTimeToLive = Duration.ofMinutes(5);
    private Duration connectionTimeout = Duration.ofSeconds(2);
    private Duration connectionAcquisitionTimeout = Duration.ofSeconds(1);
    private Duration socketTimeout = Duration.ofSeconds(30);
    private boolean tcpKeepAlive = true;

    public int getMaxConnections() {
      return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
      this.maxConnections = Math.max(1, maxConnections);
    }

    public Duration getConnectionMaxIdle() {
      return connectionMaxIdle;
    }

    public void setConnectionMaxIdle(Duration connectionMaxIdle) {
      this.connectionMaxIdle = sanitizeDuration(connectionMaxIdle, Duration.ofSeconds(30));
    }

    public Duration getConnectionTimeToLive() {
      return connectionTimeToLive;
    }

    public void setConnectionTimeToLive(Duration connectionTimeToLive) {
      this.connectionTimeToLive = sanitizeDuration(connectionTimeToLive, Duration.ofMinutes(5));
    }

    public Duration getConnectionTimeout() {
      return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
      this.connectionTimeout = sanitizeDuration(connectionTimeout, Duration.ofSeconds(2));
    }

    public Duration getConnectionAcquisitionTimeout() {
      return connectionAcquisitionTimeout;
    }

    public void setConnectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) {
      this.connectionAcquisitionTimeout =
          sanitizeDuration(connectionAcquisitionTimeout, Duration.ofSeconds(1));
    }

    public Duration getSocketTimeout() {
      return socketTimeout;
    }

    public void setSocketTimeout(Duration socketTimeout) {
      this.socketTimeout = sanitizeDuration(socketTimeout, Duration.ofSeconds(30));
    }

    public boolean isTcpKeepAlive() {
      return tcpKeepAlive;
    }

    public void setTcpKeepAlive(boolean tcpKeepAlive) {
      this.tcpKeepAlive = tcpKeepAlive;
    }

    private Duration sanitizeDuration(Duration candidate, Duration fallback) {
      if (candidate == null || candidate.isZero() || candidate.isNegative()) {
        return fallback;
      }
      return candidate;
    }
  }

  public static class Timeouts {
    private Duration apiCall = Duration.ofSeconds(30);
    private Duration apiAttempt = Duration.ofSeconds(10);

    public Duration getApiCall() {
      return apiCall;
    }

    public void setApiCall(Duration apiCall) {
      this.apiCall = sanitizeDuration(apiCall, Duration.ofSeconds(30));
    }

    public Duration getApiAttempt() {
      return apiAttempt;
    }

    public void setApiAttempt(Duration apiAttempt) {
      this.apiAttempt = sanitizeDuration(apiAttempt, Duration.ofSeconds(10));
    }

    private Duration sanitizeDuration(Duration candidate, Duration fallback) {
      if (candidate == null || candidate.isZero() || candidate.isNegative()) {
        return fallback;
      }
      return candidate;
    }
  }

  public static class Retry {
    private String mode = "standard";
    private int maxAttempts = 3;
    private Duration backoffBase = Duration.ofMillis(200);
    private Duration backoffMax = Duration.ofSeconds(5);
    private boolean enableThrottledBackoff = true;

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      if (mode == null || mode.isBlank()) {
        this.mode = "standard";
      } else {
        this.mode = mode.toLowerCase(Locale.ROOT);
      }
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = Math.max(1, maxAttempts);
    }

    public Duration getBackoffBase() {
      return backoffBase;
    }

    public void setBackoffBase(Duration backoffBase) {
      this.backoffBase = sanitizeDuration(backoffBase, Duration.ofMillis(200));
    }

    public Duration getBackoffMax() {
      return backoffMax;
    }

    public void setBackoffMax(Duration backoffMax) {
      this.backoffMax = sanitizeDuration(backoffMax, Duration.ofSeconds(5));
    }

    public boolean isEnableThrottledBackoff() {
      return enableThrottledBackoff;
    }

    public void setEnableThrottledBackoff(boolean enableThrottledBackoff) {
      this.enableThrottledBackoff = enableThrottledBackoff;
    }

    private Duration sanitizeDuration(Duration candidate, Duration fallback) {
      if (candidate == null || candidate.isZero() || candidate.isNegative()) {
        return fallback;
      }
      return candidate;
    }
  }
}
