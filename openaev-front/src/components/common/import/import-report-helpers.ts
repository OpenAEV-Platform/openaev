import { type ImportReport, type ImportReportRow } from '../../../utils/api-types';

export const skippedRowsOf = (report: ImportReport): ImportReportRow[] => report.import_rows.filter(row => row.import_row_status === 'SKIPPED');

// A spreadsheet runs a cell starting with one of these as a formula: same rule as the export of the
// backend (D11), which takes the leading ' back off when the file is imported.
// A value already starting with quotes in front of one of them gets one more (see CsvInjectionUtils).
const neutralise = (value: string): string => (/^'*[=+\-@\t\r\n]/.test(value) ? `'${value}` : value);

// react-csv wraps every cell in quotes but does not escape a quote inside it: double it here, or a
// value like say "hi" would corrupt the line.
const toCsvCell = (value: string): string => neutralise(value).replaceAll('"', '""');

// The cells of the skipped rows for the CSV file, in the order of `columns`, so the user fixes them
// and imports the result again. A value the user left out is an empty cell, which the import
// reports as missing again.
export const skippedRowsToCells = (rows: ImportReportRow[], columns: string[]): string[][] => rows
  .map(row => columns.map(column => toCsvCell(row.import_row_values[column] ?? '')));
