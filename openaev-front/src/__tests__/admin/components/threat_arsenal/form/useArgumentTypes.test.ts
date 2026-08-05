import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ fetchArgumentTypes: vi.fn() }));

vi.mock('../../../../../actions/payloads/payload-argument-actions', () => ({ default: mocks.fetchArgumentTypes }));

const importUseArgumentTypes = async () => {
  const module = await import('../../../../../admin/components/threat_arsenal/form/useArgumentTypes');
  return module.default;
};

describe('useArgumentTypes', () => {
  beforeEach(() => {
    vi.resetModules();
    mocks.fetchArgumentTypes.mockReset();
  });

  it('given_multipleConsumers_should_fetchArgumentTypesOnce', async () => {
    // Arrange
    mocks.fetchArgumentTypes.mockResolvedValue(['text', 'targeted-asset', 'asset']);
    const useArgumentTypes = await importUseArgumentTypes();

    // Act
    const hooks = renderHook(() => ({
      first: useArgumentTypes(),
      second: useArgumentTypes(),
    }));

    await waitFor(() => {
      expect(hooks.result.current.first.isLoading).toBe(false);
      expect(hooks.result.current.second.isLoading).toBe(false);
    });
    hooks.unmount();

    const laterHook = renderHook(() => useArgumentTypes());
    await waitFor(() => expect(laterHook.result.current.isLoading).toBe(false));

    // Assert
    expect(mocks.fetchArgumentTypes).toHaveBeenCalledTimes(1);
    expect(laterHook.result.current.argumentTypes).toEqual(['asset', 'targeted-asset', 'text']);
    expect(laterHook.result.current.argumentWithDefaultValueTypes).toEqual(new Set(['asset', 'text']));
  });

  it('given_aRejectedRequest_should_retryForTheNextConsumer', async () => {
    // Arrange
    const error = new Error('Unable to load argument types');
    mocks.fetchArgumentTypes
      .mockRejectedValueOnce(error)
      .mockResolvedValueOnce(['text']);
    const useArgumentTypes = await importUseArgumentTypes();

    // Act
    const failedHook = renderHook(() => useArgumentTypes());
    await waitFor(() => expect(failedHook.result.current.isLoading).toBe(false));
    failedHook.unmount();

    const retryHook = renderHook(() => useArgumentTypes());
    await waitFor(() => expect(retryHook.result.current.isLoading).toBe(false));

    // Assert
    expect(mocks.fetchArgumentTypes).toHaveBeenCalledTimes(2);
    expect(retryHook.result.current.argumentTypes).toEqual(['text']);
    expect(retryHook.result.current.error).toBeNull();
  });
});
