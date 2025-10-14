package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class ReportS3StorageTest {

  private final S3Client s3Client = Mockito.mock(S3Client.class);

  @Test
  void computeKeyFollowsDeterministicPattern() {
    ReportS3Storage storage = new ReportS3Storage(
        s3Client, "test-bucket", "reports/", "arn:aws:kms:us-east-1:123:key/abc");

    CoachReport report = new CoachReport(
        "player-123",
        "player@example.com",
        Map.of("serving", "good"),
        Instant.parse("2024-01-15T10:30:00Z"),
        "report-456",
        "coach-789"
    );

    String key = storage.computeKey(report);

    assertEquals("reports/player-123/2024/01/15/report-456.txt", key);
  }

  @Test
  void computeKeyWithEmptyPrefix() {
    ReportS3Storage storage = new ReportS3Storage(
        s3Client, "test-bucket", "", "arn:aws:kms:us-east-1:123:key/abc");

    CoachReport report = new CoachReport(
        "player-99",
        "p@example.com",
        Map.of("blocking", "strong"),
        Instant.parse("2024-12-25T23:59:59Z"),
        "report-xyz",
        "coach-1"
    );

    String key = storage.computeKey(report);

    assertEquals("player-99/2024/12/25/report-xyz.txt", key);
  }

  @Test
  void uploadSetsCorrectS3Parameters() {
    ReportS3Storage storage = new ReportS3Storage(
        s3Client, "my-bucket", "prefix/", "arn:aws:kms:us-east-1:123:key/mykey");

    storage.upload("some/key.txt", "test content");

    ArgumentCaptor<PutObjectRequest> requestCaptor = 
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

    PutObjectRequest request = requestCaptor.getValue();
    assertEquals("my-bucket", request.bucket());
    assertEquals("some/key.txt", request.key());
    assertEquals("text/plain; charset=utf-8", request.contentType());
    assertEquals("aws:kms", request.serverSideEncryption().toString());
    assertEquals("arn:aws:kms:us-east-1:123:key/mykey", request.ssekmsKeyId());
  }
}
