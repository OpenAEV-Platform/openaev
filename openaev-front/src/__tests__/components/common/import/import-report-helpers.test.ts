import { describe, expect, it } from 'vitest';

import { skippedRowsOf, skippedRowsToCsv } from '../../../../components/common/import/import-report-helpers';
import { type ImportReport } from '../../../../components/common/import/import-report-types';

// A report that has nothing to do with markings: other columns, other reason codes.
const report: ImportReport = {
  import_created: 1,
  import_skipped: 2,
  import_ignored: 1,
  import_columns: ['name', 'url'],
  import_rows: [
    {
      import_row_line: 2,
      import_row_status: 'CREATED',
      import_row_values: {
        name: 'ok',
        url: 'https://a.test',
      },
    },
    {
      import_row_line: 3,
      import_row_status: 'SKIPPED',
      import_row_reason: 'BAD_URL',
      import_row_message: 'Not a URL.',
      import_row_values: {
        name: 'broken',
        url: 'nope',
      },
    },
    {
      import_row_line: 4,
      import_row_status: 'IGNORED',
      import_row_values: {
        name: 'same',
        url: 'https://b.test',
      },
    },
    {
      import_row_line: 5,
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
    expect(rows.map(row => row.import_row_line)).toEqual([3, 5]);
  });
});

describe('skippedRowsToCsv', () => {
  it('writes the file columns as header and the values of the skipped rows only', () => {
    // Act
    const csv = skippedRowsToCsv(report);

    // Assert
    expect(csv.split('\n')[0]).toBe('name,url');
    expect(csv).toContain('broken,nope');
    expect(csv).not.toContain('ok,');
    expect(csv).not.toContain('same');
  });

  it('neutralises a formula-looking cell and leaves a missing value empty', () => {
    // Act
    const csv = skippedRowsToCsv(report);

    // Assert
    expect(csv.split('\n')[2]).toBe('\'=SUM(A1),');
  });
});
