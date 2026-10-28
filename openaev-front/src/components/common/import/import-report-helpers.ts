import { toCsv } from './import-report-csv';
import { type ImportReport, type ImportReportRow } from './import-report-types';

export const skippedRowsOf = (report: ImportReport): ImportReportRow[] => report.import_rows.filter(row => row.import_row_status === 'SKIPPED');

// The skipped rows in the format of the file that was imported (same columns, same order), so the
// user fixes them and imports the result again.
export const skippedRowsToCsv = (report: ImportReport): string => toCsv(
  report.import_columns,
  skippedRowsOf(report).map(row => report.import_columns.map(column => row.import_row_values[column] ?? '')),
);
