import createFacetFilter from '../../../../components/common/marketplace/filter/facetFilter';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from '../scenario/ScenarioType';

export const catalogScenarioType = (scenario: CatalogScenarioOutput): ScenarioTypeValue => (
  scenario.catalog_scenario_type === 'CHAINED' ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED
);

export const catalogScenarioFilter = createFacetFilter<CatalogScenarioOutput, 'types' | 'platforms' | 'categories'>(
  {
    types: scenario => [catalogScenarioType(scenario)],
    platforms: scenario => scenario.catalog_scenario_platforms,
    categories: scenario => (scenario.catalog_scenario_category ? [scenario.catalog_scenario_category] : []),
  },
  scenario => `${scenario.catalog_scenario_name ?? ''} ${scenario.catalog_scenario_description ?? ''}`,
);

export const catalogScenarioSorts = {
  catalog_scenario_name: (scenario: CatalogScenarioOutput) => scenario.catalog_scenario_name ?? '',
  catalog_scenario_published_at: (scenario: CatalogScenarioOutput) => scenario.catalog_scenario_published_at ?? '',
};
