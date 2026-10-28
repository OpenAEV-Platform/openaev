// Generic report of a file import (D9 of us7-import-export-plan.md): it knows nothing about the
// imported entity, so any import can return it and render it with ImportReportDialog.
// Hand-written while the backend `ImportReport` records do not exist: replace with the generated
// api-types (`yarn generate-types-from-api`) once the Java side is in.

export type ImportReportRowStatus = 'CREATED' | 'SKIPPED' | 'IGNORED';

export interface ImportReportRow {
  // Line of the file, header included (the header is line 1).
  import_row_line: number;
  import_row_status: ImportReportRowStatus;
  // Feature-owned code, only on SKIPPED rows.
  import_row_reason?: string;
  // Fixed text built server-side, only on SKIPPED rows.
  import_row_message?: string;
  // The row's cells as read, keyed by column name. Raw text: an invalid value has to be shown as
  // the user wrote it.
  import_row_values: Record<string, string>;
}

export interface ImportReport {
  import_created: number;
  // Rejected rows, each with a reason.
  import_skipped: number;
  // Rows identical to an existing or earlier row: nothing to do, not an error.
  import_ignored: number;
  // The column names of the file, in order.
  import_columns: string[];
  import_rows: ImportReportRow[];
}

// File-level failure (bad header, file too big, too many rows...): nothing is imported and there is
// no per-row report. The backend answers 400 with a message.
export class ImportFileError extends Error {}
