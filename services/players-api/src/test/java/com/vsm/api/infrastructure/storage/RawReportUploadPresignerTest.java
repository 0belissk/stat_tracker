package com.vsm.api.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class RawReportUploadPresignerTest {

  private final S3Presigner presigner = mock(S3Presigner.class);
  private final Clock clock = Clock.fixed(Instant.parse("2024-03-20T10:15:30Z"), ZoneOffset.UTC);
  private RawReportUploadPresigner uploadPresigner;

  @BeforeEach
  void setup() {
    uploadPresigner = new RawReportUploadPresigner(presigner, clock, "raw-bucket", "uploads/", 600);
  }

  @Test
  void createUploadBuildsExpectedPutRequest() throws MalformedURLException {
    PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
    SdkHttpFullRequest httpRequest =
        SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.PUT)
            .uri(URI.create("https://example.com/upload"))
            .putHeader("Content-Type", "text/csv")
            .build();

    when(presigned.httpRequest()).thenReturn(httpRequest);
    when(presigned.url()).thenReturn(new URL("https://example.com/upload"));
    when(presigned.expiration()).thenReturn(clock.instant().plusSeconds(600));

    ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectPresignRequest.class);
    when(presigner.presignPutObject(requestCaptor.capture())).thenReturn(presigned);

    RawReportUploadPresigner.PresignedUpload upload =
        uploadPresigner.createUpload("coach-42", "stats.csv", "text/csv", 128L);

    PutObjectRequest request = requestCaptor.getValue().putObjectRequest();
    assertEquals("raw-bucket", request.bucket());
    assertEquals("uploads/coach-42/" + upload.uploadId() + "/stats.csv", request.key());
    assertEquals("text/csv", request.contentType());
    assertEquals(Long.valueOf(128L), request.contentLength());
    assertEquals("coach-42", request.metadata().get("coach-id"));
    assertEquals(upload.uploadId(), request.metadata().get("upload-id"));
    assertEquals("stats.csv", request.metadata().get("original-file-name"));

    assertEquals("https://example.com/upload", upload.uploadUrl());
    assertEquals(clock.instant().plusSeconds(600), upload.expiresAt());
    assertEquals(Map.of("Content-Type", "text/csv"), upload.headers());
  }

  @Test
  void sanitizeFileNameStripsPathAndInvalidChars() {
    String sanitized = uploadPresigner.sanitizeFileName(" ../crazy path/Report 01.CSV ");
    assertEquals("Report_01.CSV", sanitized);
  }

  @Test
  void createUploadRejectsUnsupportedContentType() {
    assertThrows(
        IllegalArgumentException.class,
        () -> uploadPresigner.createUpload("coach-42", "stats.csv", "application/json", null));
  }
}
