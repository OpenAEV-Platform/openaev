// Generic report of a file import (D9 of us7-import-export-plan.md): it knows nothing about the
// imported entity, so any import can return it and render it with ImportReportDialog.
// Hand-written while the backend `ImportReport` records do not exist: replace with the generated
// api-types (`yarn generate-types-from-api`) once the Java side is in.

export type ImportReportRowStatus = 'CREATED' | 'SKIPPED' | 'IGNORED';

export interface ImportReportRow {
  // 1-based position of the row in the file: the index of the item in a JSON array (the first item
  // is 1), the line of a CSV file.
  import_row_position: number;
  import_row_status: ImportReportRowStatus;
  // Feature-owned code, only on SKIPPED rows.
  import_row_reason?: string;
  // Fixed text built server-side, only on SKIPPED rows.
  import_row_message?: string;
  // The row's fields as read (scalars as text), keyed by field name. An invalid value has to be
  // shown as the user wrote it.
  import_row_values: Record<string, string>;
}

export interface ImportReport {
  import_created: number;
  // Rejected rows, each with a reason.
  import_skipped: number;
  // Rows identical to an existing or earlier row: nothing to do, not an error.
  import_ignored: number;
  // The field names of a row, in display order.
  import_columns: string[];
  import_rows: ImportReportRow[];
}

// File-level failure (invalid JSON, file too big, too many items...): nothing is imported and there
// is no per-row report. The backend answers 400 with a message.
export class ImportFileError extends Error {}
