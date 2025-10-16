package com.vsm.api.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.vsm.api.domain.report.CoachReport;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ReportStorageTest {

  @Test
  void storesWithKmsAndContentType() {
    S3Client s3 = Mockito.mock(S3Client.class);
    S3ReportStorage storage = new S3ReportStorage(s3, "bucket", "reports/", "kms-arn");
    CoachReport r =
        new CoachReport(
            "p1",
            "p@example.com",
            Map.of("A", "1"),
            Instant.parse("2025-01-02T03:04:05Z"),
            "2025-01-02T03:04:05Z",
            "c1");
    String key = storage.store(r, "body");
    assertTrue(key.startsWith("reports/p1/2025/01/02/2025-01-02T03:04:05Z"));
    ArgumentCaptor<PutObjectRequest> cap = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(cap.capture(), any(RequestBody.class));
    PutObjectRequest req = cap.getValue();
    assertTrue("bucket".equals(req.bucket()));
    assertTrue("text/plain; charset=utf-8".equals(req.contentType()));
    assertTrue("aws:kms".equals(req.serverSideEncryptionAsString()));
    assertTrue("kms-arn".equals(req.ssekmsKeyId()));
  }
}
