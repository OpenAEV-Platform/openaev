import qs from 'qs';
import { describe, expect, it } from 'vitest';

import { availableUrlIn, SCENARIOS_CATALOG_KEY, UPDATE_AVAILABLE } from '../../../../../admin/components/scenarios/catalog/catalogScenarioInstallation';

describe('availableUrlIn', () => {
  it('given an installation state should open the Available tab filtered on it, for the list that reads it', () => {
    // Act
    const url = new URL(availableUrlIn(UPDATE_AVAILABLE), 'http://localhost');

    // Assert
    expect(url.pathname).toBe('/admin/scenarios/available');
    const state = qs.parse(atob(url.searchParams.get('query') ?? '')) as {
      key: string;
      filterGroup: {
        filters: {
          key: string;
          values: string[];
        }[];
      };
    };
    expect(state.key).toBe(SCENARIOS_CATALOG_KEY);
    expect(state.filterGroup.filters).toEqual([expect.objectContaining({
      key: 'catalog_item_installation',
      values: [UPDATE_AVAILABLE],
    })]);
  });
});
