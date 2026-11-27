import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ImportReportDialog from '../../../../components/common/import/ImportReportDialog';
import { type ImportReport, type ImportReportRow } from '../../../../utils/api-types';

const { mockCsvLink } = vi.hoisted(() => ({ mockCsvLink: vi.fn() }));

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

// The file itself is react-csv's job: keep what the dialog hands it.
vi.mock('react-csv', () => ({
  CSVLink: (props: { children: ReactNode }) => {
    mockCsvLink(props);
    return <a>{props.children}</a>;
  },
}));

// A report that has nothing to do with markings: other columns.
const report: ImportReport = {
  import_created: 1,
  import_skipped: 1,
  import_ignored: 2,
  import_columns: ['name', 'url'],
  import_rows: [
    {
      import_row_position: 1,
      import_row_status: 'CREATED',
      import_row_params: {},
      import_row_values: {
        name: 'created-name',
        url: 'https://a.test',
      },
    },
    {
      import_row_position: 2,
      import_row_status: 'SKIPPED',
      import_row_reason: 'INVALID_VALUE',
      import_row_params: { column: 'url' },
      import_row_values: {
        name: 'broken-name',
        url: '<b>nope</b>',
      },
    },
    {
      import_row_position: 3,
      import_row_status: 'IGNORED',
      import_row_params: {},
      import_row_values: {
        name: 'ignored-name',
        url: 'https://b.test',
      },
    },
  ],
};

const renderDialog = (element: ReactElement) => render(<ThemeProvider theme={createTheme()}>{element}</ThemeProvider>);

const dialog = (props: Partial<Parameters<typeof ImportReportDialog>[0]> = {}) => (
  <ImportReportDialog
    open
    onClose={() => {}}
    title="Import things"
    report={report}
    downloadFileName="skipped.csv"
    {...props}
  />
);

describe('ImportReportDialog', () => {
  afterEach(() => {
    cleanup();
    mockCsvLink.mockClear();
  });

  it('shows the counts, with the ignored one only when there is one', () => {
    // Act
    renderDialog(dialog());

    // Assert
    expect(screen.getByText('1 imported')).toBeTruthy();
    expect(screen.getByText('1 skipped')).toBeTruthy();
    expect(screen.getByText('2 ignored (already identical)')).toBeTruthy();
  });

  it('lists the position, one column per report field and only the skipped rows', () => {
    // Act
    renderDialog(dialog({ columnLabels: { name: 'Display name' } }));

    // Assert
    expect(screen.getByRole('columnheader', { name: 'Position' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Display name' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'url' })).toBeTruthy();
    expect(screen.getByText('broken-name')).toBeTruthy();
    expect(screen.queryByText('created-name')).toBeNull();
    expect(screen.queryByText('ignored-name')).toBeNull();
  });

  it('renders the fields as plain text, never as HTML', () => {
    // Act
    renderDialog(dialog());

    // Assert
    expect(screen.getByText('<b>nope</b>')).toBeTruthy();
    expect(document.querySelector('b')).toBeNull();
  });

  it('labels the reason, and falls back to the raw code', () => {
    // Act
    const { rerender } = renderDialog(dialog({ reasonLabels: { INVALID_VALUE: 'Invalid URL' } }));

    // Assert
    expect(screen.getByText('Invalid URL')).toBeTruthy();
    rerender(<ThemeProvider theme={createTheme()}>{dialog()}</ThemeProvider>);
    expect(screen.getByText('INVALID_VALUE')).toBeTruthy();
  });

  it('writes the sentence of a skipped row with the function of the caller, from the row', () => {
    // Arrange
    const rowMessage = vi.fn((row: ImportReportRow) => `Fix the ${row.import_row_params.column} of row ${row.import_row_position}.`);

    // Act
    const { rerender } = renderDialog(dialog({ rowMessage }));

    // Assert
    expect(screen.getByText('Fix the url of row 2.')).toBeTruthy();
    rerender(<ThemeProvider theme={createTheme()}>{dialog()}</ThemeProvider>);
    expect(screen.queryByText(/of row 2\./)).toBeNull();
  });

  it('offers the download of the skipped rows only when there are some', () => {
    // Act
    const { rerender } = renderDialog(dialog());

    // Assert
    expect(screen.getByRole('button', { name: 'Download skipped rows' })).toBeTruthy();
    const withoutSkipped: ImportReport = {
      ...report,
      import_skipped: 0,
      import_rows: report.import_rows.filter(row => row.import_row_status !== 'SKIPPED'),
    };
    rerender(<ThemeProvider theme={createTheme()}>{dialog({ report: withoutSkipped })}</ThemeProvider>);
    expect(screen.queryByRole('button', { name: 'Download skipped rows' })).toBeNull();
  });

  it('shows the message of a file-level failure and no table', () => {
    // Act
    renderDialog(dialog({
      report: null,
      fileError: 'The file is empty.',
    }));

    // Assert
    expect(screen.getByText('The file was not imported. The file is empty.')).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('hands only the skipped rows, the report fields and the file name to the CSV link', () => {
    // Act
    renderDialog(dialog());

    // Assert
    expect(mockCsvLink).toHaveBeenCalledWith(expect.objectContaining({
      data: [['broken-name', '<b>nope</b>']],
      headers: ['name', 'url'],
      filename: 'skipped.csv',
    }));
  });
});
