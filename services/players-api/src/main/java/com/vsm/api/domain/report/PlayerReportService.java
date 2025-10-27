package com.vsm.api.domain.report;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PlayerReportService {

  private final CoachReportRepository repository;
  private final int defaultLimit;
  private final int maxLimit;

  @Autowired
  public PlayerReportService(
      CoachReportRepository repository,
      @Value("${app.reports.default-page-size:20}") Integer configuredDefault,
      @Value("${app.reports.max-page-size:50}") Integer configuredMax) {
    this(repository, toIntOrDefault(configuredDefault, 20), toIntOrDefault(configuredMax, 50));
  }

  PlayerReportService(CoachReportRepository repository, int defaultLimit, int maxLimit) {
    this.repository = repository;
    int sanitizedDefault = Math.max(1, defaultLimit);
    int sanitizedMax = Math.max(sanitizedDefault, maxLimit);
    this.defaultLimit = sanitizedDefault;
    this.maxLimit = sanitizedMax;
  }

  public PlayerReportPage listReports(String playerId, Integer limit, String cursor) {
    int effectiveLimit = defaultLimit;
    if (limit != null && limit > 0) {
      effectiveLimit = Math.min(limit, maxLimit);
    }
    return repository.listReports(playerId, effectiveLimit, cursor);
  }

  private static int toIntOrDefault(Integer candidate, int fallback) {
    return candidate == null ? fallback : candidate;
  }
}
