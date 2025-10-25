package com.vsm.api.web;

import com.vsm.api.domain.report.CoachReport;
import com.vsm.api.domain.report.CoachReportService;
import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import com.vsm.api.infrastructure.metrics.ReportMetricsPublisher;
import com.vsm.api.infrastructure.storage.RawReportUploadPresigner;
import com.vsm.api.model.ReportRequest;
import com.vsm.api.model.ReportResponse;
import com.vsm.api.model.ReportUploadUrlRequest;
import com.vsm.api.model.ReportUploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/coach/reports")
@Tag(name = "Coach Reports")
public class CoachReportsController {

  private final CoachReportService coachReportService;
  private final RawReportUploadPresigner uploadPresigner;
  private final ReportMetricsPublisher metrics;

  public CoachReportsController(
      CoachReportService coachReportService,
      RawReportUploadPresigner uploadPresigner,
      ReportMetricsPublisher metrics) {
    this.coachReportService = coachReportService;
    this.uploadPresigner = uploadPresigner;
    this.metrics = metrics;
  }

  @PostMapping
  @Operation(
      summary = "Create and persist a coach report",
      description = "Validates input, enforces idempotency, and persists the report metadata.")
  public ResponseEntity<ReportResponse> createReport(
      @RequestHeader("reportId") String reportIdHeader,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ReportRequest req) {
    Instant start = Instant.now();
    String outcome = "success";
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
      outcome = "duplicate";
    } catch (RuntimeException ex) {
      outcome = "error";
      metrics.recordReportCreate(Duration.between(start, Instant.now()), outcome);
      throw ex;
    }

    metrics.recordReportCreate(Duration.between(start, Instant.now()), outcome);

    return ResponseEntity.accepted()
        .body(new ReportResponse(report.reportId(), "QUEUED", Instant.now()));
  }

  @PostMapping("/upload-url")
  @Operation(
      summary = "Generate a presigned S3 URL for uploading coach report CSVs",
      description =
          "Creates a short-lived PUT URL scoped to the authenticated coach."
              + " The uploaded CSV will trigger downstream validation.")
  public ResponseEntity<ReportUploadUrlResponse> createUploadUrl(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReportUploadUrlRequest request) {
    RawReportUploadPresigner.PresignedUpload presigned;
    try {
      presigned =
          uploadPresigner.createUpload(
              resolveCoachId(jwt),
              request.getFileName(),
              request.getContentType(),
              request.getContentLength());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }

    ReportUploadUrlResponse response =
        new ReportUploadUrlResponse(
            presigned.uploadId(),
            presigned.objectKey(),
            presigned.uploadUrl().toString(),
            presigned.expiresAt(),
            presigned.headers());
    return ResponseEntity.ok(response);
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
