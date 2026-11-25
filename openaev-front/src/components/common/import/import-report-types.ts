import { type ImportReportRow } from '../../../utils/api-types';

// The report of a file import is the generic `ImportReport` of the API types (D9 of
// us7-import-export-plan.md): it knows nothing about the imported entity, so any import can return
// it and render it with ImportReportDialog.

// Why a row was skipped. The reasons are shared by every import, as the API types declare them.
export type ImportRowReason = NonNullable<ImportReportRow['import_row_reason']>;

// Tone of the chip of a reason.
export type ReasonSeverity = 'medium' | 'critical';

// File-level failure (empty file, missing column, too big, too many rows...): nothing is imported
// and there is no per-row report. The backend answers 400 with a message.
export class ImportFileError extends Error {}
