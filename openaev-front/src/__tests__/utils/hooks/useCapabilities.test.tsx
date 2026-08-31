import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { CapabilityOutput } from '../../../utils/api-types';
import { type CapabilityScope } from '../../../utils/permissions/types';

const mocks = vi.hoisted(() => ({ fetchCapabilities: vi.fn() }));

vi.mock('../../../actions/capabilities/capability-action', () => ({ fetchCapabilities: mocks.fetchCapabilities }));

const capability = (value: string): CapabilityOutput => ({
  capability_checkable: true,
  capability_children: [],
  capability_scopes: ['TENANT'],
  capability_value: value,
});

const importUseCapabilities = async () => {
  const module = await import('../../../utils/hooks/useCapabilities');
  return module.default;
};

describe('useCapabilities', () => {
  beforeEach(() => {
    vi.resetModules();
    mocks.fetchCapabilities.mockReset();
  });

  it('given_aBackendOrder_should_keepThatOrder', async () => {
    // Arrange
    const tree = ['BYPASS', 'DASHBOARDS', 'REPORTINGS', 'FINDINGS'].map(capability);
    mocks.fetchCapabilities.mockResolvedValue({ data: tree });
    const useCapabilities = await importUseCapabilities();

    // Act
    const { result } = renderHook(() => useCapabilities('TENANT'));

    // Assert
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.capabilities.map(c => c.capability_value)).toEqual([
      'BYPASS',
      'DASHBOARDS',
      'REPORTINGS',
      'FINDINGS',
    ]);
    expect(mocks.fetchCapabilities).toHaveBeenCalledTimes(1);
    expect(mocks.fetchCapabilities).toHaveBeenCalledWith('TENANT');
  });

  it('given_aScopeChange_should_refetchForTheNewScope', async () => {
    // Arrange
    mocks.fetchCapabilities.mockImplementation((scope: CapabilityScope) => Promise.resolve({ data: [capability(scope)] }));
    const useCapabilities = await importUseCapabilities();

    // Act
    const { result, rerender } = renderHook(
      ({ scope }: { scope: CapabilityScope }) => useCapabilities(scope),
      { initialProps: { scope: 'TENANT' } },
    );
    await waitFor(() => expect(result.current.loading).toBe(false));
    rerender({ scope: 'PLATFORM' });

    // Assert
    await waitFor(() => expect(result.current.capabilities.map(c => c.capability_value)).toEqual(['PLATFORM']));
    expect(mocks.fetchCapabilities).toHaveBeenCalledTimes(2);
  });

  it('given_aRejectedRequest_should_stopLoadingWithoutCapabilities', async () => {
    // Arrange
    mocks.fetchCapabilities.mockRejectedValue(new Error('boom'));
    const useCapabilities = await importUseCapabilities();

    // Act
    const { result } = renderHook(() => useCapabilities('PLATFORM'));

    // Assert
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.capabilities).toEqual([]);
  });
});
