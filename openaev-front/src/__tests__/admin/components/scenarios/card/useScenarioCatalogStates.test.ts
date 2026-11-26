import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type ScenarioRow } from '../../../../../admin/components/scenarios/scenarioRow';

const mocks = vi.hoisted(() => ({ fetchScenarioCatalogStates: vi.fn() }));

vi.mock('../../../../../actions/scenarios/scenario-update-actions', () => ({ fetchScenarioCatalogStates: mocks.fetchScenarioCatalogStates }));
vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

const { default: useScenarioCatalogStates } = await import('../../../../../admin/components/scenarios/card/useScenarioCatalogStates');

const row = (scenarioId: string, version?: string) => ({
  scenario_id: scenarioId,
  scenario_version: version,
}) as ScenarioRow;

describe('useScenarioCatalogStates', () => {
  afterEach(() => vi.clearAllMocks());

  it('given listed scenarios should read the installed ones in one call', async () => {
    // Arrange
    const state = { scenario_catalog_state_revoked: true };
    mocks.fetchScenarioCatalogStates.mockResolvedValue({ installed: state });

    // Act
    const { result } = renderHook(() => useScenarioCatalogStates([row('installed', '1.0.0'), row('local')]));

    // Assert
    await waitFor(() => expect(result.current).toEqual({ installed: state }));
    expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledTimes(1);
    expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledWith(['installed']);
  });

  it('given more scenarios listed should read only the new ones', async () => {
    // Arrange
    mocks.fetchScenarioCatalogStates.mockResolvedValue({});
    const { rerender } = renderHook(scenarios => useScenarioCatalogStates(scenarios), { initialProps: [row('first', '1.0.0')] });
    await waitFor(() => expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledWith(['first']));

    // Act
    rerender([row('first', '1.0.0'), row('second', '1.0.0')]);

    // Assert
    await waitFor(() => expect(mocks.fetchScenarioCatalogStates).toHaveBeenLastCalledWith(['second']));
    expect(mocks.fetchScenarioCatalogStates).toHaveBeenCalledTimes(2);
  });

  it('given no installed scenario should not call', () => {
    // Act
    renderHook(() => useScenarioCatalogStates([row('local')]));

    // Assert
    expect(mocks.fetchScenarioCatalogStates).not.toHaveBeenCalled();
  });
});
