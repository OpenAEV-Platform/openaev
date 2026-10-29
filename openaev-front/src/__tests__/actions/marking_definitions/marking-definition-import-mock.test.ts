import { describe, expect, it } from 'vitest';

import {
  buildMockConflictSampleJson,
  MOCK_MAX_ITEMS,
  mockExportMarkingDefinitions,
  mockImportMarkingDefinitions,
} from '../../../actions/marking_definitions/marking-definition-import-mock';
import { ImportFileError } from '../../../components/common/import/import-report-types';
import { type MarkingDefinitionOutput } from '../../../utils/api-types';

const existingMarking = (type: string, definition: string, color: string, order: number): MarkingDefinitionOutput => ({
  marking_definition_id: `${type}-${definition}`,
  marking_definition_type: type,
  marking_definition_definition: definition,
  marking_definition_color: color,
  marking_definition_order: order,
  marking_definition_protected: true,
  marking_definition_created_at: '2026-01-01T00:00:00Z',
});

// The default TLP markings every tenant is seeded with.
const search = () => Promise.resolve({
  data: {
    content: [
      existingMarking('TLP', 'TLP:CLEAR', '#ffffff', 1),
      existingMarking('TLP', 'TLP:AMBER', '#ffc107', 3),
    ],
    last: true,
  },
});

// Either the text of the file, or the items to write as a JSON array.
const jsonFile = (content: string | unknown[], name = 'markings.json') => new File(
  [typeof content === 'string' ? content : JSON.stringify(content)],
  name,
  { type: 'application/json' },
);

const item = (type: string, definition: string, color: string, order: number | string) => ({
  type,
  definition,
  color,
  order,
});

