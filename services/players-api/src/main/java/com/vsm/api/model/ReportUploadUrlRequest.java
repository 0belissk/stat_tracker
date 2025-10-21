package com.vsm.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public class ReportUploadUrlRequest {

  @NotBlank
  @Pattern(
      regexp = "(?i).*\\.csv$",
      message = "fileName must be a CSV file ending with .csv")
  private String fileName;

  private String contentType;

  @Positive(message = "contentLength must be positive")
  private Long contentLength;

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public Long getContentLength() {
    return contentLength;
  }

  public void setContentLength(Long contentLength) {
    this.contentLength = contentLength;
  }
}
