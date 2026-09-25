import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ fetchPrimitiveTypeDescriptors: vi.fn() }));

vi.mock('../../../../../actions/payloads/primitive-type-actions', () => ({ fetchPrimitiveTypeDescriptors: mocks.fetchPrimitiveTypeDescriptors }));

const descriptors = (...types: string[]) => types.map(type => ({
  primitive_type: type,
  capabilities: { supported_operators: [] },
}));

const importUseArgumentTypes = async () => {
  const module = await import('../../../../../admin/components/threat_arsenal/form/useArgumentTypes');
  return module.default;
};

describe('useArgumentTypes', () => {
  beforeEach(() => {
    vi.resetModules();
    mocks.fetchPrimitiveTypeDescriptors.mockReset();
  });

  it('given_multipleConsumers_should_fetchArgumentTypesOnce', async () => {
    // Arrange
    mocks.fetchPrimitiveTypeDescriptors.mockResolvedValue(descriptors('text', 'targeted-asset', 'asset'));
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
    expect(mocks.fetchPrimitiveTypeDescriptors).toHaveBeenCalledTimes(1);
    expect(laterHook.result.current.argumentTypes).toEqual(['asset', 'targeted-asset', 'text']);
    expect(laterHook.result.current.argumentWithDefaultValueTypes).toEqual(new Set(['asset', 'text']));
  });

  it('given_aRejectedRequest_should_retryForTheNextConsumer', async () => {
    // Arrange
    const error = new Error('Unable to load argument types');
    mocks.fetchPrimitiveTypeDescriptors
      .mockRejectedValueOnce(error)
      .mockResolvedValueOnce(descriptors('text'));
    const useArgumentTypes = await importUseArgumentTypes();

    // Act
    const failedHook = renderHook(() => useArgumentTypes());
    await waitFor(() => expect(failedHook.result.current.isLoading).toBe(false));
    failedHook.unmount();

    const retryHook = renderHook(() => useArgumentTypes());
    await waitFor(() => expect(retryHook.result.current.isLoading).toBe(false));

    // Assert
    expect(mocks.fetchPrimitiveTypeDescriptors).toHaveBeenCalledTimes(2);
    expect(retryHook.result.current.argumentTypes).toEqual(['text']);
    expect(retryHook.result.current.error).toBeNull();
  });
});
