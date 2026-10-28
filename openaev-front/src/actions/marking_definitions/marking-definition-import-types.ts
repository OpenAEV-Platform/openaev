// Reason codes of the marking definitions import: the only marking-specific part of the report,
// which is the generic ImportReport (components/common/import/import-report-types.ts). The backend
// puts the code in `import_row_reason`.
export type MarkingDefinitionImportRowReason
  = | 'MISSING_FIELD'
    | 'INVALID_COLOR'
    | 'INVALID_ORDER'
    | 'CONFLICT';
