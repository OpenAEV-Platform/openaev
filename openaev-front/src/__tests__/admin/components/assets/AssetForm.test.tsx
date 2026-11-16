import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AssetForm from '../../../../admin/components/assets/AssetForm';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));
vi.mock('../../../../actions/assets/endpoint-actions', () => ({ resolveHostnameToIps: vi.fn() }));
vi.mock('../../../../utils/utils', () => ({ isFeatureEnabled: () => false }));
vi.mock('../../../../components/fields/SelectFieldController', () => ({ default: () => null }));
vi.mock('../../../../components/fields/TagFieldController', () => ({ default: () => null }));
vi.mock('../../../../components/fields/MarkingFieldController', () => ({ default: () => null }));
vi.mock('../../../../components/fields/PersonFieldController', () => ({ default: () => null }));
vi.mock('../../../../components/fields/AddressesFieldComponent', () => ({ default: () => null }));

describe('AssetForm', () => {
  afterEach(cleanup);

  it('given_aWebApplicationWithNoInternetFacingValue_should_keepItNullOnSave', async () => {
    // Arrange: null is "not set" (shown "-" on the detail), unlike the creation default true
    const onSubmit = vi.fn();
    render(
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <AssetForm
            category="WEB_APPLICATION"
            editing
            onSubmit={onSubmit}
            handleClose={vi.fn()}
            initialValues={{
              asset_name: 'site',
              asset_url: 'https://site.example',
              asset_internet_facing: null,
            }}
          />
        </TooltipProvider>
      </ThemeProvider>,
    );

    // Act: rename only
    fireEvent.change(screen.getByDisplayValue('site'), { target: { value: 'site renamed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Update' }));

    // Assert
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][0]).toMatchObject({
      asset_name: 'site renamed',
      asset_internet_facing: null,
    });
  });
});
