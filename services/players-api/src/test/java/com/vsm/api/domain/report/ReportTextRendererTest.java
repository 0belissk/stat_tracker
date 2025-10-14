package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportTextRendererTest {
  @Test
  void rendersSortedDeterministicText() {
    ReportTextRenderer r = new ReportTextRenderer();
    CoachReport report =
        new CoachReport(
            "p1",
            "p@example.com",
            Map.of("B", "2", "a", "1"),
            Instant.parse("2025-01-01T00:00:00Z"),
            "2025-01-01T00:00:00Z",
            "c1");
    String txt = r.render(report);
    assertTrue(txt.contains("playerId: p1"));
    int idxA = txt.indexOf("a: 1");
    int idxB = txt.indexOf("B: 2");
    assertTrue(idxA >= 0 && idxB > idxA, "categories should be case-insensitively sorted");
  }
}
