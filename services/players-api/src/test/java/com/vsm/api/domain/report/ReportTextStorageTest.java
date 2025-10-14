package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class ReportTextStorageTest {

  private final S3Client s3Client = Mockito.mock(S3Client.class);
  private final ReportTextStorage storage =
      new ReportTextStorage(s3Client, "reports-bucket", "todo: set-kms-key", "reports/");

  @Test
  void writeReportTextUploadsCanonicalBody() throws IOException {
    Map<String, String> categories = new LinkedHashMap<>();
    categories.put("serving", "Great velocity");
    categories.put("Passing", "Consistent");
    categories.put("Digs", "Quick reactions");

    CoachReport report =
        new CoachReport(
            "player-123",
            "player@example.com",
            categories,
            Instant.parse("2024-03-01T10:15:30Z"),
            "2024-03-01T10:15:30Z",
            "coach-555");

    String key = storage.writeReportText(report);

    ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

    PutObjectRequest request = requestCaptor.getValue();
    assertEquals("reports-bucket", request.bucket());
    assertEquals("reports/player-123/2024/03/01/2024-03-01T10:15:30Z.txt", request.key());
    assertEquals("text/plain; charset=utf-8", request.contentType());
    assertEquals("aws:kms", request.serverSideEncryptionAsString());
    assertEquals("todo: set-kms-key", request.ssekmsKeyId());

    try (InputStream in = bodyCaptor.getValue().contentStreamProvider().newStream()) {
      String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals("Digs: Quick reactions\nPassing: Consistent\nserving: Great velocity", body);
    }

    assertEquals(
        "reports/player-123/2024/03/01/2024-03-01T10:15:30Z.txt",
        key,
        "returns the generated key");
  }
}
