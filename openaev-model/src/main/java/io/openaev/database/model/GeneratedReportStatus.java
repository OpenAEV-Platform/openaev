package io.openaev.database.model;

/** Lifecycle status of an asynchronously generated structured PDF report. */
public enum GeneratedReportStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED
}
