import { type CatalogConnectorOutput } from '../../../../utils/api-types';

export type FacetGroupId = 'types' | 'useCases' | 'status' | 'deployment';

export interface CatalogFacetFilters {
  types: string[];
  useCases: string[];
  status: string[];
  deployment: string[];
}

export type CatalogSort = 'name_asc' | 'name_desc' | 'deployed_asc' | 'deployed_desc';

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
  /** Number of deployed instances (used by the deployed-count chip and sorting). */
  deployedCount: number;
  logoSrc?: string;
  version?: string;
  lastVerifiedAt?: string;
  /** Last contact of a deployed connector, as shown. */
  lastSeen?: string;
  /** Card link target; a card without a detail page is not clickable. */
  detailUrl?: string;
}

export const fromCatalogConnector = (connector: CatalogConnectorOutput): ConnectorItem => ({
  id: connector.catalog_connector_id,
  title: connector.catalog_connector_title,
  // Prefer the full description: the card body has room for several lines and
  // the short description is a thin one-liner meant for compact contexts.
  description: connector.catalog_connector_description
    || connector.catalog_connector_short_description,
  type: connector.catalog_connector_type,
  useCases: connector.catalog_connector_use_cases ?? [],
  verified: connector.catalog_connector_verified === true,
  external: connector.catalog_connector_manager_supported === true,
  deployedCount: connector.instance_deployed_count ?? 0,
  logoSrc: connector.catalog_connector_logo_url
    ? `/api/images/catalog/connectors/logos/${connector.catalog_connector_logo_url}`
    : undefined,
  version: connector.catalog_connector_container_version,
  lastVerifiedAt: connector.catalog_connector_last_verified_date,
  detailUrl: `/admin/integrations/catalog/${connector.catalog_connector_id}`,
});

export const CONNECTOR_TYPE_ORDER: ConnectorItemType[] = ['COLLECTOR', 'INJECTOR', 'EXECUTOR', 'SECRETS_PROVIDER'];

// The `verified` boolean now carries support semantics (same as OpenCTI):
// true = supported by Filigran, false = supported by the community.
export const STATUS_FILIGRAN = 'filigran';
export const STATUS_COMMUNITY = 'community';
export const DEPLOYMENT_EXTERNAL = 'external';
export const DEPLOYMENT_BUILT_IN = 'built_in';

export const hasActiveFacetFilters = (filters: CatalogFacetFilters): boolean => {
  return Object.values(filters).some(values => values.length > 0);
};

// Sentence case, like OpenCTI's marketplace chips ("Open source threat intel").
export const prettifyUseCase = (useCase: string): string => {
  const lowered = useCase.replace(/[_-]+/g, ' ').toLowerCase();
  return lowered.charAt(0).toUpperCase() + lowered.slice(1);
};

const GROUP_MATCHERS: Record<FacetGroupId, (item: ConnectorItem, value: string) => boolean> = {
  types: (item, value) => item.type === value,
  useCases: (item, value) => item.useCases.includes(value),
  status: (item, value) => item.verified === (value === STATUS_FILIGRAN),
  deployment: (item, value) => item.external === (value === DEPLOYMENT_EXTERNAL),
};

const matchesGroup = (item: ConnectorItem, groupId: FacetGroupId, selectedValues: string[]): boolean => selectedValues.length === 0
  || selectedValues.some(value => GROUP_MATCHERS[groupId](item, value));

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

const byTitle = (a: ConnectorItem, b: ConnectorItem) => a.title.localeCompare(b.title);

const SORT_COMPARATORS: Record<CatalogSort, (a: ConnectorItem, b: ConnectorItem) => number> = {
  name_asc: byTitle,
  name_desc: (a, b) => byTitle(b, a),
  deployed_asc: (a, b) => a.deployedCount - b.deployedCount || byTitle(a, b),
  deployed_desc: (a, b) => b.deployedCount - a.deployedCount || byTitle(a, b),
};

export const sortConnectors = (items: ConnectorItem[], sort: CatalogSort): ConnectorItem[] => [...items]
  .sort(SORT_COMPARATORS[sort]);
