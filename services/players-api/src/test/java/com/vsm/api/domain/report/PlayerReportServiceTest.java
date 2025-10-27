package com.vsm.api.domain.report;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlayerReportServiceTest {

  private final CoachReportRepository repository = Mockito.mock(CoachReportRepository.class);
  private final PlayerReportService service = new PlayerReportService(repository, 20, 50);

  @Test
  void defaultsLimitWhenNull() {
    PlayerReportPage page = new PlayerReportPage(List.of(), null);
    when(repository.listReports("player-1", 20, null)).thenReturn(page);

    service.listReports("player-1", null, null);

    verify(repository).listReports("player-1", 20, null);
  }

  @Test
  void capsLimitAtMaximum() {
    PlayerReportPage page = new PlayerReportPage(List.of(), null);
    when(repository.listReports("player-1", 50, null)).thenReturn(page);

    service.listReports("player-1", 500, null);

    verify(repository).listReports("player-1", 50, null);
  }

  @Test
  void sanitizesConfiguredLimits() {
    PlayerReportService customService = new PlayerReportService(repository, -5, 3);
    PlayerReportPage page = new PlayerReportPage(List.of(), null);
    when(repository.listReports("player-1", 1, null)).thenReturn(page);

    customService.listReports("player-1", null, null);

    verify(repository).listReports("player-1", 1, null);
  }
}
