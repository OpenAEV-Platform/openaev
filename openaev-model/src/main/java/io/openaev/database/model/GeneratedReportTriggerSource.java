package io.openaev.database.model;

/** What triggered a report's generation, for audit/traceability purposes. */
public enum GeneratedReportTriggerSource {
  /** User clicked "Generate" explicitly. */
  MANUAL,
  /** Fired automatically the moment a simulation completed. */
  AUTO_ON_COMPLETION,
  /** Fired by a (future) scheduler/cron - not implemented yet. */
  SCHEDULED
}