describe('mockImportMarkingDefinitions', () => {
  it('reports every outcome of the sample file, one per item', async () => {
    // Arrange
    const file = jsonFile(buildMockConflictSampleJson());

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_columns).toEqual(['type', 'definition', 'color', 'order']);
    expect(report.import_created).toBe(2);
    expect(report.import_skipped).toBe(6);
    expect(report.import_ignored).toBe(2);
    expect(report.import_rows.map(row => [row.import_row_position, row.import_row_status, row.import_row_reason ?? null])).toEqual([
      [1, 'CREATED', null],
      [2, 'SKIPPED', 'CONFLICT'],
      [3, 'SKIPPED', 'CONFLICT'],
      [4, 'IGNORED', null],
      [5, 'CREATED', null],
      [6, 'SKIPPED', 'CONFLICT'],
      [7, 'IGNORED', null],
      [8, 'SKIPPED', 'INVALID_COLOR'],
      [9, 'SKIPPED', 'INVALID_ORDER'],
      [10, 'SKIPPED', 'MISSING_FIELD'],
    ]);
  });

  it('never overwrites: a conflict with an existing marking says what differs and its current values', async () => {
    // Arrange
    const file = jsonFile([item('TLP', 'TLP:AMBER', '#000000', 9)]);

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    const [row] = report.import_rows;
    expect(report.import_created).toBe(0);
    expect(row.import_row_reason).toBe('CONFLICT');
    expect(row.import_row_message).toContain('the existing marking');
    expect(row.import_row_message).toContain('color differs (this row #000000, the existing marking has #ffc107)');
    expect(row.import_row_message).toContain('order differs (this row 9, the existing marking has 3)');
  });

  it('names only what differs: colour alone, then order alone', async () => {
    // Arrange
    const file = jsonFile([
      item('TLP', 'TLP:AMBER', '#000000', 3),
      item('TLP', 'TLP:CLEAR', '#ffffff', 8),
    ]);

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    const [colourOnly, orderOnly] = report.import_rows.map(row => row.import_row_message);
    expect(colourOnly).toContain('color differs');
    expect(colourOnly).not.toContain('order differs');
    expect(orderOnly).toContain('order differs');
    expect(orderOnly).not.toContain('color differs');
  });

  it('ignores an item identical to an existing marking, whatever the case of its colour', async () => {
    // Arrange
    const file = jsonFile([item('tlp', 'tlp:amber', '#FFC107', 3)]);

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_ignored).toBe(1);
    expect(report.import_skipped).toBe(0);
    expect(report.import_rows[0].import_row_status).toBe('IGNORED');
    expect(report.import_rows[0].import_row_reason).toBeUndefined();
  });

  it('compares a repeated item with the first one of the file when the marking is new', async () => {
    // Arrange
    const file = jsonFile([
      item('PAP', 'PAP:GREEN', '#00ff00', 2),
      item('PAP', 'PAP:GREEN', '#00ff00', 2),
      item('PAP', 'PAP:GREEN', '#00ff00', 5),
    ]);

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_rows.map(row => row.import_row_status)).toEqual(['CREATED', 'IGNORED', 'SKIPPED']);
    expect(report.import_rows[2].import_row_message).toContain('item 1 has 2');
  });

  it('re-imports an exported file unchanged, including quotes and accents', async () => {
    // Arrange
    const exported = await mockExportMarkingDefinitions(search);
    const file = jsonFile([...JSON.parse(exported.data), item('TLP', 'TLP:"ÉTÉ"', '#ffffff', 4)]);

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_rows.map(row => row.import_row_status)).toEqual(['IGNORED', 'IGNORED', 'CREATED']);
    expect(report.import_rows[2].import_row_values.definition).toBe('TLP:"ÉTÉ"');
  });

  it('accepts reordered keys, ignores unknown ones, and reads an order written as a string', async () => {
    // Arrange
    const file = jsonFile('[{"order":"7","note":"hello","color":"#abc","definition":"TLP:PURPLE","type":"TLP"}]');

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_created).toBe(1);
    expect(report.import_rows[0].import_row_values.order).toBe('7');
  });

  it('tolerates a byte order mark', async () => {
    // Arrange
    const file = jsonFile(`\uFEFF${JSON.stringify([item('TLP', 'TLP:PURPLE', '#abc', 7)])}`);

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_created).toBe(1);
  });

  it('skips only the item with a missing key, or a value that is not a scalar', async () => {
    // Arrange
    const file = jsonFile('[{"type":"TLP","definition":"TLP:PURPLE","color":"#abc"},'
      + '{"type":["TLP"],"definition":"TLP:BLUE","color":"#abc","order":1},'
      + '{"type":"TLP","definition":"TLP:GREEN","color":"#abc","order":2}]');

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_rows.map(row => [row.import_row_status, row.import_row_reason ?? null])).toEqual([
      ['SKIPPED', 'MISSING_FIELD'],
      ['SKIPPED', 'MISSING_FIELD'],
      ['CREATED', null],
    ]);
  });

  it.each([
    ['a decimal', 3.5],
    ['a boolean', true],
    ['a negative number', -1],
    ['text', 'abc'],
  ])('rejects %s as an order', async (_label, order) => {
    // Arrange
    const file = jsonFile([{
      ...item('TLP', 'TLP:PURPLE', '#abc', 0),
      order,
    }]);

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_rows[0].import_row_reason).toBe('INVALID_ORDER');
  });

  it.each([
    ['invalid JSON', '{nope', 'markings.json', 'The file is not valid JSON.'],
    ['an empty file', '', 'markings.json', 'The file is not valid JSON.'],
    ['an object instead of an array', '{}', 'markings.json', 'The file must contain a JSON array'],
    ['an empty array', '[]', 'markings.json', 'The file contains no marking definition.'],
    ['an item that is not an object', '[1]', 'markings.json', 'Item 1 is not an object.'],
    ['a wrong extension', '[]', 'markings.csv', 'Only .json files are accepted.'],
  ])('rejects the whole file on %s', async (_label, content, name, message) => {
    // Arrange
    const file = jsonFile(content, name);

    // Act
    const run = mockImportMarkingDefinitions(file, search);

    // Assert
    await expect(run).rejects.toBeInstanceOf(ImportFileError);
    await expect(run).rejects.toThrow(message);
  });

  it('rejects a file with too many items', async () => {
    // Arrange
    const file = jsonFile(Array.from({ length: MOCK_MAX_ITEMS + 1 }, (_, index) => item('PAP', `PAP:${index}`, '#abc', index)));

    // Act
    const run = mockImportMarkingDefinitions(file, search);

    // Assert
    await expect(run).rejects.toThrow(`more than ${MOCK_MAX_ITEMS} items`);
  });
});

describe('mockExportMarkingDefinitions', () => {
  it('writes a JSON array of the four fields only, sorted by type then order', async () => {
    // Act
    const { data, filename } = await mockExportMarkingDefinitions(search);

    // Assert
    expect(filename).toMatch(/^MarkingDefinitions\d{12}\.json$/);
    expect(JSON.parse(data)).toEqual([
      item('TLP', 'TLP:CLEAR', '#ffffff', 1),
      item('TLP', 'TLP:AMBER', '#ffc107', 3),
    ]);
  });
});
