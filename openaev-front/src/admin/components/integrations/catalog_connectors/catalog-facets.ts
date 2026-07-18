import { type CatalogConnectorOutput } from '../../../../utils/api-types';

export type FacetGroupId = 'types' | 'useCases' | 'status' | 'deployment';

export interface CatalogFacetFilters {
  types: string[];
  useCases: string[];
  status: string[];
  deployment: string[];
}

export type CatalogSort = 'name_asc' | 'name_desc' | 'deployed_desc';

export const EMPTY_FACET_FILTERS: CatalogFacetFilters = {
  types: [],
  useCases: [],
  status: [],
  deployment: [],
};

export const CONNECTOR_TYPE_ORDER: CatalogConnectorOutput['catalog_connector_type'][] = ['COLLECTOR', 'INJECTOR', 'EXECUTOR'];

export const STATUS_VERIFIED = 'verified';
export const STATUS_DEPLOYED = 'deployed';
export const DEPLOYMENT_EXTERNAL = 'external';
export const DEPLOYMENT_BUILT_IN = 'built_in';

export const hasActiveFacetFilters = (filters: CatalogFacetFilters): boolean => {
  return Object.values(filters).some(values => values.length > 0);
};

export const prettifyUseCase = (useCase: string): string => {
  return useCase.replace(/[_-]+/g, ' ').toLowerCase();
};

const matchesGroup = (
  connector: CatalogConnectorOutput,
  groupId: FacetGroupId,
  selectedValues: string[],
): boolean => {
  if (selectedValues.length === 0) {
    return true;
  }
  switch (groupId) {
    case 'types':
      return selectedValues.includes(connector.catalog_connector_type);
    case 'useCases':
      return (connector.catalog_connector_use_cases ?? []).some(useCase => selectedValues.includes(useCase));
    case 'status':
      return selectedValues.some((value) => {
        if (value === STATUS_VERIFIED) {
          return connector.catalog_connector_verified === true;
        }
        return (connector.instance_deployed_count ?? 0) > 0;
      });
    case 'deployment':
      return selectedValues.some((value) => {
        if (value === DEPLOYMENT_EXTERNAL) {
          return connector.catalog_connector_manager_supported === true;
        }
        return connector.catalog_connector_manager_supported !== true;
      });
    default:
      return true;
  }
};

const matchesSearch = (connector: CatalogConnectorOutput, keyword: string): boolean => {
  if (!keyword) {
    return true;
  }
  const lowered = keyword.toLowerCase();
  return (connector.catalog_connector_title ?? '').toLowerCase().includes(lowered)
    || (connector.catalog_connector_short_description ?? '').toLowerCase().includes(lowered);
};

/**
 * Filters connectors by search keyword and every facet group,
 * optionally ignoring one group (faceted-search count semantics: a group's
 * counts are computed against everything filtered EXCEPT itself).
 */
export const filterConnectors = (
  connectors: CatalogConnectorOutput[],
  filters: CatalogFacetFilters,
  keyword: string,
  excludedGroup?: FacetGroupId,
): CatalogConnectorOutput[] => {
  return connectors.filter((connector) => {
    if (!matchesSearch(connector, keyword)) {
      return false;
    }
    return (Object.keys(filters) as FacetGroupId[]).every((groupId) => {
      if (groupId === excludedGroup) {
        return true;
      }
      return matchesGroup(connector, groupId, filters[groupId]);
    });
  });
};

export const countByPredicate = (
  connectors: CatalogConnectorOutput[],
  predicate: (connector: CatalogConnectorOutput) => boolean,
): number => connectors.filter(predicate).length;

export const sortConnectors = (
  connectors: CatalogConnectorOutput[],
  sort: CatalogSort,
): CatalogConnectorOutput[] => {
  const sorted = [...connectors];
  switch (sort) {
    case 'name_desc':
      sorted.sort((a, b) => b.catalog_connector_title.localeCompare(a.catalog_connector_title));
      break;
    case 'deployed_desc':
      sorted.sort((a, b) => (b.instance_deployed_count ?? 0) - (a.instance_deployed_count ?? 0)
        || a.catalog_connector_title.localeCompare(b.catalog_connector_title));
      break;
    default:
      sorted.sort((a, b) => a.catalog_connector_title.localeCompare(b.catalog_connector_title));
      break;
  }
  return sorted;
};
