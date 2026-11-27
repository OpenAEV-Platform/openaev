import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ search: vi.fn() }));

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));
vi.mock('../../../../../admin/components/scenarios/catalog/catalogScenarioInstallation', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    searchCatalogScenariosIn: () => mocks.search,
  };
});

const { default: useScenarioUpdatesAvailable } = await import('../../../../../admin/components/scenarios/card/useScenarioUpdatesAvailable');

describe('useScenarioUpdatesAvailable', () => {
  afterEach(() => vi.clearAllMocks());

  it('given an update for a chained scenario should leave it out, a chained one cannot be updated yet', async () => {
    // Arrange
    mocks.search.mockResolvedValue({
      data: {
        content: [{
          catalog_scenario_installed_scenario_id: 'timeline',
          catalog_scenario_version: '1.2.0',
          catalog_scenario_type: 'TIME_BASED',
        }, {
          catalog_scenario_installed_scenario_id: 'chained',
          catalog_scenario_version: '2.0.0',
          catalog_scenario_type: 'CHAINED',
        }],
      },
    });

    // Act
    const { result } = renderHook(() => useScenarioUpdatesAvailable());

    // Assert
    await waitFor(() => expect(result.current.get('timeline')).toBe('1.2.0'));
    expect(result.current.has('chained')).toBe(false);
  });
});
