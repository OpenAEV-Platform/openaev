import qs from 'qs';

import { searchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import { CATALOG_ITEM_DEFAULT_SORT } from '../../../../components/common/marketplace/filter/catalogItemSearch';
import { buildFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { type CatalogScenarioOutput, type SearchPaginationInput } from '../../../../utils/api-types';

export const CATALOG_SCENARIO_INSTALLATION = 'catalog_item_installation';
export const INSTALLED = 'INSTALLED';
export const NOT_INSTALLED = 'NOT_INSTALLED';
export const UPDATE_AVAILABLE = 'UPDATE_AVAILABLE';

// The Available tab's list key: its filters are kept under it, and a link carrying them must name it.
export const SCENARIOS_CATALOG_KEY = 'scenarios-catalog';

const LABELS: Record<string, string> = {
  [INSTALLED]: 'Installed',
  [NOT_INSTALLED]: 'Not installed',
  [UPDATE_AVAILABLE]: 'Update available',
};

export const installationLabel = (installation: string) => LABELS[installation] ?? installation;

export const scenarioUrl = (scenarioId: string) => `/admin/scenarios/${scenarioId}`;

export const installedScenarioUrl = (scenario: CatalogScenarioOutput) => (scenario.catalog_scenario_installed_scenario_id
  ? scenarioUrl(scenario.catalog_scenario_installed_scenario_id)
  : undefined);

// The catalogue scenarios in one installation state only.
export const searchCatalogScenariosIn = (installation: string) => (input: SearchPaginationInput) => searchCatalogScenarios({
  ...input,
  filterGroup: {
    mode: 'and',
    filters: [buildFilter(CATALOG_SCENARIO_INSTALLATION, [installation], 'eq')],
  },
});

// The Available tab, opened on the catalogue scenarios in one installation state (the list's own link format).
export const availableUrlIn = (installation: string) => `/admin/scenarios/available?query=${btoa(qs.stringify({
  ...buildSearchPagination({
    sorts: [CATALOG_ITEM_DEFAULT_SORT],
    filterGroup: {
      mode: 'and',
      filters: [buildFilter(CATALOG_SCENARIO_INSTALLATION, [installation], 'eq')],
    },
  }),
  key: SCENARIOS_CATALOG_KEY,
}, { allowEmptyArrays: true }))}`;
