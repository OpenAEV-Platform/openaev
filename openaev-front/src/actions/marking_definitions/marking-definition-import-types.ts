// Reason codes of the marking definitions import: the only marking-specific part of the report,
// which is the generic ImportReport of the API types. The backend puts the code in
// `import_row_reason`.
export type MarkingDefinitionImportRowReason
  = | 'MISSING_FIELD'
    | 'VALUE_TOO_LONG'
    | 'INVALID_COLOR'
    | 'INVALID_ORDER'
    | 'CONFLICT'
    | 'INSUFFICIENT_ACCESS';
