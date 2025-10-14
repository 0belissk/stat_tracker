package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

class CoachReportServiceTest {

  private final CoachReportRepository repository = Mockito.mock(CoachReportRepository.class);
  private final ReportTextStorage textStorage = Mockito.mock(ReportTextStorage.class);
  private final CoachReportEventPublisher eventPublisher = Mockito.mock(CoachReportEventPublisher.class);

  private final CoachReportService service =
      new CoachReportService(
          repository,
          textStorage,
          eventPublisher,
          java.time.Clock.fixed(Instant.parse("2024-02-02T00:00:00Z"), java.time.ZoneOffset.UTC));

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

    Mockito.when(textStorage.writeReportText(report)).thenReturn("reports/player-1/report.txt");

    service.create(report);

    verify(textStorage).writeReportText(report);
    verify(repository).save(report, "reports/player-1/report.txt");
    verify(eventPublisher).publishReportCreated(report, "reports/player-1/report.txt");
    verify(repository).saveAuditEntry(report, Instant.parse("2024-02-02T00:00:00Z"));
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

    Mockito.when(textStorage.writeReportText(report)).thenReturn("reports/player-1/report.txt");

    doThrow(ConditionalCheckFailedException.builder().message("exists").build())
        .when(repository)
        .save(report, "reports/player-1/report.txt");

    assertThrows(ReportAlreadyExistsException.class, () -> service.create(report));

    verify(eventPublisher, never()).publishReportCreated(Mockito.any(), Mockito.anyString());
    verify(repository, never()).saveAuditEntry(Mockito.any(), Mockito.any());
  }
}
