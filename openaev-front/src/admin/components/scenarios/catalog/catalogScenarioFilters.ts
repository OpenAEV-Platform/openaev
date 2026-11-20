import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from '../scenario/ScenarioType';

export type FacetKey = 'types' | 'platforms' | 'categories';

export type CatalogScenarioFilters = Record<FacetKey, string[]> & { keyword: string };

export type SortField = 'catalog_scenario_name' | 'catalog_scenario_published_at';

export interface CatalogScenarioSort {
  field: SortField;
  asc: boolean;
}

export const NO_FILTERS: CatalogScenarioFilters = {
  keyword: '',
  types: [],
  platforms: [],
  categories: [],
};

export const catalogScenarioType = (scenario: CatalogScenarioOutput): ScenarioTypeValue => (
  scenario.catalog_scenario_type === 'CHAINED' ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED
);

const FACET_VALUES: Record<FacetKey, (scenario: CatalogScenarioOutput) => string[]> = {
  types: scenario => [catalogScenarioType(scenario)],
  platforms: scenario => scenario.catalog_scenario_platforms,
  categories: scenario => (scenario.catalog_scenario_category ? [scenario.catalog_scenario_category] : []),
};

const FACET_KEYS = Object.keys(FACET_VALUES) as FacetKey[];

const matchesKeyword = (scenario: CatalogScenarioOutput, keyword: string) => `${scenario.catalog_scenario_name ?? ''} ${scenario.catalog_scenario_description ?? ''}`
  .toLowerCase()
  .includes(keyword.toLowerCase());

// Values are ORed within a facet, facets ANDed; a facet is ignored while its own counts are computed.
const matches = (scenario: CatalogScenarioOutput, filters: CatalogScenarioFilters, ignored?: FacetKey) => matchesKeyword(scenario, filters.keyword)
  && FACET_KEYS
    .filter(key => key !== ignored && filters[key].length > 0)
    .every(key => FACET_VALUES[key](scenario).some(value => filters[key].includes(value)));

export const hasFilters = (filters: CatalogScenarioFilters) => filters.keyword !== '' || FACET_KEYS.some(key => filters[key].length > 0);

export const facetCounts = (scenarios: CatalogScenarioOutput[], filters: CatalogScenarioFilters, key: FacetKey) => scenarios
  .filter(scenario => matches(scenario, filters, key))
  .flatMap(FACET_VALUES[key])
  .reduce<Record<string, number>>((counts, value) => ({
    ...counts,
    [value]: (counts[value] ?? 0) + 1,
  }), {});

export const facetValues = (scenarios: CatalogScenarioOutput[], key: FacetKey) => [...new Set(scenarios.flatMap(FACET_VALUES[key]))].sort();

export const filterScenarios = (scenarios: CatalogScenarioOutput[], filters: CatalogScenarioFilters, { field, asc }: CatalogScenarioSort) => scenarios
  .filter(scenario => matches(scenario, filters))
  .sort((a, b) => (a[field] ?? '').localeCompare(b[field] ?? '') * (asc ? 1 : -1));
