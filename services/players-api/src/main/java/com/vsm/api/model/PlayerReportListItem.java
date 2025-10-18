package com.vsm.api.model;

import java.time.Instant;

public record PlayerReportListItem(
    String reportId, Instant reportTimestamp, Instant createdAt, String coachId, String s3Key) {}
