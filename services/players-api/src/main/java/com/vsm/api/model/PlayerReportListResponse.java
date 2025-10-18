package com.vsm.api.model;

import java.util.List;

public record PlayerReportListResponse(List<PlayerReportListItem> items, String nextCursor) {}
