// MOCK of the marking definitions import/export backend (issue #8690).
// Remove this file, and the USE_MOCK_IMPORT_EXPORT switch in marking-definition-actions.ts, once
// `GET /marking_definitions/export` and `POST /marking_definitions/import` exist.
//
// It applies the rules of the plan (us7-import-export-plan.md, section 5) to the uploaded file,
// against the markings the real search endpoint returns. Nothing is saved: created rows are only
// reported.
import { buildSearchPagination } from '../../components/common/queryable/QueryableUtils';
import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { FORMULA_PREFIXES, MARKING_CSV_COLUMNS, toMarkingDefinitionsCsv } from './marking-definition-csv';
import {
  MarkingDefinitionImportFileError,
  type MarkingDefinitionImportResult,
  type MarkingDefinitionImportRow,
  type MarkingDefinitionImportRowReason,
} from './marking-definition-import-types';

export const MOCK_MAX_FILE_SIZE_BYTES = 1000 * 1000;
export const MOCK_MAX_ROWS = 1000;
const MOCK_LATENCY_MS = 700;
const HEX_COLOR = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;
const NON_NEGATIVE_INTEGER = /^\d+$/;
const BYTE_ORDER_MARK = 0xFEFF;

type SearchFn = (input: ReturnType<typeof buildSearchPagination>) => Promise<{
  data: {
    content: MarkingDefinitionOutput[];
    last?: boolean;
  };
}>;

const sleep = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms));

// -- CSV --

const parseCsvLine = (line: string): string[] => {
  const cells: string[] = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i += 1) {
    const char = line[i];
    if (inQuotes) {
      if (char === '"' && line[i + 1] === '"') {
        current += '"';
        i += 1;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        current += char;
      }
    } else if (char === '"') {
      inQuotes = true;
    } else if (char === ',') {
      cells.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  cells.push(current);
  return cells;
};

// Undo the export's formula prefix so an exported file re-imports unchanged.
const stripFormulaNeutralization = (value: string) => (value.startsWith('\'') && FORMULA_PREFIXES.includes(value[1]) ? value.slice(1) : value);

interface RawRow {
  line: number;
  type: string;
  definition: string;
  color: string;
  order: string;
}

const parseFile = (text: string): RawRow[] => {
  const withoutBom = text.charCodeAt(0) === BYTE_ORDER_MARK ? text.slice(1) : text;
  const lines = withoutBom.split(/\r?\n/);
  while (lines.length > 0 && lines[lines.length - 1].trim() === '') {
    lines.pop();
  }
  if (lines.length === 0) {
    throw new MarkingDefinitionImportFileError('The file is empty.');
  }
  const header = parseCsvLine(lines[0]).map(cell => cell.trim().toLowerCase());
  const missing = MARKING_CSV_COLUMNS.filter(column => !header.includes(column));
  if (missing.length > 0) {
    throw new MarkingDefinitionImportFileError(`Missing column(s): ${missing.join(', ')}. Expected header: ${MARKING_CSV_COLUMNS.join(',')}.`);
  }
  if (lines.length - 1 > MOCK_MAX_ROWS) {
    throw new MarkingDefinitionImportFileError(`The file has more than ${MOCK_MAX_ROWS} rows.`);
  }
  const indexOf = (column: string) => header.indexOf(column);
  return lines.slice(1).map((line, index) => {
    const cells = parseCsvLine(line);
    const cell = (column: string) => stripFormulaNeutralization((cells[indexOf(column)] ?? '').trim());
    return {
      line: index + 2,
      type: cell('type'),
      definition: cell('definition'),
      color: cell('color'),
      order: cell('order'),
    };
  });
};

// -- Rules --

const normalize = (value: string) => value.trim().toUpperCase();
const keyOf = (type: string, definition: string) => `${normalize(type)}\u0000${normalize(definition)}`;

const toRow = (
  raw: RawRow,
  status: 'CREATED' | 'SKIPPED',
  reason?: MarkingDefinitionImportRowReason,
  message?: string,
): MarkingDefinitionImportRow => ({
  import_row_line: raw.line,
  import_row_type: raw.type,
  import_row_definition: raw.definition,
  import_row_color: raw.color,
  import_row_order: raw.order,
  import_row_status: status,
  import_row_reason: reason,
  import_row_message: message,
});

const applyRules = (
  rawRows: RawRow[],
  existing: Map<string, MarkingDefinitionOutput>,
): MarkingDefinitionImportResult => {
  const acceptedInFile = new Map<string, number>();
  const rows = rawRows.map((raw) => {
    const emptyFields = (['type', 'definition', 'color', 'order'] as const).filter(field => raw[field] === '');
    if (emptyFields.length > 0) {
      return toRow(raw, 'SKIPPED', 'MISSING_FIELD', `Missing value for: ${emptyFields.join(', ')}.`);
    }
    if (!HEX_COLOR.test(raw.color)) {
      return toRow(raw, 'SKIPPED', 'INVALID_COLOR', `"${raw.color}" is not a valid hex colour (expected #rgb or #rrggbb).`);
    }
    if (!NON_NEGATIVE_INTEGER.test(raw.order)) {
      return toRow(raw, 'SKIPPED', 'INVALID_ORDER', `"${raw.order}" is not a whole number greater than or equal to 0.`);
    }
    const key = keyOf(raw.type, raw.definition);
    const firstLine = acceptedInFile.get(key);
    if (firstLine !== undefined) {
      return toRow(raw, 'SKIPPED', 'DUPLICATE_IN_FILE', `Same type and definition as line ${firstLine}. Only the first one is imported.`);
    }
    const found = existing.get(key);
    if (found) {
      return toRow(
        raw,
        'SKIPPED',
        'ALREADY_EXISTS',
        `Already exists with order ${found.marking_definition_order} and colour ${found.marking_definition_color}. The existing marking was not changed.`,
      );
    }
    acceptedInFile.set(key, raw.line);
    return toRow(raw, 'CREATED');
  });
  const created = rows.filter(row => row.import_row_status === 'CREATED').length;
  return {
    import_created: created,
    import_skipped: rows.length - created,
    import_rows: rows,
  };
};

// -- Existing markings (real search endpoint) --

const fetchAllExisting = async (search: SearchFn): Promise<MarkingDefinitionOutput[]> => {
  const all: MarkingDefinitionOutput[] = [];
  for (let page = 0; page < 50; page += 1) {
    // Pages are fetched one after the other: whether there is a next one depends on this answer.
    // eslint-disable-next-line no-await-in-loop
    const { data } = await search(buildSearchPagination({
      page,
      size: 100,
    }));
    all.push(...data.content);
    if (data.last !== false) {
      break;
    }
  }
  return all;
};

// -- Mocked endpoints --

export const mockExportMarkingDefinitions = async (search: SearchFn) => {
  const existing = await fetchAllExisting(search);
  await sleep(MOCK_LATENCY_MS / 2);
  const sorted = [...existing].sort((a, b) =>
    a.marking_definition_type.localeCompare(b.marking_definition_type)
    || a.marking_definition_order - b.marking_definition_order);
  const csv = toMarkingDefinitionsCsv(sorted.map(item => [
    item.marking_definition_type,
    item.marking_definition_definition,
    item.marking_definition_color,
    String(item.marking_definition_order),
  ]));
  const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 12);
  return {
    data: csv,
    filename: `MarkingDefinitions${stamp}.csv`,
  };
};

