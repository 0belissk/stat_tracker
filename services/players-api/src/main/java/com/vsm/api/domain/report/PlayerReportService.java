package com.vsm.api.domain.report;

import org.springframework.stereotype.Service;

@Service
public class PlayerReportService {
  private static final int MAX_LIMIT = 50;
  private static final int DEFAULT_LIMIT = 20;

  private final CoachReportRepository repository;

  public PlayerReportService(CoachReportRepository repository) {
    this.repository = repository;
  }

  public PlayerReportPage listReports(String playerId, Integer limit, String cursor) {
    int effectiveLimit = DEFAULT_LIMIT;
    if (limit != null && limit > 0) {
      effectiveLimit = Math.min(limit, MAX_LIMIT);
    }
    return repository.listReports(playerId, effectiveLimit, cursor);
  }
}
