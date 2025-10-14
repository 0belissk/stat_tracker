package com.vsm.api.domain.report;

import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Service
@Validated
public class CoachReportService {

  private final CoachReportRepository repository;
  private final ReportTextStorage reportTextStorage;
  private final CoachReportEventPublisher eventPublisher;
  private final Clock clock;

  public CoachReportService(
      CoachReportRepository repository,
      ReportTextStorage reportTextStorage,
      CoachReportEventPublisher eventPublisher,
      Clock clock) {
    this.repository = repository;
    this.reportTextStorage = reportTextStorage;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public void create(@NotNull @Valid CoachReport report) {
    String s3Key = reportTextStorage.writeReportText(report);
    try {
      repository.save(report, s3Key);
    } catch (ConditionalCheckFailedException exists) {
      throw new ReportAlreadyExistsException(report.reportId(), exists);
    }
    eventPublisher.publishReportCreated(report, s3Key);
    repository.saveAuditEntry(report, Instant.now(clock));
  }
}
