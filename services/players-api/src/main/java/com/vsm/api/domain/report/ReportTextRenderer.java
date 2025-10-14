package com.vsm.api.domain.report;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Canonical plain-text renderer for CoachReport.
 * Header lines: playerId, coachId, reportTimestamp (ISO-INSTANT), reportId then blank line.
 * Categories sorted case-insensitively: "Category: value" each line, newline terminated.
 */
@Component
public class ReportTextRenderer {
  private static final DateTimeFormatter INSTANT_FMT = DateTimeFormatter.ISO_INSTANT;

  public String render(CoachReport report) {
    StringBuilder sb = new StringBuilder();
    sb.append("playerId: ").append(report.playerId()).append('\n');
    sb.append("coachId: ").append(report.coachId()).append('\n');
    sb.append("reportTimestamp: ")
        .append(INSTANT_FMT.format(report.reportTimestamp().atOffset(ZoneOffset.UTC)))
        .append('\n');
    sb.append("reportId: ").append(report.reportId()).append('\n');
    sb.append('\n');

    Map<String, String> cats = report.categories();
    String catLines =
        cats.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));
    sb.append(catLines);
    if (!cats.isEmpty()) sb.append('\n');
    return sb.toString();
  }
}
