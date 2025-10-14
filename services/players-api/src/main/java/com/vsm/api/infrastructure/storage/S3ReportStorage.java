package com.vsm.api.infrastructure.storage;

import com.vsm.api.domain.report.CoachReport;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Stores canonical report text in S3 with SSE-KMS and returns its S3 key. */
@Component
public class S3ReportStorage {
  private static final DateTimeFormatter YYYY = DateTimeFormatter.ofPattern("yyyy");
  private static final DateTimeFormatter MM = DateTimeFormatter.ofPattern("MM");
  private static final DateTimeFormatter DD = DateTimeFormatter.ofPattern("dd");

  private final S3Client s3;
  private final String bucket;
  private final String keyPrefix; // may be empty, includes trailing slash if present
  private final String kmsKeyArn;

  public S3ReportStorage(
      S3Client s3,
      @Value("${app.s3.reportsBucket}") String bucket,
      @Value("${app.s3.keyPrefix:}") String keyPrefix,
      @Value("${app.kms.keyArn}") String kmsKeyArn) {
    this.s3 = s3;
    this.bucket = bucket;
    this.keyPrefix = normalize(keyPrefix);
    this.kmsKeyArn = kmsKeyArn;
  }

  private String normalize(String p) {
    if (p == null || p.isBlank()) return "";
    return p.endsWith("/") ? p : (p + "/");
  }

  public String store(CoachReport report, String text) {
    var zdt = report.reportTimestamp().atZone(ZoneOffset.UTC);
    String key =
        keyPrefix
            + report.playerId()
            + "/"
            + YYYY.format(zdt)
            + "/"
            + MM.format(zdt)
            + "/"
            + DD.format(zdt)
            + "/"
            + report.reportId()
            + ".txt";

    PutObjectRequest req =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("text/plain; charset=utf-8")
            .serverSideEncryption("aws:kms")
            .ssekmsKeyId(kmsKeyArn)
            .build();

    s3.putObject(req, RequestBody.fromString(text, StandardCharsets.UTF_8));
    return key;
  }
}
