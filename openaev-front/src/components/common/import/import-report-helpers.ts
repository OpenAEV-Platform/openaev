import { type ImportReport, type ImportReportRow } from './import-report-types';

export const skippedRowsOf = (report: ImportReport): ImportReportRow[] => report.import_rows.filter(row => row.import_row_status === 'SKIPPED');

// The skipped rows in the format of the file that was imported (a JSON array of flat objects, same
// fields, same order), so the user fixes them and imports the result again. Values stay strings: the
// import reads a scalar as text, so "3" is the same order as 3. A value the user left out is an
// empty string, which the import reports as missing again.
export const skippedRowsToJson = (report: ImportReport): string => JSON.stringify(
  skippedRowsOf(report).map(row => Object.fromEntries(
    report.import_columns.map(column => [column, row.import_row_values[column] ?? '']),
  )),
  null,
  2,
);
