package com.vsm.api.domain.report;

import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Service
@Validated
public class CoachReportService {

  private final CoachReportRepository repository;
  private final ReportTextRenderer textRenderer;
  private final ReportS3Storage s3Storage;
  private final ReportEventPublisher eventPublisher;
  private final AuditRepository auditRepository;

  public CoachReportService(
      CoachReportRepository repository,
      ReportTextRenderer textRenderer,
      ReportS3Storage s3Storage,
      ReportEventPublisher eventPublisher,
      AuditRepository auditRepository) {
    this.repository = repository;
    this.textRenderer = textRenderer;
    this.s3Storage = s3Storage;
    this.eventPublisher = eventPublisher;
    this.auditRepository = auditRepository;
  }

  public void create(@NotNull @Valid CoachReport report) {
    // Step 1: Compute canonical text and S3 key, then upload to S3
    String canonicalText = textRenderer.render(report);
    String s3Key = s3Storage.computeKey(report);
    s3Storage.upload(s3Key, canonicalText);

    // Step 2: Persist the report item with conditional write for idempotency
    try {
      repository.save(report);
    } catch (ConditionalCheckFailedException exists) {
      // Duplicate report - treat as success but ensure S3/event/audit still happen
      // S3 upload is safe to repeat (overwrite), so we continue with the rest
    }

    // Step 3: Update the report item to include s3Key
    try {
      repository.updateS3Key(report.playerId(), report.reportTimestamp(), s3Key);
    } catch (Exception e) {
      // Tolerate if update fails (e.g., already set) - don't break idempotency
    }

    // Step 4: Publish EventBridge report.created event
    eventPublisher.publishReportCreated(report.playerId(), report.reportId(), s3Key);

    // Step 5: Write audit log entry
    auditRepository.writeReportSent(report.reportId(), report.coachId(), Instant.now());
  }
}
