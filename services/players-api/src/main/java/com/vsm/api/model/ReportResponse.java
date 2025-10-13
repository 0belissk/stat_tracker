package com.vsm.api.model;

import java.time.Instant;

public record ReportResponse(String reportId, String status, Instant at) {}
