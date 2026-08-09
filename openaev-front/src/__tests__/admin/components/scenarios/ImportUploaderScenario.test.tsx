import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

const dispatchMock = vi.fn();
const notifyPartialImportMock = vi.fn();
const refreshMock = vi.fn();

vi.mock('../../../../utils/hooks', () => ({ useAppDispatch: () => dispatchMock }));
vi.mock('../../../../actions/scenarios/scenario-actions', () => ({ importScenario: vi.fn(() => ({ type: 'IMPORT_SCENARIO' })) }));
vi.mock('../../../../utils/importResultNotifier', () => ({ notifyPartialImport: (...args: unknown[]) => notifyPartialImportMock(...args) }));

// Imported after the mocks so the component picks them up.
const { default: ImportUploaderScenario } = await import('../../../../admin/components/scenarios/ImportUploaderScenario');

const theme = createTheme();

const renderComponent = () =>
  render(
    <ThemeProvider theme={theme}>
      <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
        <ImportUploaderScenario refresh={refreshMock} />
      </IntlProvider>
    </ThemeProvider>,
  );

const uploadFile = () => {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement;
  const file = new File(['{}'], 'scenario.zip', { type: 'application/zip' });
  fireEvent.change(input, { target: { files: [file] } });
};

describe('ImportUploaderScenario', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('refreshes the list after a fully successful import (no missing action)', async () => {
    dispatchMock.mockResolvedValue({ missingActions: [] });
    notifyPartialImportMock.mockReturnValue(false);

    renderComponent();
    uploadFile();

    await waitFor(() => expect(notifyPartialImportMock).toHaveBeenCalledTimes(1));
    expect(refreshMock).toHaveBeenCalledTimes(1);
  });

  it('refreshes the list on a partial import without a full page reload, so the sticky toast stays visible', async () => {
    dispatchMock.mockResolvedValue({
      missingActions: [{
        type: 'Injector',
        name: 'Step A',
      }],
    });
    notifyPartialImportMock.mockReturnValue(true);

    renderComponent();
    uploadFile();

    await waitFor(() => expect(notifyPartialImportMock).toHaveBeenCalledTimes(1));
    expect(refreshMock).toHaveBeenCalledTimes(1);
  });

  it('neither notifies nor refreshes when the call returns a form error', async () => {
    dispatchMock.mockResolvedValue({ 'FINAL_FORM/form-error': 'boom' });

    renderComponent();
    uploadFile();

    await waitFor(() => expect(dispatchMock).toHaveBeenCalledTimes(1));
    expect(notifyPartialImportMock).not.toHaveBeenCalled();
    expect(refreshMock).not.toHaveBeenCalled();
  });
});
