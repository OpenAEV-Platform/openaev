// CSV helpers shared by the exports and the imports: header row, comma separator.

// A cell starting with one of these is read by spreadsheets as a formula.
export const FORMULA_PREFIXES = ['=', '+', '-', '@'];

const escapeCsvCell = (value: string) => (/[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value);

// Prefix a formula-looking cell so it is read as text. The import strips the prefix again, so an
// exported file re-imports unchanged.
export const neutralizeFormula = (value: string) => (FORMULA_PREFIXES.some(prefix => value.startsWith(prefix)) ? `'${value}` : value);

// Undo neutralizeFormula.
export const stripFormulaNeutralization = (value: string) => (value.startsWith('\'') && FORMULA_PREFIXES.includes(value[1]) ? value.slice(1) : value);

export const toCsv = (columns: readonly string[], rows: string[][]) => [
  columns.join(','),
  ...rows.map(row => row.map(cell => escapeCsvCell(neutralizeFormula(cell))).join(',')),
].join('\n');
