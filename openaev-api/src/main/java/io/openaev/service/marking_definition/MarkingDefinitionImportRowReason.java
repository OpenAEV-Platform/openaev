package io.openaev.service.marking_definition;

/** Why a row of an imported marking file is skipped; its {@code name()} is the report's code. */
public enum MarkingDefinitionImportRowReason {
  MISSING_FIELD,
  VALUE_TOO_LONG,
  INVALID_COLOR,
  INVALID_ORDER,
  CONFLICT,
  INSUFFICIENT_ACCESS
}
