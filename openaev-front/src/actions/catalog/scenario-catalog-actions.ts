import { type SearchPaginationInput } from '../../utils/api-types';
import { facetsPreview, type PreviewCatalog, searchPreview } from './catalog-preview-search';
import { CATALOG_SCENARIOS_PREVIEW, type CatalogScenario } from './scenario-catalog-preview';

// Preview until the catalogue API ships (RFC 0005: search and facets in PR 3b, install in PR 11).

export const SCENARIO_SEVERITY_FILTER = 'catalog_item_severity';
export const SCENARIO_CATEGORY_FILTER = 'catalog_item_category';
export const SCENARIO_TYPE_FILTER = 'catalog_item_type';
export const SCENARIO_PLATFORM_FILTER = 'catalog_item_platforms';
export const SCENARIO_AUTHOR_FILTER = 'catalog_item_author';

const SCENARIO_CATALOG: PreviewCatalog<CatalogScenario> = {
  items: CATALOG_SCENARIOS_PREVIEW,
  text: item => `${item.catalog_item_name} ${item.catalog_item_description}`,
  valuesOf: (item, key) => {
    switch (key) {
      case SCENARIO_SEVERITY_FILTER: return [item.catalog_item_severity];
      case SCENARIO_CATEGORY_FILTER: return [item.catalog_item_category];
      case SCENARIO_TYPE_FILTER: return [item.catalog_item_type];
      case SCENARIO_PLATFORM_FILTER: return item.catalog_item_platforms;
      case SCENARIO_AUTHOR_FILTER: return [item.catalog_item_author];
      default: return [];
    }
  },
  facetKeys: [SCENARIO_SEVERITY_FILTER, SCENARIO_CATEGORY_FILTER, SCENARIO_TYPE_FILTER, SCENARIO_PLATFORM_FILTER, SCENARIO_AUTHOR_FILTER],
};

export const searchCatalogScenarios = (input: SearchPaginationInput) => searchPreview(SCENARIO_CATALOG, input);

export const fetchCatalogScenarioFacets = (input: SearchPaginationInput) => facetsPreview(SCENARIO_CATALOG, input);

export const installCatalogScenario = (externalId: string): Promise<string[]> => Promise.resolve([externalId]);
