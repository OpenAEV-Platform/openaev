import { describe, expect, it } from 'vitest';

import { skippedRowsOf, skippedRowsToJson } from '../../../../components/common/import/import-report-helpers';
import { type ImportReport } from '../../../../components/common/import/import-report-types';

// A report that has nothing to do with markings: other fields, other reason codes.
const report: ImportReport = {
  import_created: 1,
  import_skipped: 2,
  import_ignored: 1,
  import_columns: ['name', 'url'],
  import_rows: [
    {
      import_row_position: 1,
      import_row_status: 'CREATED',
      import_row_values: {
        name: 'ok',
        url: 'https://a.test',
      },
    },
    {
      import_row_position: 2,
      import_row_status: 'SKIPPED',
      import_row_reason: 'BAD_URL',
      import_row_message: 'Not a URL.',
      import_row_values: {
        name: 'broken',
        url: 'nope',
      },
    },
    {
      import_row_position: 3,
      import_row_status: 'IGNORED',
      import_row_values: {
        name: 'same',
        url: 'https://b.test',
      },
    },
    {
      import_row_position: 4,
      import_row_status: 'SKIPPED',
      import_row_reason: 'MISSING',
      import_row_message: 'Missing url.',
      import_row_values: {
        name: '=SUM(A1)',
        url: '',
      },
    },
  ],
};

describe('skippedRowsOf', () => {
  it('keeps only the skipped rows, not the created or ignored ones', () => {
    // Act
    const rows = skippedRowsOf(report);

    // Assert
    expect(rows.map(row => row.import_row_position)).toEqual([2, 4]);
  });
});

describe('skippedRowsToJson', () => {
  it('writes a JSON array of the values of the skipped rows only, with the report fields as keys', () => {
    // Act
    const json = JSON.parse(skippedRowsToJson(report));

    // Assert
    expect(json).toEqual([
      {
        name: 'broken',
        url: 'nope',
      },
      {
        name: '=SUM(A1)',
        url: '',
      },
    ]);
  });

  it('keeps a value as the user wrote it, and leaves a missing value as an empty string', () => {
    // Act
    const [, second] = JSON.parse(skippedRowsToJson(report));

    // Assert
    expect(second.name).toBe('=SUM(A1)');
    expect(second.url).toBe('');
  });

  it('follows the order of the report fields, even when a row lists them differently', () => {
    // Arrange
    const reordered: ImportReport = {
      ...report,
      import_columns: ['url', 'name'],
    };

    // Act
    const [first] = JSON.parse(skippedRowsToJson(reordered));

    // Assert
    expect(Object.keys(first)).toEqual(['url', 'name']);
  });
});
