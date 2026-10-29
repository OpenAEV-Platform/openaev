import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { InjectContext, type InjectContextType } from '../../../../../admin/components/common/Context';
import { type ImportTestSummary } from '../../../../../utils/api-types';

vi.mock('../../../../../actions/mapper/mapper-actions', () => ({
  searchMappers: vi.fn(() => Promise.resolve({
    data: {
      content: [{
        import_mapper_id: 'mapper-1',
        import_mapper_name: 'Email mapper',
      }],
      totalPages: 1,
    },
  })),
}));

// Imported after the mocks so the component picks them up.
const { default: ImportUploaderInjectFromXlsInjects } = await import('../../../../../admin/components/common/injects/ImportUploaderInjectFromXlsInjects');

const theme = createTheme();

const renderComponent = (dryRunResult: ImportTestSummary) => {
  const onDryImportInjectFromXls = vi.fn(() => Promise.resolve(dryRunResult));
  const context = { onDryImportInjectFromXls } as unknown as InjectContextType;
  render(
    <ThemeProvider theme={theme}>
      <TooltipProvider>
        <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
          <InjectContext.Provider value={context}>
            <ImportUploaderInjectFromXlsInjects
              sheets={['Sheet1']}
              importId="import-1"
              handleClose={vi.fn()}
              handleSubmit={vi.fn()}
            />
          </InjectContext.Provider>
        </IntlProvider>
      </TooltipProvider>
    </ThemeProvider>,
  );
  return onDryImportInjectFromXls;
};

const pickFirstOption = async (combobox: HTMLElement) => {
  fireEvent.keyDown(combobox, { key: 'ArrowDown' });
  await screen.findByRole('listbox');
  fireEvent.click(screen.getAllByRole('option')[0]);
  await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull());
};

// Selecting the sheet and the mapper triggers the dry-run import.
const selectSheetAndMapper = async () => {
  const [sheetInput, mapperInput] = screen.getAllByRole('combobox');
  await pickFirstOption(sheetInput);
  await pickFirstOption(mapperInput);
};

describe('ImportUploaderInjectFromXlsInjects', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('shows the ready count without an errors header when the dry run reports no message', async () => {
    const dryRun = renderComponent({
      import_message: [],
      total_injects: 1,
      total_rows_analysed: 1,
    });

    await selectSheetAndMapper();

    await waitFor(() => expect(dryRun).toHaveBeenCalled());
    expect(await screen.findByText(/1 \/ 1 injects are ready to import/)).toBeTruthy();
    expect(screen.queryByText('ERRORS DETECTED:')).toBeNull();
  });

  it('shows the errors header with the reported rows when the dry run returns messages', async () => {
    renderComponent({
      import_message: [{
        message_code: 'NO_POTENTIAL_MATCH_FOUND',
        message_level: 'INFO',
        message_params: {
          column_type_num: 'A',
          row_num: '3',
        },
      }],
      total_injects: 1,
      total_rows_analysed: 2,
    });

    await selectSheetAndMapper();

    expect(await screen.findByText(/1 \/ 2 injects are ready to import/)).toBeTruthy();
    expect(screen.getByText('ERRORS DETECTED:')).toBeTruthy();
    expect(screen.getByText(/NO_POTENTIAL_MATCH_FOUND/).textContent).toContain('ON ROW: 3');
  });
});
