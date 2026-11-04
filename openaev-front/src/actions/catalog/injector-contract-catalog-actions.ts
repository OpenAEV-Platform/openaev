import { simpleCall } from '../../utils/Action';
import { type CatalogItemOutput, type SearchPaginationInput } from '../../utils/api-types';
import { catalogFilterSchema, type FacetCounts, facetsPreview, type PreviewCatalog, searchPreview } from './catalog-preview-search';
import { CATALOG_AUTHOR_FILTER, CATALOG_DOMAIN_FILTER, CATALOG_PLATFORM_FILTER, fetchAvailableCatalogPayloads } from './payload-catalog-actions';

// A contract of a catalogue injector that is not running yet: enabling the integration installs it.
export interface CatalogInjectorContract {
  contract_id: string;
  contract_label?: string;
  contract_domains?: string[];
  contract_platforms?: string[];
  catalog_connector_id: string;
  catalog_connector_slug?: string;
  catalog_connector_title?: string;
  catalog_connector_logo_url?: string;
  catalog_connector_manager_supported?: boolean;
}

export const fetchCatalogInjectorContracts = (): Promise<CatalogInjectorContract[]> => simpleCall('/api/catalog/injector-contracts')
  .then(({ data }: { data: CatalogInjectorContract[] }) => data);

const ACTION_NAME_FILTER = 'catalog_item_name';
const ACTION_PUBLISHED_FILTER = 'catalog_item_published_at';

// The Available tab mixes catalogue payloads and contracts of injectors to enable.
export interface AvailableAction {
  id: string;
  catalog_item_name: string;
  catalog_item_published_at?: string;
  payload?: CatalogItemOutput;
  contract?: CatalogInjectorContract;
}

export const fetchAvailableActions = (): Promise<AvailableAction[]> => Promise.all([
  fetchAvailableCatalogPayloads(),
  fetchCatalogInjectorContracts(),
]).then(([payloads, contracts]) => [
  ...payloads.map(payload => ({
    id: payload.catalog_item_external_id,
    catalog_item_name: payload.catalog_item_name ?? payload.catalog_item_external_id,
    catalog_item_published_at: payload.catalog_item_published_at,
    payload,
  })),
  ...contracts.map(contract => ({
    id: contract.contract_id,
    catalog_item_name: contract.contract_label ?? contract.contract_id,
    // No publication date: sorted after the dated payloads, newest first.
    catalog_item_published_at: '',
    contract,
  })),
]);

const availableCatalog = (items: AvailableAction[]): PreviewCatalog<AvailableAction> => ({
  items,
  text: item => (item.payload
    ? `${item.payload.catalog_item_name ?? ''} ${item.payload.catalog_item_description ?? ''}`
    : `${item.contract?.contract_label ?? ''} ${item.contract?.catalog_connector_title ?? ''}`),
  valuesOf: (item, key) => {
    const { payload, contract } = item;
    switch (key) {
      case CATALOG_DOMAIN_FILTER:
        if (payload) return payload.catalog_item_category ? [payload.catalog_item_category] : [];
        return contract?.contract_domains ?? [];
      case CATALOG_PLATFORM_FILTER:
        return payload ? payload.catalog_item_platforms ?? [] : contract?.contract_platforms ?? [];
      case ACTION_NAME_FILTER: return [item.catalog_item_name];
      case ACTION_PUBLISHED_FILTER: return item.catalog_item_published_at ? [item.catalog_item_published_at] : [];
      case CATALOG_AUTHOR_FILTER: {
        const author = payload ? payload.catalog_item_author : contract?.catalog_connector_title;
        return author ? [author] : [];
      }
      default: return [];
    }
  },
  facetKeys: [CATALOG_DOMAIN_FILTER, CATALOG_PLATFORM_FILTER, CATALOG_AUTHOR_FILTER],
});

export const availableActionFilterSchemas = (facetCounts: FacetCounts | null) => {
  const valuesOf = (key: string) => Object.keys(facetCounts?.[key] ?? {});
  return [
    catalogFilterSchema(ACTION_NAME_FILTER, 'string'),
    catalogFilterSchema(CATALOG_DOMAIN_FILTER, 'string', { values: valuesOf(CATALOG_DOMAIN_FILTER) }),
    catalogFilterSchema(CATALOG_PLATFORM_FILTER, 'string', {
      values: ['Windows', 'Linux', 'MacOS'],
      array: true,
    }),
    catalogFilterSchema(CATALOG_AUTHOR_FILTER, 'string', { values: valuesOf(CATALOG_AUTHOR_FILTER) }),
    catalogFilterSchema(ACTION_PUBLISHED_FILTER, 'instant'),
  ];
};

export const searchAvailableActions = (input: SearchPaginationInput) => fetchAvailableActions()
  .then(items => searchPreview(availableCatalog(items), input));

export const fetchAvailableActionFacets = (input: SearchPaginationInput) => fetchAvailableActions()
  .then(items => facetsPreview(availableCatalog(items), input));
