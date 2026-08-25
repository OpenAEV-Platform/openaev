import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { CapabilityOutput } from '../../../utils/api-types';

const mockDispatch = vi.fn();
const mockFetchCapabilities = vi.fn();

const capabilityA: CapabilityOutput = {
  capability_checkable: true,
  capability_children: [],
  capability_scopes: ['PLATFORM'],
  capability_value: 'CAPABILITY_A',
};

const capabilityB: CapabilityOutput = {
  capability_checkable: true,
  capability_children: [],
  capability_scopes: ['PLATFORM'],
  capability_value: 'CAPABILITY_B',
};

const helperMock = {
  getPlatformCapabilities: () => [capabilityB, capabilityA],
  getTenantCapabilities: () => [],
  getPlatformCapabilitiesMap: () => ({
    CAPABILITY_A: capabilityA,
    CAPABILITY_B: capabilityB,
  }),
  getTenantCapabilitiesMap: () => ({}),
};

vi.mock('../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));

vi.mock('../../../actions/capabilities/capability-action', () => ({ fetchCapabilities: (scope: 'PLATFORM' | 'TENANT') => mockFetchCapabilities(scope) }));

vi.mock('../../../store', () => ({ useHelper: (selector: (helper: typeof helperMock) => unknown) => selector(helperMock) }));

vi.mock('../../../utils/hooks/useDataLoader', async () => {
  const React = await import('react');
  return {
    default: (loader: () => void) => {
      React.useEffect(() => {
        loader();
      }, []);
    },
  };
});

describe('useCapabilities', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchCapabilities.mockReturnValue({ type: 'MOCK_FETCH_CAPABILITIES' });
    mockDispatch.mockResolvedValue({ result: ['CAPABILITY_A', 'CAPABILITY_B'] });
  });

  it('given_backendResultOrder_should_keepThatOrderInReturnedCapabilities', async () => {
    const { default: useCapabilities } = await import('../../../utils/hooks/useCapabilities');

    const { result } = renderHook(() => useCapabilities('PLATFORM'));

    await waitFor(() => {
      expect(mockDispatch).toHaveBeenCalledTimes(1);
      expect(result.current.capabilities.map(capability => capability.capability_value)).toEqual([
        'CAPABILITY_A',
        'CAPABILITY_B',
      ]);
    });

    expect(mockFetchCapabilities).toHaveBeenCalledWith('PLATFORM');
    expect(result.current.loading).toBe(false);
  });
});
