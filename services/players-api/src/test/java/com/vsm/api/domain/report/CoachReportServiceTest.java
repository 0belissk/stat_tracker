package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

class CoachReportServiceTest {

  private final CoachReportRepository repository = Mockito.mock(CoachReportRepository.class);

  private final CoachReportService service = new CoachReportService(repository);

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

    service.create(report);

    verify(repository).save(report);
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

    doThrow(ConditionalCheckFailedException.builder().message("exists").build())
        .when(repository)
        .save(report);

    assertThrows(ReportAlreadyExistsException.class, () -> service.create(report));
  }
}
