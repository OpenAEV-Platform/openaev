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

export type ConnectorItemType = CatalogConnectorOutput['catalog_connector_type'];

/**
 * The view model shared by the Available (catalog) and Deployed tabs: both
 * render the same faceted marketplace (sidebar, toolbar, cards), so both data
 * sources are converted to this single shape.
 */
export interface ConnectorItem {
  id: string;
  title: string;
  description?: string;
  type: ConnectorItemType;
  useCases: string[];
  verified: boolean;
  /** External (managed / manually deployed) versus built-in. */
  external: boolean;
  /** Number of deployed instances (used by the Deployed facet and sorting). */
  deployedCount: number;
  logoSrc?: string;
  /** Card link target; a card without a detail page is not clickable. */
  detailUrl?: string;
}

export const fromCatalogConnector = (connector: CatalogConnectorOutput): ConnectorItem => ({
  id: connector.catalog_connector_id,
  title: connector.catalog_connector_title,
  description: connector.catalog_connector_short_description,
  type: connector.catalog_connector_type,
  useCases: connector.catalog_connector_use_cases ?? [],
  verified: connector.catalog_connector_verified === true,
  external: connector.catalog_connector_manager_supported === true,
  deployedCount: connector.instance_deployed_count ?? 0,
  logoSrc: connector.catalog_connector_logo_url
    ? `/api/images/catalog/connectors/logos/${connector.catalog_connector_logo_url}`
    : undefined,
  detailUrl: `/admin/integrations/catalog/${connector.catalog_connector_id}`,
});

export const CONNECTOR_TYPE_ORDER: ConnectorItemType[] = ['COLLECTOR', 'INJECTOR', 'EXECUTOR'];

// The `verified` boolean now carries support semantics (same as OpenCTI):
// true = supported by Filigran, false = supported by the community.
export const STATUS_FILIGRAN = 'filigran';
export const STATUS_COMMUNITY = 'community';
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
  item: ConnectorItem,
  groupId: FacetGroupId,
  selectedValues: string[],
): boolean => {
  if (selectedValues.length === 0) {
    return true;
  }
  switch (groupId) {
    case 'types':
      return selectedValues.includes(item.type);
    case 'useCases':
      return item.useCases.some(useCase => selectedValues.includes(useCase));
    case 'status':
      return selectedValues.some((value) => {
        if (value === STATUS_FILIGRAN) {
          return item.verified;
        }
        if (value === STATUS_COMMUNITY) {
          return !item.verified;
        }
        return item.deployedCount > 0;
      });
    case 'deployment':
      return selectedValues.some((value) => {
        if (value === DEPLOYMENT_EXTERNAL) {
          return item.external;
        }
        return !item.external;
      });
    default:
      return true;
  }
};

const matchesSearch = (item: ConnectorItem, keyword: string): boolean => {
  if (!keyword) {
    return true;
  }
  const lowered = keyword.toLowerCase();
  return item.title.toLowerCase().includes(lowered)
    || (item.description ?? '').toLowerCase().includes(lowered);
};

/**
 * Filters items by search keyword and every facet group,
 * optionally ignoring one group (faceted-search count semantics: a group's
 * counts are computed against everything filtered EXCEPT itself).
 */
export const filterConnectors = (
  items: ConnectorItem[],
  filters: CatalogFacetFilters,
  keyword: string,
  excludedGroup?: FacetGroupId,
): ConnectorItem[] => {
  return items.filter((item) => {
    if (!matchesSearch(item, keyword)) {
      return false;
    }
    return (Object.keys(filters) as FacetGroupId[]).every((groupId) => {
      if (groupId === excludedGroup) {
        return true;
      }
      return matchesGroup(item, groupId, filters[groupId]);
    });
  });
};

export const countByPredicate = (
  items: ConnectorItem[],
  predicate: (item: ConnectorItem) => boolean,
): number => items.filter(predicate).length;

export const sortConnectors = (
  items: ConnectorItem[],
  sort: CatalogSort,
): ConnectorItem[] => {
  const sorted = [...items];
  switch (sort) {
    case 'name_desc':
      sorted.sort((a, b) => b.title.localeCompare(a.title));
      break;
    case 'deployed_desc':
      sorted.sort((a, b) => b.deployedCount - a.deployedCount || a.title.localeCompare(b.title));
      break;
    default:
      sorted.sort((a, b) => a.title.localeCompare(b.title));
      break;
  }
  return sorted;
};
