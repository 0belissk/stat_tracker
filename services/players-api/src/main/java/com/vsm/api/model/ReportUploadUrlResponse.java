package com.vsm.api.model;

import java.time.Instant;
import java.util.Map;

public class ReportUploadUrlResponse {

  private final String uploadId;
  private final String objectKey;
  private final String uploadUrl;
  private final Instant expiresAt;
  private final Map<String, String> headers;

  public ReportUploadUrlResponse(
      String uploadId,
      String objectKey,
      String uploadUrl,
      Instant expiresAt,
      Map<String, String> headers) {
    this.uploadId = uploadId;
    this.objectKey = objectKey;
    this.uploadUrl = uploadUrl;
    this.expiresAt = expiresAt;
    this.headers = headers;
  }

  public String getUploadId() {
    return uploadId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getUploadUrl() {
    return uploadUrl;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }
}
