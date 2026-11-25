import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type ScenarioVersionOutput } from '../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({ fetchScenarioVersions: vi.fn() }));

vi.mock('../../../../../actions/scenarios/scenario-update-actions', () => ({ fetchScenarioVersions: mocks.fetchScenarioVersions }));

const { default: useInstalledVersionRevoked } = await import('../../../../../admin/components/scenarios/card/useInstalledVersionRevoked');

const installed = (status: ScenarioVersionOutput['scenario_version_status']): ScenarioVersionOutput => ({
  scenario_version: '1.0.0',
  scenario_version_current: true,
  scenario_version_latest: false,
  scenario_version_status: status,
});

describe('useInstalledVersionRevoked', () => {
  afterEach(() => vi.clearAllMocks());

  it.each([
    ['REVOKED', true],
    ['VERIFIED', false],
  ] as const)('given the installed version %s should say whether it is revoked', async (status, revoked) => {
    // Arrange
    mocks.fetchScenarioVersions.mockResolvedValue({
      scenario_simulations_count: 0,
      scenario_versions: [installed(status)],
    });

    // Act
    const { result } = renderHook(() => useInstalledVersionRevoked('scenario-1', true));

    // Assert
    await waitFor(() => expect(mocks.fetchScenarioVersions).toHaveBeenCalledWith('scenario-1'));
    await waitFor(() => expect(result.current).toBe(revoked));
  });

  it('given no update to offer should not read the versions', () => {
    // Act
    const { result } = renderHook(() => useInstalledVersionRevoked('scenario-1', false));

    // Assert
    expect(result.current).toBe(false);
    expect(mocks.fetchScenarioVersions).not.toHaveBeenCalled();
  });
});
