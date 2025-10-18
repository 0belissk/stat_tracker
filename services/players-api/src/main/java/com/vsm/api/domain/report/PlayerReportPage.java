package com.vsm.api.domain.report;

import java.util.List;

public record PlayerReportPage(List<PlayerReportSummary> items, String nextCursor) {}
