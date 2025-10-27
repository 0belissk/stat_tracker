package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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

class CoachReportServiceTest {

  private final CoachReportRepository repository = Mockito.mock(CoachReportRepository.class);
  private final ReportTextRenderer renderer = Mockito.mock(ReportTextRenderer.class);
  private final S3ReportStorage storage = Mockito.mock(S3ReportStorage.class);
  private final ReportEventPublisher eventPublisher = Mockito.mock(ReportEventPublisher.class);
  private final AuditRepository auditRepository = Mockito.mock(AuditRepository.class);
  private final SoapStampClient soapStampClient = Mockito.mock(SoapStampClient.class);

  private final CoachReportService service =
      new CoachReportService(
          repository, renderer, storage, eventPublisher, auditRepository, soapStampClient);

  @Test
  void createDelegatesToRepository() {
    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "strong"),
            Instant.parse("2024-01-01T00:00:00Z"),
            "2024-01-01T00:00:00Z",
            "coach-1");

    Mockito.when(renderer.render(report)).thenReturn("Rendered report");
    Mockito.when(storage.store(report, "Rendered report"))
        .thenReturn("reports/player-1/report.txt");
    Mockito.when(soapStampClient.fetchStamp(report.reportId())).thenReturn(Optional.of("echo"));

    service.create(report);

    verify(renderer).render(report);
    verify(storage).store(report, "Rendered report");
    verify(soapStampClient).fetchStamp(report.reportId());
    verify(repository).save(report, "echo");
    verify(repository)
        .updateS3Key(
            report.playerId(),
            report.reportTimestamp(),
            report.reportId(),
            "reports/player-1/report.txt");
    verify(eventPublisher)
        .publishReportCreated(report.playerId(), report.reportId(), "reports/player-1/report.txt");
    verify(auditRepository)
        .writeSent(Mockito.eq(report.reportId()), Mockito.eq(report.coachId()), any(Instant.class));
  }

  @Test
  void createThrowsWhenDuplicate() {
    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "strong"),
            Instant.parse("2024-01-01T00:00:00Z"),
            "2024-01-01T00:00:00Z",
            "coach-1");

    Mockito.when(renderer.render(report)).thenReturn("Rendered report");
    Mockito.when(storage.store(report, "Rendered report"))
        .thenReturn("reports/player-1/report.txt");
    Mockito.when(soapStampClient.fetchStamp(report.reportId())).thenReturn(Optional.empty());

    doThrow(ConditionalCheckFailedException.builder().message("exists").build())
        .when(repository)
        .save(report, null);

    assertThrows(ReportAlreadyExistsException.class, () -> service.create(report));

    verify(storage).store(report, "Rendered report");
    verify(soapStampClient).fetchStamp(report.reportId());
    verify(repository).save(report, null);
    verify(repository)
        .updateS3Key(
            report.playerId(),
            report.reportTimestamp(),
            report.reportId(),
            "reports/player-1/report.txt");
    verify(eventPublisher)
        .publishReportCreated(report.playerId(), report.reportId(), "reports/player-1/report.txt");
    verify(auditRepository)
        .writeSent(Mockito.eq(report.reportId()), Mockito.eq(report.coachId()), any(Instant.class));
  }
}
