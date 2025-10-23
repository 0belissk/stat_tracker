package com.vsm.api.domain.report;

import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import com.vsm.api.infrastructure.audit.AuditRepository;
import com.vsm.api.infrastructure.events.ReportEventPublisher;
import com.vsm.api.infrastructure.storage.S3ReportStorage;
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
  private final ReportTextRenderer renderer;
  private final S3ReportStorage storage;
  private final ReportEventPublisher events;
  private final AuditRepository audit;

  public CoachReportService(
      CoachReportRepository repository,
      ReportTextRenderer renderer,
      S3ReportStorage storage,
      ReportEventPublisher events,
      AuditRepository audit) {
    this.repository = repository;
    this.renderer = renderer;
    this.storage = storage;
    this.events = events;
    this.audit = audit;
  }

  public void create(@NotNull @Valid CoachReport report) {
    // a) Render & store text first (safe overwrite for retries)
    String text = renderer.render(report);
    String s3Key = storage.store(report, text);

    boolean duplicate = false;
    try {
      // b) Persist core report (idempotent via conditional)
      repository.save(report);
    } catch (ConditionalCheckFailedException e) {
      duplicate = true; // existing report
    }

    // c) Attach s3Key (if_not_exists semantics)
    try {
      repository.updateS3Key(report.playerId(), report.reportTimestamp(), report.reportId(), s3Key);
    } catch (RuntimeException ignore) {
      // tolerate transient failures; can be healed later
    }

    // d) Publish event
    events.publishReportCreated(report.playerId(), report.reportId(), s3Key);

    // e) Audit entry
    audit.writeSent(report.reportId(), report.coachId(), Instant.now());

    if (duplicate) {
      throw new ReportAlreadyExistsException(report.reportId(), null);
    }
  }
}
