import { type ImportReport, type ImportReportRow } from '../../../utils/api-types';

// Same separator as the backend (opencsv DEFAULT_SEPARATOR), so the file can be imported again.
const CSV_SEPARATOR = ',';
const CSV_LINE_BREAK = '\r\n';

export const skippedRowsOf = (report: ImportReport): ImportReportRow[] => report.import_rows.filter(row => row.import_row_status === 'SKIPPED');

// A spreadsheet runs a cell starting with one of these as a formula: same rule as the export of the
// backend (D11), which takes the leading ' back off when the file is imported.
// A value already starting with quotes in front of one of them gets one more (see CsvInjectionUtils).
const neutralise = (value: string): string => (/^'*[=+\-@\t\r\n]/.test(value) ? `'${value}` : value);

const toCsvCell = (value: string): string => {
  const neutralised = neutralise(value);
  return /[",\r\n]/.test(neutralised) ? `"${neutralised.replaceAll('"', '""')}"` : neutralised;
};

// The skipped rows as a CSV file (header = the report fields, same order), so the user fixes them
// and imports the result again. A value the user left out is an empty cell, which the import
// reports as missing again.
export const skippedRowsToCsv = (report: ImportReport): string => {
  const header = report.import_columns.join(CSV_SEPARATOR);
  const lines = skippedRowsOf(report).map(row => report.import_columns
    .map(column => toCsvCell(row.import_row_values[column] ?? ''))
    .join(CSV_SEPARATOR));
  return [header, ...lines].join(CSV_LINE_BREAK) + CSV_LINE_BREAK;
};
