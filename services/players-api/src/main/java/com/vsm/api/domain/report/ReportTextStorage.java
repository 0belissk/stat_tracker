package com.vsm.api.domain.report;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Component
public class ReportTextStorage {

  private static final DateTimeFormatter YEAR_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter MONTH_FORMATTER =
      DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DAY_FORMATTER =
      DateTimeFormatter.ofPattern("dd").withZone(ZoneOffset.UTC);

  private final S3Client s3Client;
  private final String bucketName;
  private final String kmsKeyId;
  private final String prefix;

  public ReportTextStorage(
      S3Client s3Client,
      @Value("${app.reports.bucket-name}") String bucketName,
      @Value("${app.reports.kms-key-id}") String kmsKeyId,
      @Value("${app.reports.s3-prefix:reports}") String prefix) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
    this.kmsKeyId = kmsKeyId;
    if (prefix == null || prefix.isBlank()) {
      this.prefix = "";
    } else if (prefix.endsWith("/")) {
      this.prefix = prefix;
    } else {
      this.prefix = prefix + "/";
    }
  }

  public String writeReportText(CoachReport report) {
    String key = buildObjectKey(report);
    String body = buildCanonicalBody(report.categories());
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("text/plain; charset=utf-8")
            .serverSideEncryption(ServerSideEncryption.AWS_KMS)
            .ssekmsKeyId(kmsKeyId)
            .build();

    s3Client.putObject(request, RequestBody.fromBytes(bytes));
    return key;
  }

  String buildCanonicalBody(Map<String, String> categories) {
    return categories.entrySet().stream()
        .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
        .map(entry -> entry.getKey() + ": " + entry.getValue())
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private String buildObjectKey(CoachReport report) {
    String year = YEAR_FORMATTER.format(report.reportTimestamp());
    String month = MONTH_FORMATTER.format(report.reportTimestamp());
    String day = DAY_FORMATTER.format(report.reportTimestamp());

    return String.format(
        Locale.ROOT,
        "%s%s/%s/%s/%s/%s.txt",
        prefix,
        report.playerId(),
        year,
        month,
        day,
        report.reportId());
  }
}
