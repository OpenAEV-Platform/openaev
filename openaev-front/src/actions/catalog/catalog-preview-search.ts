import { type Page } from '../../components/common/queryable/Page';
import { type Filter, type SearchPaginationInput } from '../../utils/api-types';

// In-memory search over simulated catalogue items, with the signature of the real
// paginated search, until the catalogue API ships (RFC 0005, PR 3b).

/** Facet value counts, keyed by filter key. */
export type FacetCounts = Record<string, Record<string, number>>;

export interface PreviewCatalog<T> {
  items: T[];
  text: (item: T) => string;
  valuesOf: (item: T, filterKey: string) => string[];
  facetKeys: string[];
}

// Facet values are ORed within a filter and filters are ANDed, like the platform lists.
const matches = <T>(catalog: PreviewCatalog<T>, item: T, input: SearchPaginationInput, ignoredKey?: string) => {
  const text = (input.textSearch ?? '').toLowerCase();
  const filters: Filter[] = input.filterGroup?.filters ?? [];
  return (!text || catalog.text(item).toLowerCase().includes(text))
    && filters
      .filter(filter => filter.key !== ignoredKey && (filter.values?.length ?? 0) > 0)
      .every(filter => catalog.valuesOf(item, filter.key).some(value => filter.values?.includes(value)));
};

const SORTED = {
  empty: false,
  sorted: true,
  unsorted: false,
};

export const searchPreview = <T>(catalog: PreviewCatalog<T>, input: SearchPaginationInput): Promise<{ data: Page<T> }> => {
  const sort = input.sorts?.[0];
  const direction = sort?.direction?.toUpperCase() === 'DESC' ? -1 : 1;
  const field = sort?.property as keyof T | undefined;
  const found = catalog.items
    .filter(item => matches(catalog, item, input))
    .sort((a, b) => (field ? String(a[field]).localeCompare(String(b[field])) * direction : 0));
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
      sort: SORTED,
      pageable: {
        offset: input.page * input.size,
        pageNumber: input.page,
        pageSize: input.size,
        paged: true,
        unpaged: false,
        sort: SORTED,
      },
    },
  });
};

// Each facet counts under every other filter, so its own values stay visible while selected.
export const facetsPreview = <T>(catalog: PreviewCatalog<T>, input: SearchPaginationInput): Promise<{ data: FacetCounts }> => {
  const count = (key: string) => catalog.items
    .filter(item => matches(catalog, item, input, key))
    .flatMap(item => catalog.valuesOf(item, key))
    .reduce<Record<string, number>>((acc, value) => ({
      ...acc,
      [value]: (acc[value] ?? 0) + 1,
    }), {});
  return Promise.resolve({ data: Object.fromEntries(catalog.facetKeys.map(key => [key, count(key)])) });
};
