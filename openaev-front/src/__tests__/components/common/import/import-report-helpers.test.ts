import { describe, expect, it } from 'vitest';

import { skippedRowsOf, skippedRowsToCells } from '../../../../components/common/import/import-report-helpers';
import { type ImportReport } from '../../../../utils/api-types';

// A report that has nothing to do with markings: other fields.
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
      import_row_reason: 'INVALID_VALUE',
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
      import_row_reason: 'MISSING_FIELD',
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

describe('skippedRowsToCells', () => {
  const cellsOf = (target: ImportReport) => skippedRowsToCells(skippedRowsOf(target), target.import_columns);

  it('gives one line per skipped row only, with one cell per report field', () => {
    // Act
    const cells = cellsOf(report);

    // Assert
    expect(cells).toEqual([
      ['broken', 'nope'],
      ['\'=SUM(A1)', ''],
    ]);
  });

  it('follows the order of the report fields, even when a row lists them differently', () => {
    // Arrange
    const reordered: ImportReport = {
      ...report,
      import_columns: ['url', 'name'],
    };

    // Act
    const [first] = cellsOf(reordered);

    // Assert
    expect(first).toEqual(['nope', 'broken']);
  });

  it('leaves a missing value as an empty cell', () => {
    // Act
    const [line] = cellsOf(withRow({ name: 'only-name' }));

    // Assert
    expect(line).toEqual(['only-name', '']);
  });

  it('doubles a quote, which react-csv does not escape inside the quotes it puts round a cell', () => {
    // Act
    const [line] = cellsOf(withRow({
      name: 'say "hi"',
      url: 'a,b',
    }));

    // Assert
    expect(line).toEqual(['say ""hi""', 'a,b']);
  });

  it.each(['=', '+', '-', '@', '\t', '\r'])('neutralises a value starting with %j against spreadsheet formulas', (start) => {
    // Act
    const [line] = cellsOf(withRow({
      name: `${start}x`,
      url: 'safe',
    }));

    // Assert
    expect(line).toEqual([`'${start}x`, 'safe']);
  });

  it('puts one more quote in front of a value that already starts with quotes before a formula start', () => {
    // Act
    const [line] = cellsOf(withRow({
      name: '\'=x',
      url: '\'TLP',
    }));

    // Assert
    expect(line).toEqual(['\'\'=x', '\'TLP']);
  });

  it('does not touch a value that cannot be read as a formula', () => {
    // Act
    const [line] = cellsOf(withRow({
      name: 'TLP:AMBER',
      url: '#ffc107',
    }));

    // Assert
    expect(line).toEqual(['TLP:AMBER', '#ffc107']);
  });
});