export const mockImportMarkingDefinitions = async (
  file: File,
  search: SearchFn,
): Promise<MarkingDefinitionImportResult> => {
  if (!file.name.toLowerCase().endsWith('.csv')) {
    throw new MarkingDefinitionImportFileError('Only .csv files are accepted.');
  }
  if (file.size >= MOCK_MAX_FILE_SIZE_BYTES) {
    throw new MarkingDefinitionImportFileError('The file cannot be larger than 1 MB.');
  }
  const rawRows = parseFile(await file.text());
  const existing = await fetchAllExisting(search);
  await sleep(MOCK_LATENCY_MS);
  const existingByKey = new Map(existing.map(item => [keyOf(item.marking_definition_type, item.marking_definition_definition), item]));
  return applyRules(rawRows, existingByKey);
};

// A file that triggers every kind of conflict, to see the report without writing a CSV by hand.
// The ALREADY_EXISTS rows rely on the default TLP markings seeded in every tenant.
export const buildMockConflictSampleCsv = () => toMarkingDefinitionsCsv([
  ['TLP', 'TLP:PURPLE', '#800080', '6'],
  ['TLP', 'TLP:AMBER', '#ffc107', '9'],
  ['TLP', 'TLP:CLEAR', '#ffffff', '1'],
  ['PAP', 'PAP:GREEN', '#00ff00', '2'],
  ['PAP', 'pap:green', '#00cc00', '3'],
  ['PAP', 'PAP:AMBER', 'orange', '3'],
  ['PAP', 'PAP:RED', '#ff0000', '-1'],
  ['', 'PAP:CLEAR', '#ffffff', '1'],
]);
