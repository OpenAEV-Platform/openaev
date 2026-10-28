import { describe, expect, it } from 'vitest';

import { toMarkingDefinitionsCsv } from '../../../actions/marking_definitions/marking-definition-csv';
import {
  buildMockConflictSampleCsv,
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

const csvFile = (content: string, name = 'markings.csv') => new File([content], name, { type: 'text/csv' });

describe('mockImportMarkingDefinitions', () => {
  it('reports every outcome of the sample file, one per row', async () => {
    // Arrange
    const file = csvFile(buildMockConflictSampleCsv());

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_columns).toEqual(['type', 'definition', 'color', 'order']);
    expect(report.import_created).toBe(2);
    expect(report.import_skipped).toBe(6);
    expect(report.import_ignored).toBe(2);
    expect(report.import_rows.map(row => [row.import_row_line, row.import_row_status, row.import_row_reason ?? null])).toEqual([
      [2, 'CREATED', null],
      [3, 'SKIPPED', 'CONFLICT'],
      [4, 'SKIPPED', 'CONFLICT'],
      [5, 'IGNORED', null],
      [6, 'CREATED', null],
      [7, 'SKIPPED', 'CONFLICT'],
      [8, 'IGNORED', null],
      [9, 'SKIPPED', 'INVALID_COLOR'],
      [10, 'SKIPPED', 'INVALID_ORDER'],
      [11, 'SKIPPED', 'MISSING_FIELD'],
    ]);
  });

  it('never overwrites: a conflict with an existing marking says what differs and its current values', async () => {
    // Arrange
    const file = csvFile(toMarkingDefinitionsCsv([['TLP', 'TLP:AMBER', '#000000', '9']]));

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    const [row] = report.import_rows;
    expect(report.import_created).toBe(0);
    expect(row.import_row_reason).toBe('CONFLICT');
    expect(row.import_row_message).toContain('the existing marking');
    expect(row.import_row_message).toContain('color differs (this row #000000, the existing marking #ffc107)');
    expect(row.import_row_message).toContain('order differs (this row 9, the existing marking 3)');
  });

  it('names only what differs: colour alone, then order alone', async () => {
    // Arrange
    const file = csvFile(toMarkingDefinitionsCsv([
      ['TLP', 'TLP:AMBER', '#000000', '3'],
      ['TLP', 'TLP:CLEAR', '#ffffff', '8'],
    ]));

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    const [colourOnly, orderOnly] = report.import_rows.map(row => row.import_row_message);
    expect(colourOnly).toContain('color differs');
    expect(colourOnly).not.toContain('order differs');
    expect(orderOnly).toContain('order differs');
    expect(orderOnly).not.toContain('color differs');
  });

  it('ignores a row identical to an existing marking, whatever the case of its colour', async () => {
    // Arrange
    const file = csvFile(toMarkingDefinitionsCsv([['tlp', 'tlp:amber', '#FFC107', '3']]));

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_ignored).toBe(1);
    expect(report.import_skipped).toBe(0);
    expect(report.import_rows[0].import_row_status).toBe('IGNORED');
    expect(report.import_rows[0].import_row_reason).toBeUndefined();
  });

  it('compares a repeated row with the first one of the file when the marking is new', async () => {
    // Arrange
    const file = csvFile(toMarkingDefinitionsCsv([
      ['PAP', 'PAP:GREEN', '#00ff00', '2'],
      ['PAP', 'PAP:GREEN', '#00ff00', '2'],
      ['PAP', 'PAP:GREEN', '#00ff00', '5'],
    ]));

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_rows.map(row => row.import_row_status)).toEqual(['CREATED', 'IGNORED', 'SKIPPED']);
    expect(report.import_rows[2].import_row_message).toContain('line 2');
  });

  it('re-imports an exported file unchanged, including a formula-looking cell', async () => {
    // Arrange
    const file = csvFile(toMarkingDefinitionsCsv([['TLP', '-1', '#ffffff', '4']]));

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_rows[0].import_row_values.definition).toBe('-1');
    expect(report.import_rows[0].import_row_status).toBe('CREATED');
  });

  it('accepts reordered columns and ignores unknown ones', async () => {
    // Arrange
    const file = csvFile('order,note,color,definition,type\n7,hello,#abc,TLP:PURPLE,TLP');

    // Act
    const report = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(report.import_created).toBe(1);
    expect(report.import_rows[0].import_row_values.order).toBe('7');
  });

  it.each([
    ['a missing column', 'type,definition,color\nTLP,TLP:PURPLE,#fff', 'markings.csv', 'Missing column(s): order'],
    ['an empty file', '', 'markings.csv', 'The file is empty.'],
    ['a wrong extension', 'type,definition,color,order', 'markings.txt', 'Only .csv files are accepted.'],
  ])('rejects the whole file on %s', async (_label, content, name, message) => {
    // Arrange
    const file = csvFile(content, name);

    // Act
    const run = mockImportMarkingDefinitions(file, search);

    // Assert
    await expect(run).rejects.toBeInstanceOf(ImportFileError);
    await expect(run).rejects.toThrow(message);
  });
});
