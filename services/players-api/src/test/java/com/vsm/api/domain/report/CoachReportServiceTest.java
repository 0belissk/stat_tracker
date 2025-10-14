package com.vsm.api.domain.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

class CoachReportServiceTest {

  private final CoachReportRepository repository = Mockito.mock(CoachReportRepository.class);
  private final ReportTextRenderer textRenderer = Mockito.mock(ReportTextRenderer.class);
  private final ReportS3Storage s3Storage = Mockito.mock(ReportS3Storage.class);
  private final ReportEventPublisher eventPublisher = Mockito.mock(ReportEventPublisher.class);
  private final AuditRepository auditRepository = Mockito.mock(AuditRepository.class);

  private final CoachReportService service = new CoachReportService(
      repository, textRenderer, s3Storage, eventPublisher, auditRepository);

  @Test
  void createOrchestratesToS3RepositoryEventAndAudit() {
    CoachReport report = new CoachReport(
        "player-1",
        "player@example.com",
        Map.of("serving", "strong"),
        Instant.parse("2024-01-01T00:00:00Z"),
        "report-id-1",
        "coach-1");

    when(textRenderer.render(report)).thenReturn("canonical text");
    when(s3Storage.computeKey(report)).thenReturn("player-1/2024/01/01/report-id-1.txt");

    service.create(report);

    // Verify S3 upload
    verify(textRenderer).render(report);
    verify(s3Storage).computeKey(report);
    verify(s3Storage).upload("player-1/2024/01/01/report-id-1.txt", "canonical text");

    // Verify repository save
    verify(repository).save(report);

    // Verify S3 key update
    verify(repository).updateS3Key(
        eq("player-1"), eq(Instant.parse("2024-01-01T00:00:00Z")), 
        eq("player-1/2024/01/01/report-id-1.txt"));

    // Verify event published
    verify(eventPublisher).publishReportCreated(
        "player-1", "report-id-1", "player-1/2024/01/01/report-id-1.txt");

    // Verify audit log
    verify(auditRepository).writeReportSent(eq("report-id-1"), eq("coach-1"), any(Instant.class));
  }

  @Test
  void createContinuesWhenReportAlreadyExists() {
    CoachReport report = new CoachReport(
        "player-1",
        "player@example.com",
        Map.of("serving", "strong"),
        Instant.parse("2024-01-01T00:00:00Z"),
        "report-id-1",
        "coach-1");

    when(textRenderer.render(report)).thenReturn("canonical text");
    when(s3Storage.computeKey(report)).thenReturn("player-1/2024/01/01/report-id-1.txt");

    // Simulate duplicate report
    doThrow(ConditionalCheckFailedException.builder().message("exists").build())
        .when(repository)
        .save(report);

    // Should not throw exception - idempotent behavior
    service.create(report);

    // Verify S3, event, and audit still happen
    verify(s3Storage).upload(anyString(), anyString());
    verify(repository).updateS3Key(any(), any(), anyString());
    verify(eventPublisher).publishReportCreated(anyString(), anyString(), anyString());
    verify(auditRepository).writeReportSent(anyString(), anyString(), any(Instant.class));
  }
}
