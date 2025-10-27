package com.vsm.api.domain.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import com.vsm.api.infrastructure.audit.AuditRepository;
import com.vsm.api.infrastructure.events.ReportEventPublisher;
import com.vsm.api.infrastructure.soap.SoapStampClient;
import com.vsm.api.infrastructure.storage.S3ReportStorage;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

class CoachReportServiceDay7Test {

  @Test
  void orchestratesHappyPath() {
    CoachReportRepository repo = Mockito.mock(CoachReportRepository.class);
    ReportTextRenderer renderer = new ReportTextRenderer();
    S3ReportStorage storage = Mockito.mock(S3ReportStorage.class);
    ReportEventPublisher events = Mockito.mock(ReportEventPublisher.class);
    AuditRepository audit = Mockito.mock(AuditRepository.class);
    SoapStampClient soap = Mockito.mock(SoapStampClient.class);
    CoachReportService svc = new CoachReportService(repo, renderer, storage, events, audit, soap);
    CoachReport report =
        new CoachReport(
            "p1", "p@x", Map.of("A", "1"), Instant.parse("2025-01-01T00:00:00Z"), "r1", "c1");
    when(storage.store(eq(report), any())).thenReturn("reports/p1/2025/01/01/r1.txt");
    when(soap.fetchStamp("r1")).thenReturn(Optional.of("soap-r1"));
    svc.create(report);
    verify(storage).store(eq(report), any());
    verify(soap).fetchStamp("r1");
    verify(repo).save(report, "soap-r1");
    verify(repo)
        .updateS3Key(
            "p1", Instant.parse("2025-01-01T00:00:00Z"), "r1", "reports/p1/2025/01/01/r1.txt");
    verify(events).publishReportCreated("p1", "r1", "reports/p1/2025/01/01/r1.txt");
    verify(audit).writeSent(eq("r1"), eq("c1"), any());
  }

  @Test
  void duplicateStillPerformsSideEffectsAndThrows() {
    CoachReportRepository repo = Mockito.mock(CoachReportRepository.class);
    ReportTextRenderer renderer = new ReportTextRenderer();
    S3ReportStorage storage = Mockito.mock(S3ReportStorage.class);
    ReportEventPublisher events = Mockito.mock(ReportEventPublisher.class);
    AuditRepository audit = Mockito.mock(AuditRepository.class);
    SoapStampClient soap = Mockito.mock(SoapStampClient.class);
    CoachReportService svc = new CoachReportService(repo, renderer, storage, events, audit, soap);
    CoachReport report =
        new CoachReport(
            "p1", "p@x", Map.of("A", "1"), Instant.parse("2025-01-01T00:00:00Z"), "r1", "c1");
    when(storage.store(eq(report), any())).thenReturn("reports/p1/2025/01/01/r1.txt");
    when(soap.fetchStamp("r1")).thenReturn(Optional.empty());
    doThrow(ConditionalCheckFailedException.builder().message("exists").build())
        .when(repo)
        .save(report, null);
    try {
      svc.create(report);
    } catch (ReportAlreadyExistsException ignored) {
    }
    verify(storage).store(eq(report), any());
    verify(soap).fetchStamp("r1");
    verify(repo).save(report, null);
    verify(repo)
        .updateS3Key(
            "p1", Instant.parse("2025-01-01T00:00:00Z"), "r1", "reports/p1/2025/01/01/r1.txt");
    verify(events).publishReportCreated("p1", "r1", "reports/p1/2025/01/01/r1.txt");
    verify(audit).writeSent(eq("r1"), eq("c1"), any());
  }
}
