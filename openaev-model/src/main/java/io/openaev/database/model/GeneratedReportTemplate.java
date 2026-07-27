package io.openaev.database.model;

/**
 * The two fixed, non-customizable report templates supported by the structured PDF "Reports"
 * feature. No other template can be added or edited by end users; template content is defined in
 * code (frontend PDF builders).
 */
public enum GeneratedReportTemplate {
  TECHNICAL,
  EXECUTIVE
}
