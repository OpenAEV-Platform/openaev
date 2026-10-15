import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type CatalogInstallInput,
  type CatalogInstallOutput,
  type CatalogInstallPreviewOutput,
  type CatalogItemOutput,
  type CatalogScenarioVersionsOutput,
  type ScenarioTargetsOutput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { facetsPreview, type PreviewCatalog, searchPreview } from './catalog-preview-search';

const CATALOG_URI = '/api/catalog';

export const SCENARIO_SEVERITY_FILTER = 'catalog_item_severity';
export const SCENARIO_CATEGORY_FILTER = 'catalog_item_category';
export const SCENARIO_TYPE_FILTER = 'catalog_item_scenario_type';
export const SCENARIO_PLATFORM_FILTER = 'catalog_item_platforms';
export const SCENARIO_AUTHOR_FILTER = 'catalog_item_author';

export const fetchCatalogScenarios = (): Promise<CatalogItemOutput[]> => simpleCall(`${CATALOG_URI}/scenarios`)
  .then(({ data }: { data: CatalogItemOutput[] }) => data);

// The tenant catalogue is small: it is searched and faceted in memory.
const scenarioCatalog = (items: CatalogItemOutput[]): PreviewCatalog<CatalogItemOutput> => ({
  items,
  text: item => `${item.catalog_item_name ?? ''} ${item.catalog_item_description ?? ''}`,
  valuesOf: (item, key) => {
    switch (key) {
      case SCENARIO_SEVERITY_FILTER: return item.catalog_item_severity ? [item.catalog_item_severity] : [];
      case SCENARIO_CATEGORY_FILTER: return item.catalog_item_category ? [item.catalog_item_category] : [];
      case SCENARIO_TYPE_FILTER: return item.catalog_item_scenario_type ? [item.catalog_item_scenario_type] : [];
      case SCENARIO_PLATFORM_FILTER: return item.catalog_item_platforms ?? [];
      case SCENARIO_AUTHOR_FILTER: return item.catalog_item_author ? [item.catalog_item_author] : [];
      default: return [];
    }
  },
  facetKeys: [SCENARIO_SEVERITY_FILTER, SCENARIO_CATEGORY_FILTER, SCENARIO_TYPE_FILTER, SCENARIO_PLATFORM_FILTER, SCENARIO_AUTHOR_FILTER],
});

export const searchCatalogScenarios = (input: SearchPaginationInput) => fetchCatalogScenarios()
  .then(items => searchPreview(scenarioCatalog(items), input));

export const fetchCatalogScenarioFacets = (input: SearchPaginationInput) => fetchCatalogScenarios()
  .then(items => facetsPreview(scenarioCatalog(items), input));

export const fetchCatalogScenarioInstallPreview = (externalId: string, version: string): Promise<{ data: CatalogInstallPreviewOutput }> => {
  return simpleCall(`${CATALOG_URI}/scenarios/${encodeURIComponent(externalId)}/versions/${encodeURIComponent(version)}/install-preview`);
};

export const installCatalogScenario = (externalId: string, version: string, input: CatalogInstallInput): Promise<{ data: CatalogInstallOutput }> => {
  return simplePostCall(`${CATALOG_URI}/scenarios/${encodeURIComponent(externalId)}/versions/${encodeURIComponent(version)}/install`, input);
};

export const uninstallCatalogScenario = (scenarioId: string) => {
  return simpleDelCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}`);
};

export const fetchScenarioTargets = (scenarioId: string): Promise<{ data: ScenarioTargetsOutput }> => {
  return simpleCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/targets`);
};

export const completeScenarioTargets = (scenarioId: string, input: CatalogInstallInput): Promise<{ data: ScenarioTargetsOutput }> => {
  return simplePutCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/targets`, input, undefined, true, false);
};

export const fetchCatalogScenarioVersions = (scenarioId: string): Promise<{ data: CatalogScenarioVersionsOutput }> => {
  return simpleCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/versions`);
};
