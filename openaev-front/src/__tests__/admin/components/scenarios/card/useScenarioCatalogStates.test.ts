import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ fetchScenarioCatalogStates: vi.fn() }));

vi.mock('../../../../../actions/scenarios/scenario-update-actions', () => ({ fetchScenarioCatalogStates: mocks.fetchScenarioCatalogStates }));
vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

const { default: useScenarioCatalogStates } = await import('../../../../../admin/components/scenarios/card/useScenarioCatalogStates');

describe('useScenarioCatalogStates', () => {
  afterEach(() => vi.clearAllMocks());

  it('given listed scenarios should read them in one call', async () => {
    // Arrange
    const state = { scenario_catalog_state_revoked: true };
    mocks.fetchScenarioCatalogStates.mockResolvedValue({ installed: state });

    // Act
    const { result } = renderHook(() => useScenarioCatalogStates(['installed']));

    // Assert
    await waitFor(() => expect(result.current).toEqual({ installed: state }));
    expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledTimes(1);
    expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledWith(['installed']);
  });

  it('given more scenarios listed should read only the new ones', async () => {
    // Arrange
    mocks.fetchScenarioCatalogStates.mockResolvedValue({});
    const { rerender } = renderHook(scenarios => useScenarioCatalogStates(scenarios), { initialProps: ['first'] });
    await waitFor(() => expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledWith(['first']));

    // Act
    rerender(['first', 'second']);

    // Assert
    await waitFor(() => expect(mocks.fetchScenarioCatalogStates).toHaveBeenLastCalledWith(['second']));
    expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledTimes(2);
  });

  it('given no scenario should not call', () => {
    // Act
    renderHook(() => useScenarioCatalogStates([]));

    // Assert
    expect(mocks.fetchScenarioCatalogStates).not.toHaveBeenCalled();
  });
});
