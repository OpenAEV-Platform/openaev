// Shape of the import report returned by `POST /marking_definitions/import`.
// Proposed contract, kept by hand while the backend endpoint does not exist: replace with the
// generated api-types (`yarn generate-types-from-api`) once the Java records are in.

export type MarkingDefinitionImportRowStatus = 'CREATED' | 'SKIPPED';

export type MarkingDefinitionImportRowReason
  = | 'MISSING_FIELD'
    | 'INVALID_COLOR'
    | 'INVALID_ORDER'
    | 'DUPLICATE_IN_FILE'
    | 'ALREADY_EXISTS';

export interface MarkingDefinitionImportRow {
  import_row_line: number;
  import_row_type?: string;
  import_row_definition?: string;
  import_row_color?: string;
  // Raw text: an invalid order ("abc", "-1") has to be shown as the user wrote it.
  import_row_order?: string;
  import_row_status: MarkingDefinitionImportRowStatus;
  import_row_reason?: MarkingDefinitionImportRowReason;
  import_row_message?: string;
}

// File-level failure (bad header, file too big, too many rows...): nothing is imported and there is
// no per-row report. The backend answers 400 with a message.
export class MarkingDefinitionImportFileError extends Error {}

export interface MarkingDefinitionImportResult {
  import_created: number;
  import_skipped: number;
  import_rows: MarkingDefinitionImportRow[];
}
