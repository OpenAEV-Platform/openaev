// CSV format of the marking definitions export/import (issue #8690): header row, comma separator.
export const MARKING_CSV_COLUMNS = ['type', 'definition', 'color', 'order'] as const;

// A cell starting with one of these is read by spreadsheets as a formula.
export const FORMULA_PREFIXES = ['=', '+', '-', '@'];

const escapeCsvCell = (value: string) => (/[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value);

// Prefix a formula-looking cell so it is read as text. The import strips the prefix again, so an
// exported file re-imports unchanged.
const neutralizeFormula = (value: string) => (FORMULA_PREFIXES.some(prefix => value.startsWith(prefix)) ? `'${value}` : value);

export const toMarkingDefinitionsCsv = (rows: string[][]) => [
  MARKING_CSV_COLUMNS.join(','),
  ...rows.map(row => row.map(cell => escapeCsvCell(neutralizeFormula(cell))).join(',')),
].join('\n');
