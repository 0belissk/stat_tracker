package com.vsm.api.domain.report;

import com.vsm.api.domain.report.exception.ReportAlreadyExistsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Service
@Validated
public class CoachReportService {

  private final CoachReportRepository repository;

  public CoachReportService(CoachReportRepository repository) {
    this.repository = repository;
  }

  public void create(@NotNull @Valid CoachReport report) {
    try {
      repository.save(report);
    } catch (ConditionalCheckFailedException exists) {
      throw new ReportAlreadyExistsException(report.reportId(), exists);
    }
  }
}
