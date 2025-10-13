package com.vsm.api.domain.report.exception;

public class ReportAlreadyExistsException extends RuntimeException {

  private final String reportId;

  public ReportAlreadyExistsException(String reportId, Throwable cause) {
    super("Report already exists for id %s".formatted(reportId), cause);
    this.reportId = reportId;
  }

  public String getReportId() {
    return reportId;
  }
}
