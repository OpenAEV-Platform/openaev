package io.openaev.service.import_report;

/**
 * Why a row of an imported file is skipped. The client writes the sentence of each reason in its
 * own language, from the row's params.
 */
public enum ImportRowReason {
  MISSING_FIELD,
  VALUE_TOO_LONG,
  /** The value of a column is malformed; the param {@code column} names the column. */
  INVALID_VALUE,
  CONFLICT,
  INSUFFICIENT_ACCESS
}
