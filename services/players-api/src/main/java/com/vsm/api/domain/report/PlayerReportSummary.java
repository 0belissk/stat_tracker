package com.vsm.api.domain.report;

import java.time.Instant;

public record PlayerReportSummary(
    String reportId, Instant reportTimestamp, Instant createdAt, String coachId, String s3Key) {}
