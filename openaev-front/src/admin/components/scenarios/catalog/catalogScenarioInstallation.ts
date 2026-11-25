import { searchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import { buildFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { type CatalogScenarioOutput, type SearchPaginationInput } from '../../../../utils/api-types';

export const CATALOG_SCENARIO_INSTALLATION = 'catalog_item_installation';
export const INSTALLED = 'INSTALLED';
export const NOT_INSTALLED = 'NOT_INSTALLED';
export const UPDATE_AVAILABLE = 'UPDATE_AVAILABLE';

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
