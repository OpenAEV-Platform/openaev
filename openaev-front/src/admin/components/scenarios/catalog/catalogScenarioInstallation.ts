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

// A link carrying the Available tab's filters must name its list key.
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

const installationFilterGroup = (installation: string) => ({
  mode: 'and' as const,
  filters: [buildFilter(CATALOG_SCENARIO_INSTALLATION, [installation], 'eq')],
});

export const searchCatalogScenariosIn = (installation: string) => (input: SearchPaginationInput) => searchCatalogScenarios({
  ...input,
  filterGroup: installationFilterGroup(installation),
});

// The Available tab filtered on one installation state, in the list's own link format.
export const availableUrlIn = (installation: string) => `/admin/scenarios/available?query=${btoa(qs.stringify({
  ...buildSearchPagination({
    sorts: [CATALOG_ITEM_DEFAULT_SORT],
    filterGroup: installationFilterGroup(installation),
  }),
  key: SCENARIOS_CATALOG_KEY,
}, { allowEmptyArrays: true }))}`;
