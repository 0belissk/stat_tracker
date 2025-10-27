package com.vsm.api.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsm.api.config.SecurityConfig;
import com.vsm.api.domain.report.PlayerReportPage;
import com.vsm.api.domain.report.PlayerReportService;
import com.vsm.api.domain.report.PlayerReportSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerReportsController.class)
@Import(SecurityConfig.class)
class PlayerReportsControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private PlayerReportService playerReportService;

  @MockBean private JwtDecoder jwtDecoder;

  @Test
  void listReportsRejectsInvalidLimit() throws Exception {
    mvc.perform(get("/api/players/player-1/reports").param("limit", "0").with(jwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReportsReturnsSummaries() throws Exception {
    PlayerReportSummary summary =
        new PlayerReportSummary(
            "2024-01-01T00:00:00Z",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-01T00:05:00Z"),
            "coach-123",
            "reports/player-1/report.txt",
            "soap:2024-01-01T00:00:00Z");
    when(playerReportService.listReports("player-1", null, null))
        .thenReturn(new PlayerReportPage(List.of(summary), "2024-01-02T00:00:00Z"));

    mvc.perform(get("/api/players/player-1/reports").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].reportId").value("2024-01-01T00:00:00Z"))
        .andExpect(jsonPath("$.items[0].coachId").value("coach-123"))
        .andExpect(jsonPath("$.items[0].s3Key").value("reports/player-1/report.txt"))
        .andExpect(jsonPath("$.items[0].soapStamp").value("soap:2024-01-01T00:00:00Z"))
        .andExpect(jsonPath("$.nextCursor").value("2024-01-02T00:00:00Z"));

    verify(playerReportService).listReports("player-1", null, null);
  }
}
