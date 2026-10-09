import { type SearchPaginationInput } from '../../utils/api-types';
import { facetsPreview, type PreviewCatalog, searchPreview } from './catalog-preview-search';
import { CATALOG_PAYLOADS_PREVIEW, type CatalogPayload } from './payload-catalog-preview';

// Preview until the catalogue API ships (RFC 0005: search and facets in PR 3b, install in PR 4).

export const CATALOG_DOMAIN_FILTER = 'catalog_item_domain';
export const CATALOG_PLATFORM_FILTER = 'catalog_item_platforms';
export const CATALOG_AUTHOR_FILTER = 'catalog_item_author';

const PAYLOAD_CATALOG: PreviewCatalog<CatalogPayload> = {
  items: CATALOG_PAYLOADS_PREVIEW,
  text: item => `${item.catalog_item_name} ${item.catalog_item_description}`,
  valuesOf: (item, key) => {
    switch (key) {
      case CATALOG_DOMAIN_FILTER: return [item.catalog_item_domain];
      case CATALOG_PLATFORM_FILTER: return item.catalog_item_platforms;
      case CATALOG_AUTHOR_FILTER: return [item.catalog_item_author];
      default: return [];
    }
  },
  facetKeys: [CATALOG_DOMAIN_FILTER, CATALOG_PLATFORM_FILTER, CATALOG_AUTHOR_FILTER],
};

export const searchCatalogPayloads = (input: SearchPaginationInput) => searchPreview(PAYLOAD_CATALOG, input);

export const fetchCatalogPayloadFacets = (input: SearchPaginationInput) => facetsPreview(PAYLOAD_CATALOG, input);

export const installCatalogPayloads = (externalIds: string[]): Promise<string[]> => Promise.resolve(externalIds);

export const installAllCatalogPayloads = (): Promise<string[]> =>
  Promise.resolve(CATALOG_PAYLOADS_PREVIEW.map(item => item.catalog_item_external_id));
