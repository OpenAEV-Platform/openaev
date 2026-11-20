import { describe, expect, it } from 'vitest';

import { skippedRowsOf, skippedRowsToCsv } from '../../../../components/common/import/import-report-helpers';
import { type ImportReport } from '../../../../utils/api-types';

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
      import_row_params: {},
      import_row_values: {
        name: 'ok',
        url: 'https://a.test',
      },
    },
    {
      import_row_position: 2,
      import_row_status: 'SKIPPED',
      import_row_reason: 'BAD_URL',
      import_row_params: {},
      import_row_values: {
        name: 'broken',
        url: 'nope',
      },
    },
    {
      import_row_position: 3,
      import_row_status: 'IGNORED',
      import_row_params: {},
      import_row_values: {
        name: 'same',
        url: 'https://b.test',
      },
    },
    {
      import_row_position: 4,
      import_row_status: 'SKIPPED',
      import_row_reason: 'MISSING',
      import_row_params: {},
      import_row_values: {
        name: '=SUM(A1)',
        url: '',
      },
    },
  ],
};

const withRow = (values: Record<string, string>): ImportReport => ({
  ...report,
  import_rows: [{
    import_row_position: 1,
    import_row_status: 'SKIPPED',
    import_row_params: {},
    import_row_values: values,
  }],
});

describe('skippedRowsOf', () => {
  it('keeps only the skipped rows, not the created or ignored ones', () => {
    // Act
    const rows = skippedRowsOf(report);

    // Assert
    expect(rows.map(row => row.import_row_position)).toEqual([2, 4]);
  });
});

describe('skippedRowsToCsv', () => {
  it('writes the report fields as the header, then one line per skipped row only', () => {
    // Act
    const lines = skippedRowsToCsv(report).split('\r\n');

    // Assert
    expect(lines).toEqual([
      'name,url',
      'broken,nope',
      '\'=SUM(A1),',
      '',
    ]);
  });

  it('follows the order of the report fields, even when a row lists them differently', () => {
    // Arrange
    const reordered: ImportReport = {
      ...report,
      import_columns: ['url', 'name'],
    };

    // Act
    const [header, first] = skippedRowsToCsv(reordered).split('\r\n');

    // Assert
    expect(header).toBe('url,name');
    expect(first).toBe('nope,broken');
  });

  it('leaves a missing value as an empty cell', () => {
    // Act
    const [, line] = skippedRowsToCsv(withRow({ name: 'only-name' })).split('\r\n');

    // Assert
    expect(line).toBe('only-name,');
  });

  it('quotes a value holding the separator, a quote or a line break', () => {
    // Act
    const csv = skippedRowsToCsv(withRow({
      name: 'a,b',
      url: 'say "hi"\nnow',
    }));

    // Assert
    expect(csv).toContain('"a,b","say ""hi""\nnow"');
  });

  it.each(['=', '+', '-', '@', '\t', '\r'])('neutralises a value starting with %j against spreadsheet formulas', (start) => {
    // Act
    const csv = skippedRowsToCsv(withRow({
      name: `${start}x`,
      url: 'safe',
    }));

    // Assert
    expect(csv).toContain(`'${start}x`);
  });

  it('puts one more quote in front of a value that already starts with quotes before a formula start', () => {
    // Act
    const csv = skippedRowsToCsv(withRow({
      name: '\'=x',
      url: '\'TLP',
    }));

    // Assert
    expect(csv).toContain('\'\'=x,\'TLP');
  });

  it('does not touch a value that cannot be read as a formula', () => {
    // Act
    const csv = skippedRowsToCsv(withRow({
      name: 'TLP:AMBER',
      url: '#ffc107',
    }));

    // Assert
    expect(csv).toContain('TLP:AMBER,#ffc107');
  });
});
