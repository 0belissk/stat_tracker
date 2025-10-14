package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportTextRendererTest {

  private final ReportTextRenderer renderer = new ReportTextRenderer();

  @Test
  void renderProducesCanonicalTextWithHeaderAndSortedCategories() {
    Map<String, String> categories = new LinkedHashMap<>();
    categories.put("serving", "strong");
    categories.put("blocking", "needs work");
    categories.put("attacking", "excellent");

    CoachReport report = new CoachReport(
        "player-123",
        "player@example.com",
        categories,
        Instant.parse("2024-01-15T10:30:00Z"),
        "report-456",
        "coach-789"
    );

    String text = renderer.render(report);

    // Verify header lines
    assertTrue(text.contains("playerId: player-123"));
    assertTrue(text.contains("coachId: coach-789"));
    assertTrue(text.contains("reportTimestamp: 2024-01-15T10:30:00Z"));
    assertTrue(text.contains("reportId: report-456"));

    // Verify categories are sorted alphabetically
    String[] lines = text.split("\n");
    boolean foundAttacking = false;
    boolean foundBlocking = false;
    boolean foundServing = false;
    
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].equals("attacking: excellent")) {
        foundAttacking = true;
        // attacking should come before blocking
        for (int j = i + 1; j < lines.length; j++) {
          if (lines[j].equals("blocking: needs work")) {
            foundBlocking = true;
            break;
          }
        }
      }
      if (lines[i].equals("serving: strong")) {
        foundServing = true;
      }
    }

    assertTrue(foundAttacking, "Should contain attacking category");
    assertTrue(foundBlocking, "Should contain blocking category");
    assertTrue(foundServing, "Should contain serving category");
  }

  @Test
  void renderEndsWithNewline() {
    CoachReport report = new CoachReport(
        "player-1",
        "p@example.com",
        Map.of("skill", "good"),
        Instant.parse("2024-01-01T00:00:00Z"),
        "report-1",
        "coach-1"
    );

    String text = renderer.render(report);

    assertTrue(text.endsWith("\n"), "Text should end with newline");
  }
}
