package com.vsm.api.domain.report;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Renders a canonical plain-text representation of a CoachReport.
 * The format includes header lines for playerId, coachId, reportTimestamp, reportId,
 * followed by categories sorted alphabetically by name.
 */
@Component
public class ReportTextRenderer {

  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ISO_INSTANT;

  /**
   * Generates canonical UTF-8 plain text from a CoachReport.
   *
   * @param report the coach report to render
   * @return canonical text body with header lines and sorted categories
   */
  public String render(CoachReport report) {
    StringBuilder sb = new StringBuilder();
    
    // Header lines
    sb.append("playerId: ").append(report.playerId()).append("\n");
    sb.append("coachId: ").append(report.coachId()).append("\n");
    sb.append("reportTimestamp: ")
        .append(TIMESTAMP_FORMATTER.format(report.reportTimestamp()))
        .append("\n");
    sb.append("reportId: ").append(report.reportId()).append("\n");
    
    // Categories sorted by name
    String categoriesText = report.categories().entrySet().stream()
        .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
        .map(e -> e.getKey() + ": " + e.getValue())
        .collect(Collectors.joining("\n"));
    
    if (!categoriesText.isEmpty()) {
      sb.append(categoriesText).append("\n");
    }
    
    return sb.toString();
  }
}
