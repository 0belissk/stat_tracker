package com.vsm.api.web;

import com.vsm.api.domain.report.PlayerReportPage;
import com.vsm.api.domain.report.PlayerReportService;
import com.vsm.api.domain.report.PlayerReportSummary;
import com.vsm.api.model.PlayerReportListItem;
import com.vsm.api.model.PlayerReportListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/players/{playerId}/reports")
@Tag(name = "Player Reports")
public class PlayerReportsController {

  private final PlayerReportService service;

  public PlayerReportsController(PlayerReportService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "List reports for a player",
      description = "Returns reports in reverse chronological order.")
  public PlayerReportListResponse listReports(
      @PathVariable("playerId") String playerId,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "cursor", required = false) String cursor) {
    if (limit != null && (limit < 1 || limit > 50)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");
    }

    PlayerReportPage page = service.listReports(playerId, limit, cursor);
    List<PlayerReportListItem> items =
        page.items().stream().map(this::toItem).collect(Collectors.toList());
    return new PlayerReportListResponse(items, page.nextCursor());
  }

  private PlayerReportListItem toItem(PlayerReportSummary summary) {
    return new PlayerReportListItem(
        summary.reportId(),
        summary.reportTimestamp(),
        summary.createdAt(),
        summary.coachId(),
        summary.s3Key());
  }
}
