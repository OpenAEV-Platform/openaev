package io.openaev.database.model;

/** Building blocks composing a {@link Reporting} template, rendered in order. */
public enum ReportingModuleType {
  COVER,
  EXECUTIVE_SUMMARY,
  SUBJECT_DETAILS,
  MITRE_COVERAGE,
  RESULTS_BREAKDOWN,
  SECURITY_DOMAINS,
  SCORE_TRENDS,
  FAILED_EXPECTATIONS,
  FINDINGS,
  ATTACK_PATHS,
  CUSTOM_MARKDOWN
}
