package com.vsm.api.domain.report;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Handles S3 storage of coach reports with KMS encryption.
 * Computes deterministic S3 keys and uploads canonical text files.
 */
@Component
public class ReportS3Storage {

  private final S3Client s3Client;
  private final String reportsBucket;
  private final String keyPrefix;
  private final String kmsKeyArn;

  public ReportS3Storage(
      S3Client s3Client,
      @Value("${app.s3.reportsBucket}") String reportsBucket,
      @Value("${app.s3.keyPrefix}") String keyPrefix,
      @Value("${app.kms.keyArn}") String kmsKeyArn) {
    this.s3Client = s3Client;
    this.reportsBucket = reportsBucket;
    this.keyPrefix = keyPrefix;
    this.kmsKeyArn = kmsKeyArn;
  }

  /**
   * Computes the deterministic S3 key for a report.
   * Pattern: {keyPrefix}{playerId}/{yyyy}/{MM}/{dd}/{reportId}.txt
   *
   * @param report the coach report
   * @return the S3 key
   */
  public String computeKey(CoachReport report) {
    LocalDate date = report.reportTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
    String year = DateTimeFormatter.ofPattern("yyyy").format(date);
    String month = DateTimeFormatter.ofPattern("MM").format(date);
    String day = DateTimeFormatter.ofPattern("dd").format(date);
    
    String prefix = (keyPrefix == null || keyPrefix.isEmpty()) ? "" : keyPrefix;
    return prefix + report.playerId() + "/" + year + "/" + month + "/" + day + "/" 
        + report.reportId() + ".txt";
  }

  /**
   * Uploads the canonical text to S3 with KMS encryption.
   *
   * @param s3Key the S3 key
   * @param canonicalText the report text content
   */
  public void upload(String s3Key, String canonicalText) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(reportsBucket)
        .key(s3Key)
        .contentType("text/plain; charset=utf-8")
        .serverSideEncryption("aws:kms")
        .ssekmsKeyId(kmsKeyArn)
        .build();

    s3Client.putObject(request, RequestBody.fromString(canonicalText));
  }
}
