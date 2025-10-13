package com.vsm.api.domain.report;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record CoachReport(
    @NotBlank String playerId,
    @Email @NotBlank String playerEmail,
    @NotEmpty Map<@NotBlank String, @NotBlank String> categories,
    @NotNull Instant reportTimestamp,
    @NotBlank String reportId,
    @NotBlank String coachId) {}
