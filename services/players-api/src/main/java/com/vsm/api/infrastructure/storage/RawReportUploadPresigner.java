package com.vsm.api.infrastructure.storage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class RawReportUploadPresigner {

  public record PresignedUpload(
      String uploadId, String objectKey, String uploadUrl, Instant expiresAt, Map<String, String> headers) {}

  private static final String DEFAULT_CONTENT_TYPE = "text/csv";

  private final S3Presigner presigner;
  private final Clock clock;
  private final String bucket;
  private final String prefix;
  private final Duration expiry;

  public RawReportUploadPresigner(
      S3Presigner presigner,
      Clock clock,
      @Value("${app.s3.rawUploadsBucket}") String bucket,
      @Value("${app.s3.rawUploadsPrefix:}") String prefix,
      @Value("${app.s3.rawUploadExpirySeconds:900}") long expirySeconds) {
    this.presigner = presigner;
    this.clock = clock;
    this.bucket = bucket;
    this.prefix = normalizePrefix(prefix);
    if (expirySeconds <= 0) {
      throw new IllegalArgumentException("rawUploadExpirySeconds must be positive");
    }
    this.expiry = Duration.ofSeconds(expirySeconds);
  }

  private String normalizePrefix(String maybePrefix) {
    if (!StringUtils.hasText(maybePrefix)) {
      return "";
    }
    return maybePrefix.endsWith("/") ? maybePrefix : maybePrefix + "/";
  }

  public PresignedUpload createUpload(
      String coachId, String fileName, String contentType, Long contentLength) {
    if (!StringUtils.hasText(coachId)) {
      throw new IllegalArgumentException("coachId is required for presigned upload");
    }
    if (!StringUtils.hasText(fileName)) {
      throw new IllegalArgumentException("fileName is required for presigned upload");
    }

    String sanitizedFileName = sanitizeFileName(fileName);
    String resolvedContentType = resolveContentType(contentType);

    String uploadId = UUID.randomUUID().toString();
    String objectKey = prefix + coachId + "/" + uploadId + "/" + sanitizedFileName;

    Map<String, String> metadata = new HashMap<>();
    metadata.put("coach-id", coachId);
    metadata.put("upload-id", uploadId);
    metadata.put("original-file-name", sanitizedFileName);

    PutObjectRequest.Builder putObjectBuilder =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(resolvedContentType)
            .metadata(metadata);

    if (contentLength != null && contentLength > 0) {
      putObjectBuilder.contentLength(contentLength);
    }

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .putObjectRequest(putObjectBuilder.build())
            .build();

    PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

    Instant expiresAt =
        presigned.expiration() != null ? presigned.expiration() : clock.instant().plus(expiry);
    Map<String, String> headers = flattenHeaders(presigned.httpRequest());

    return new PresignedUpload(uploadId, objectKey, presigned.url().toString(), expiresAt, headers);
  }

  private Map<String, String> flattenHeaders(SdkHttpRequest httpRequest) {
    if (httpRequest == null) {
      return Map.of();
    }
    return httpRequest.headers().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
  }

  private String resolveContentType(String contentType) {
    if (!StringUtils.hasText(contentType)) {
      return DEFAULT_CONTENT_TYPE;
    }
    String normalized = contentType.trim().toLowerCase(Locale.ROOT);
    if (!DEFAULT_CONTENT_TYPE.equals(normalized)) {
      throw new IllegalArgumentException("Only text/csv content is supported for uploads");
    }
    return DEFAULT_CONTENT_TYPE;
  }

  String sanitizeFileName(String fileName) {
    String candidate = fileName.replace('\\', '/');
    int lastSlash = candidate.lastIndexOf('/');
    if (lastSlash >= 0) {
      candidate = candidate.substring(lastSlash + 1);
    }
    candidate = candidate.trim();
    if (candidate.isEmpty()) {
      throw new IllegalArgumentException("fileName must resolve to a non-empty name");
    }
    String sanitized = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
    if (!sanitized.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      sanitized = sanitized + ".csv";
    }
    return sanitized;
  }
}
