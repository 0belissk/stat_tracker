package com.vsm.api.web;

import com.vsm.api.model.ReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coach/reports")
@Tag(name = "Coach Reports")
public class CoachReportsController {

  @PostMapping
  @Operation(
      summary = "Create and send a report (stub)",
      description = "Validates input; returns a fake reportId for now.")
  public ResponseEntity<?> createReport(@Valid @RequestBody ReportRequest req) {
    // Day 3: just validate and return a fake ID
    String reportId = UUID.randomUUID().toString();
    return ResponseEntity.accepted()
        .body(Map.of("reportId", reportId, "status", "QUEUED", "at", Instant.now().toString()));
  }
}
