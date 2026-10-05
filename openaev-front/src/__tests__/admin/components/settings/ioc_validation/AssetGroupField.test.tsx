import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchIocValidationSettings, searchIocValidationAssetGroupOptions } from '../../../../../actions/ioc_validations/ioc-validation-actions';
import IocValidationSettings, { AssetGroupField } from '../../../../../admin/components/settings/ioc_validation/IocValidationSettings';

vi.mock('../../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string) => value }),
  };
});

vi.mock('../../../../../actions/ioc_validations/ioc-validation-actions', () => ({
  fetchIocValidationSettings: vi.fn(),
  updateIocValidationSettings: vi.fn(),
  searchIocValidationAssetGroupOptions: vi.fn((searchText: string) => Promise.resolve({
    data: searchText
      ? [{
          id: 'group-lab',
          label: 'Lab endpoints',
        }]
      : [{
          id: 'group-configured',
          label: 'Configured group',
        }, {
          id: 'group-other',
          label: 'Other group',
        }],
  })),
}));

describe('AssetGroupField', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('shows the configured group and searches the other groups by name on the API', async () => {
    render(<AssetGroupField value="group-configured" onChange={vi.fn()} onBlur={vi.fn()} />);

    const input = await screen.findByTestId('ioc-validation-asset-group');
    await waitFor(() => expect((input as HTMLInputElement).value).toBe('Configured group'));
    expect(searchIocValidationAssetGroupOptions).toHaveBeenCalledWith('');

    fireEvent.change(input, { target: { value: 'lab' } });

    await waitFor(() => expect(searchIocValidationAssetGroupOptions).toHaveBeenCalledWith('lab'), { timeout: 2000 });
  });

  it('tells a failed lookup from an empty list and searches again on retry', async () => {
    vi.mocked(searchIocValidationAssetGroupOptions).mockRejectedValueOnce(new Error('unreachable'));
    render(<AssetGroupField value="group-configured" onChange={vi.fn()} onBlur={vi.fn()} />);

    const helper = await screen.findByTestId('ioc-validation-asset-group-helper');
    await waitFor(() => expect(helper.textContent).toContain('The asset groups could not be loaded.'));

    fireEvent.click(within(helper).getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(helper.textContent).toBe('Endpoints of this group run the benign tests.'));
    expect(searchIocValidationAssetGroupOptions).toHaveBeenCalledTimes(2);
    const input = screen.getByTestId('ioc-validation-asset-group') as HTMLInputElement;
    await waitFor(() => expect(input.value).toBe('Configured group'));
  });
});

describe('IocValidationSettings', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('offers a retry when the settings cannot be loaded', async () => {
    vi.mocked(fetchIocValidationSettings).mockRejectedValue(new Error('unreachable'));
    render(<IocValidationSettings />);

    const notice = await screen.findByTestId('ioc-validation-settings-load-failed');
    expect(notice.textContent).toContain('The IOC validation settings could not be loaded.');

    fireEvent.click(within(notice).getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(fetchIocValidationSettings).toHaveBeenCalledTimes(2));
    expect(await screen.findByTestId('ioc-validation-settings-load-failed')).toBeTruthy();
  });
});
