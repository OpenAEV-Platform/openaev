import { type Page } from '../../components/common/queryable/Page';
import { type Filter, type SearchPaginationInput } from '../../utils/api-types';
import { CATALOG_PAYLOADS_PREVIEW, type CatalogPayload } from './payload-catalog-preview';

// Preview: every function below answers from simulated data until the catalogue API ships
// (RFC 0005: search and facets in PR 3b, install in PR 4). Their signatures are the real ones.

export const CATALOG_DOMAIN_FILTER = 'catalog_item_domain';
export const CATALOG_PLATFORM_FILTER = 'catalog_item_platforms';
export const CATALOG_AUTHOR_FILTER = 'catalog_item_author';

export interface CatalogFacetCounts {
  domains: Record<string, number>;
  platforms: Record<string, number>;
  authors: Record<string, number>;
}

const valuesOf = (item: CatalogPayload, key: string): string[] => {
  switch (key) {
    case CATALOG_DOMAIN_FILTER: return [item.catalog_item_domain];
    case CATALOG_PLATFORM_FILTER: return item.catalog_item_platforms;
    case CATALOG_AUTHOR_FILTER: return [item.catalog_item_author];
    default: return [];
  }
};

// Facet values are ORed within a filter and filters are ANDed, like the Installed tab.
const matches = (item: CatalogPayload, input: SearchPaginationInput, ignoredKey?: string) => {
  const text = (input.textSearch ?? '').toLowerCase();
  const textMatch = !text
    || item.catalog_item_name.toLowerCase().includes(text)
    || item.catalog_item_description.toLowerCase().includes(text);
  const filters: Filter[] = input.filterGroup?.filters ?? [];
  return textMatch && filters
    .filter(filter => filter.key !== ignoredKey && (filter.values?.length ?? 0) > 0)
    .every(filter => valuesOf(item, filter.key).some(value => filter.values?.includes(value)));
};

const compare = (input: SearchPaginationInput) => (a: CatalogPayload, b: CatalogPayload) => {
  const sort = input.sorts?.[0];
  const direction = sort?.direction?.toUpperCase() === 'DESC' ? -1 : 1;
  const field = (sort?.property ?? 'catalog_item_name') as keyof CatalogPayload;
  return String(a[field]).localeCompare(String(b[field])) * direction;
};

export const searchCatalogPayloads = (input: SearchPaginationInput): Promise<{ data: Page<CatalogPayload> }> => {
  const found = CATALOG_PAYLOADS_PREVIEW.filter(item => matches(item, input)).sort(compare(input));
  const content = found.slice(input.page * input.size, (input.page + 1) * input.size);
  const totalPages = Math.max(1, Math.ceil(found.length / input.size));
  return Promise.resolve({
    data: {
      content,
      empty: content.length === 0,
      first: input.page === 0,
      last: input.page >= totalPages - 1,
      number: input.page,
      numberOfElements: content.length,
      size: input.size,
      totalElements: found.length,
      totalPages,
      sort: {
        empty: false,
        sorted: true,
        unsorted: false,
      },
      pageable: {
        offset: input.page * input.size,
        pageNumber: input.page,
        pageSize: input.size,
        paged: true,
        unpaged: false,
        sort: {
          empty: false,
          sorted: true,
          unsorted: false,
        },
      },
    },
  });
};

// Each facet counts under every other filter, so its own values stay visible while selected.
export const fetchCatalogPayloadFacets = (input: SearchPaginationInput): Promise<{ data: CatalogFacetCounts }> => {
  const count = (key: string) => CATALOG_PAYLOADS_PREVIEW
    .filter(item => matches(item, input, key))
    .flatMap(item => valuesOf(item, key))
    .reduce<Record<string, number>>((acc, value) => ({
      ...acc,
      [value]: (acc[value] ?? 0) + 1,
    }), {});
  return Promise.resolve({
    data: {
      domains: count(CATALOG_DOMAIN_FILTER),
      platforms: count(CATALOG_PLATFORM_FILTER),
      authors: count(CATALOG_AUTHOR_FILTER),
    },
  });
};

export const installCatalogPayloads = (externalIds: string[]): Promise<string[]> => Promise.resolve(externalIds);

export const installAllCatalogPayloads = (): Promise<string[]> =>
  Promise.resolve(CATALOG_PAYLOADS_PREVIEW.map(item => item.catalog_item_external_id));
