package com.vsm.api.web;

import com.vsm.api.domain.report.CoachReport;
import com.vsm.api.domain.report.CoachReportService;
import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import com.vsm.api.model.ReportRequest;
import com.vsm.api.model.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/coach/reports")
@Tag(name = "Coach Reports")
public class CoachReportsController {

  private final CoachReportService coachReportService;

  public CoachReportsController(CoachReportService coachReportService) {
    this.coachReportService = coachReportService;
  }

  @PostMapping
  @Operation(
      summary = "Create and persist a coach report",
      description = "Validates input, enforces idempotency, and persists the report metadata.")
  public ResponseEntity<ReportResponse> createReport(
      @RequestHeader("reportId") String reportIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ReportRequest req) {
    Instant reportTimestamp = parseReportTimestamp(reportIdHeader);
    CoachReport report =
        new CoachReport(
            req.getPlayerId(),
            req.getPlayerEmail(),
            req.getCategories(),
            reportTimestamp,
            reportIdHeader,
            resolveCoachId(jwt));

    try {
      coachReportService.create(report);
    } catch (ReportAlreadyExistsException ignored) {
      // Idempotent retry - treat as success.
    }

    return ResponseEntity.accepted()
        .body(new ReportResponse(report.reportId(), "QUEUED", Instant.now()));
  }

  private Instant parseReportTimestamp(String header) {
    if (header == null || header.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportId header is required");
    }
    try {
      return Instant.parse(header);
    } catch (DateTimeParseException ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "reportId must be an ISO-8601 instant", ex);
    }
  }

  private String resolveCoachId(Jwt jwt) {
    if (jwt == null) {
      return "anonymous";
    }
    if (jwt.getClaimAsString("username") != null) {
      return jwt.getClaimAsString("username");
    }
    return jwt.getSubject();
  }
}
