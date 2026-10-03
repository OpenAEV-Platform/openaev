import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { searchIocValidationAssetGroupOptions } from '../../../../../actions/ioc_validations/ioc-validation-actions';
import { AssetGroupField } from '../../../../../admin/components/settings/ioc_validation/IocValidationSettings';

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
});
