// MOCK of the marking definitions import/export backend (issue #8690).
// Remove this file, and the USE_MOCK_IMPORT_EXPORT switch in marking-definition-actions.ts, once
// `GET /marking_definitions/export` and `POST /marking_definitions/import` exist.
//
// It applies the rules of the plan (us7-import-export-plan.md, sections 4 and 5) to the uploaded file,
// against the markings the real search endpoint returns. Nothing is saved: created rows are only
// reported.
import { ImportFileError, type ImportReport, type ImportReportRow } from '../../components/common/import/import-report-types';
import { buildSearchPagination } from '../../components/common/queryable/QueryableUtils';
import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { type MarkingDefinitionImportRowReason } from './marking-definition-import-types';

// The four fields of an item of the file, in display order (section 4 of the plan).
export const MARKING_FIELDS = ['type', 'definition', 'color', 'order'] as const;

export const MOCK_MAX_FILE_SIZE_BYTES = 1000 * 1000;
export const MOCK_MAX_ITEMS = 1000;
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

// -- JSON --

interface RawRow {
  // 1-based position of the item in the array.
  position: number;
  type: string;
  definition: string;
  color: string;
  order: string;
}

// A scalar is read as text, so the order 3 and "3" are the same. A missing key, a null, an object or
// an array read as empty.
const scalarText = (value: unknown): string => {
  if (typeof value === 'string') {
    return value.trim();
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return '';
};

// Items are read one by one, never bound to a typed object: a bad value is one row's outcome, not a
// failure of the whole file.
const parseFile = (text: string): RawRow[] => {
  const withoutBom = text.charCodeAt(0) === BYTE_ORDER_MARK ? text.slice(1) : text;
  let parsed: unknown;
  try {
    parsed = JSON.parse(withoutBom);
  } catch {
    throw new ImportFileError('The file is not valid JSON.');
  }
  if (!Array.isArray(parsed)) {
    throw new ImportFileError('The file must contain a JSON array of marking definitions.');
  }
  if (parsed.length === 0) {
    throw new ImportFileError('The file contains no marking definition.');
  }
  if (parsed.length > MOCK_MAX_ITEMS) {
    throw new ImportFileError(`The file has more than ${MOCK_MAX_ITEMS} items.`);
  }
  return parsed.map((item: unknown, index) => {
    if (typeof item !== 'object' || item === null || Array.isArray(item)) {
      throw new ImportFileError(`Item ${index + 1} is not an object.`);
    }
    const fields = item as Record<string, unknown>;
    return {
      position: index + 1,
      type: scalarText(fields.type),
      definition: scalarText(fields.definition),
      color: scalarText(fields.color),
      order: scalarText(fields.order),
    };
  });
};

// -- Rules --

const normalize = (value: string) => value.trim().toUpperCase();
const keyOf = (type: string, definition: string) => `${normalize(type)}\u0000${normalize(definition)}`;

const toRow = (
  raw: RawRow,
  status: ImportReportRow['import_row_status'],
  reason?: MarkingDefinitionImportRowReason,
  message?: string,
): ImportReportRow => ({
  import_row_position: raw.position,
  import_row_status: status,
  import_row_reason: reason,
  import_row_message: message,
  import_row_values: {
    type: raw.type,
    definition: raw.definition,
    color: raw.color,
    order: raw.order,
  },
});

interface Other {
  // How the message names the other marking: "the existing marking" or "item 6".
  label: string;
  color: string;
  order: number;
}

// Same type and definition as another marking: identical (colour, whatever its case, and order) is
// ignored, anything else is a conflict whose message says exactly what differs. The other marking is
// never changed.
const compareWith = (raw: RawRow, other: Other): ImportReportRow => {
  const differences: string[] = [];
  if (raw.color.toLowerCase() !== other.color.toLowerCase()) {
    differences.push(`color differs (this row ${raw.color}, ${other.label} has ${other.color})`);
  }
  if (Number(raw.order) !== other.order) {
    differences.push(`order differs (this row ${raw.order}, ${other.label} has ${other.order})`);
  }
  if (differences.length === 0) {
    return toRow(raw, 'IGNORED');
  }
  return toRow(raw, 'SKIPPED', 'CONFLICT', `Same type and definition as ${other.label}, but ${differences.join(' and ')}. Nothing was changed.`);
};

const applyRules = (
  rawRows: RawRow[],
  existing: Map<string, MarkingDefinitionOutput>,
): ImportReport => {
  const firstInFile = new Map<string, RawRow>();
  const rows = rawRows.map((raw) => {
    const emptyFields = MARKING_FIELDS.filter(field => raw[field] === '');
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
    // The existing marking is checked first: a key that exists in the tenant is always compared
    // with it, whatever the earlier rows of the file.
    const found = existing.get(key);
    if (found) {
      return compareWith(raw, {
        label: 'the existing marking',
        color: found.marking_definition_color,
        order: found.marking_definition_order,
      });
    }
    const earlier = firstInFile.get(key);
    if (earlier) {
      return compareWith(raw, {
        label: `item ${earlier.position}`,
        color: earlier.color,
        order: Number(earlier.order),
      });
    }
    firstInFile.set(key, raw);
    return toRow(raw, 'CREATED');
  });
  const countOf = (status: ImportReportRow['import_row_status']) => rows.filter(row => row.import_row_status === status).length;
  return {
    import_created: countOf('CREATED'),
    import_skipped: countOf('SKIPPED'),
    import_ignored: countOf('IGNORED'),
    import_columns: [...MARKING_FIELDS],
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
  // No id, no tenant, no protected flag: the file only carries what an import reads (section 4).
  const json = JSON.stringify(sorted.map(item => ({
    type: item.marking_definition_type,
    definition: item.marking_definition_definition,
    color: item.marking_definition_color,
    order: item.marking_definition_order,
  })), null, 2);
  const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 12);
  return {
    data: json,
    filename: `MarkingDefinitions${stamp}.json`,
  };
};

export const mockImportMarkingDefinitions = async (
  file: File,
  search: SearchFn,
): Promise<ImportReport> => {
  if (!file.name.toLowerCase().endsWith('.json')) {
    throw new ImportFileError('Only .json files are accepted.');
  }
  if (file.size >= MOCK_MAX_FILE_SIZE_BYTES) {
    throw new ImportFileError('The file cannot be larger than 1 MB.');
  }
  const rawRows = parseFile(await file.text());
  const existing = await fetchAllExisting(search);
  await sleep(MOCK_LATENCY_MS);
  const existingByKey = new Map(existing.map(item => [keyOf(item.marking_definition_type, item.marking_definition_definition), item]));
  return applyRules(rawRows, existingByKey);
};

const sampleItem = (type: string, definition: string, color: string, order: number): Record<string, unknown> => ({
  type,
  definition,
  color,
  order,
});

// A file that triggers every outcome, to see the report without writing a JSON file by hand. The
// items against an existing marking rely on the default TLP markings seeded in every tenant
// (TLP:CLEAR #ffffff order 1, TLP:AMBER #ffc107 order 3).
//   item 1 created, 2 conflict (order), 3 conflict (color), 4 ignored (identical to TLP:CLEAR),
//   5 created, 6 conflict with item 5 (color and order), 7 ignored (identical to item 5),
//   8 invalid colour, 9 invalid order, 10 missing value (no `type` key).
export const buildMockConflictSampleJson = () => JSON.stringify([
  sampleItem('TLP', 'TLP:PURPLE', '#800080', 6),
  sampleItem('TLP', 'TLP:AMBER', '#ffc107', 9),
  sampleItem('TLP', 'TLP:AMBER', '#000000', 3),
  sampleItem('TLP', 'TLP:CLEAR', '#ffffff', 1),
  sampleItem('PAP', 'PAP:GREEN', '#00ff00', 2),
  sampleItem('PAP', 'pap:green', '#00cc00', 3),
  sampleItem('PAP', 'PAP:GREEN', '#00FF00', 2),
  sampleItem('PAP', 'PAP:AMBER', 'orange', 3),
  sampleItem('PAP', 'PAP:RED', '#ff0000', -1),
  {
    definition: 'PAP:CLEAR',
    color: '#ffffff',
    order: 1,
  },
], null, 2);
