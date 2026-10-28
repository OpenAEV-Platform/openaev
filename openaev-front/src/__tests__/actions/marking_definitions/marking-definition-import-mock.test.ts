import { describe, expect, it } from 'vitest';

import { toMarkingDefinitionsCsv } from '../../../actions/marking_definitions/marking-definition-csv';
import {
  buildMockConflictSampleCsv,
  mockImportMarkingDefinitions,
} from '../../../actions/marking_definitions/marking-definition-import-mock';
import { MarkingDefinitionImportFileError } from '../../../actions/marking_definitions/marking-definition-import-types';
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
  it('reports every kind of conflict of the sample file, one reason per row', async () => {
    // Arrange
    const file = csvFile(buildMockConflictSampleCsv());

    // Act
    const result = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(result.import_created).toBe(2);
    expect(result.import_skipped).toBe(6);
    expect(result.import_rows.map(row => [row.import_row_line, row.import_row_reason ?? 'CREATED'])).toEqual([
      [2, 'CREATED'],
      [3, 'ALREADY_EXISTS'],
      [4, 'ALREADY_EXISTS'],
      [5, 'CREATED'],
      [6, 'DUPLICATE_IN_FILE'],
      [7, 'INVALID_COLOR'],
      [8, 'INVALID_ORDER'],
      [9, 'MISSING_FIELD'],
    ]);
  });

  it('never overwrites: an existing marking with another order is reported with its current values', async () => {
    // Arrange
    const file = csvFile(toMarkingDefinitionsCsv([['TLP', 'TLP:AMBER', '#000000', '9']]));

    // Act
    const result = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(result.import_created).toBe(0);
    expect(result.import_rows[0].import_row_reason).toBe('ALREADY_EXISTS');
    expect(result.import_rows[0].import_row_message).toContain('order 3');
    expect(result.import_rows[0].import_row_message).toContain('#ffc107');
  });

  it('re-imports an exported file unchanged, including a formula-looking cell', async () => {
    // Arrange
    const file = csvFile(toMarkingDefinitionsCsv([['TLP', '-1', '#ffffff', '4']]));

    // Act
    const result = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(result.import_rows[0].import_row_definition).toBe('-1');
    expect(result.import_rows[0].import_row_status).toBe('CREATED');
  });

  it('accepts reordered columns and ignores unknown ones', async () => {
    // Arrange
    const file = csvFile('order,note,color,definition,type\n7,hello,#abc,TLP:PURPLE,TLP');

    // Act
    const result = await mockImportMarkingDefinitions(file, search);

    // Assert
    expect(result.import_created).toBe(1);
    expect(result.import_rows[0].import_row_order).toBe('7');
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
    await expect(run).rejects.toBeInstanceOf(MarkingDefinitionImportFileError);
    await expect(run).rejects.toThrow(message);
  });
});
