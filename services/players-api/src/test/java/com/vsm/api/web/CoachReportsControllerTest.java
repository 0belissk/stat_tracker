package com.vsm.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vsm.api.config.SecurityConfig;
import com.vsm.api.domain.report.CoachReportService;
import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import com.vsm.api.model.ReportRequest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CoachReportsController.class)
@Import(SecurityConfig.class)
class CoachReportsControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper mapper;

  @MockBean private CoachReportService coachReportService;

  @MockBean private JwtDecoder jwtDecoder;

  @Test
  void createReport_requiresReportIdHeader() throws Exception {
    ReportRequest request = new ReportRequest();
    request.setPlayerId("p123");
    request.setPlayerEmail("player@example.com");
    request.setCategories(Map.of("serving", "great"));

    mvc.perform(
            post("/api/coach/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReport_rejectsInvalidTimestamp() throws Exception {
    ReportRequest request = new ReportRequest();
    request.setPlayerId("p123");
    request.setPlayerEmail("player@example.com");
    request.setCategories(Map.of("serving", "great"));

    mvc.perform(
            post("/api/coach/reports")
                .header("reportId", "not-a-timestamp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReport_persistsAndReturnsAccepted() throws Exception {
    ReportRequest request = new ReportRequest();
    request.setPlayerId("p123");
    request.setPlayerEmail("player@example.com");
    request.setCategories(Map.of("serving", "great"));

    String timestamp = Instant.now().toString();

    mvc.perform(
            post("/api/coach/reports")
                .header("reportId", timestamp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))
                .with(
                    jwt()
                        .jwt(jwt -> jwt.subject("coach-123"))
                        .authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.reportId").value(timestamp))
        .andExpect(jsonPath("$.status").value("QUEUED"));

    verify(coachReportService).create(any());
  }

  @Test
  void createReport_isIdempotent() throws Exception {
    ReportRequest request = new ReportRequest();
    request.setPlayerId("p123");
    request.setPlayerEmail("player@example.com");
    request.setCategories(Map.of("serving", "great"));

    String timestamp = Instant.now().toString();

    doThrow(new ReportAlreadyExistsException(timestamp, new RuntimeException()))
        .when(coachReportService)
        .create(any());

    mvc.perform(
            post("/api/coach/reports")
                .header("reportId", timestamp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.reportId").value(timestamp));
  }
}
