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

// Numbers compare as numbers, anything else as a date.
const comparable = (value: string) => (value !== '' && !Number.isNaN(Number(value)) ? Number(value) : Date.parse(value));

// One filter against the item's values, with the operators of the platform lists.
const matchesFilter = (values: string[], filter: Filter) => {
  const wanted = filter.values ?? [];
  const lower = (value: string) => value.toLowerCase();
  const anyWanted = (test: (value: string, target: string) => boolean) => wanted.some(target => values.some(value => test(lower(value), lower(target))));
  const compare = (test: (left: number, right: number) => boolean) => values.some((value) => {
    const left = comparable(value);
    const right = comparable(wanted[0] ?? '');
    return !Number.isNaN(left) && !Number.isNaN(right) && test(left, right);
  });
  switch (filter.operator ?? 'eq') {
    case 'empty': return values.every(value => value === '');
    case 'not_empty': return values.some(value => value !== '');
    case 'not_eq': return !values.some(value => wanted.includes(value));
    case 'contains': return anyWanted((value, target) => value.includes(target));
    case 'not_contains': return !anyWanted((value, target) => value.includes(target));
    case 'starts_with': return anyWanted((value, target) => value.startsWith(target));
    case 'not_starts_with': return !anyWanted((value, target) => value.startsWith(target));
    case 'gt': return compare((left, right) => left > right);
    case 'gte': return compare((left, right) => left >= right);
    case 'lt': return compare((left, right) => left < right);
    case 'lte': return compare((left, right) => left <= right);
    default: return values.some(value => wanted.includes(value));
  }
};

// Values are ORed within a filter; filters follow the group mode, like the platform lists.
const matches = <T>(catalog: PreviewCatalog<T>, item: T, input: SearchPaginationInput, ignoredKey?: string) => {
  const text = (input.textSearch ?? '').toLowerCase();
  const filters: Filter[] = (input.filterGroup?.filters ?? [])
    .filter(filter => filter.key !== ignoredKey
      && ((filter.values?.length ?? 0) > 0 || filter.operator === 'empty' || filter.operator === 'not_empty'));
  const test = (filter: Filter) => matchesFilter(catalog.valuesOf(item, filter.key), filter);
  const filtersMatch = input.filterGroup?.mode === 'or' && filters.length > 0 ? filters.some(test) : filters.every(test);
  return (!text || catalog.text(item).toLowerCase().includes(text)) && filtersMatch;
};

// Filter fields of a local catalogue, for "Add filter": enum values come from the facet counts.
export const catalogFilterSchema = (
  name: string,
  type: 'string' | 'instant' | 'integer',
  options: {
    values?: string[];
    array?: boolean;
  } = {},
) => ({
  schema_property_entity: 'CatalogItem',
  schema_property_name: name,
  schema_property_label: name,
  schema_property_type: type,
  schema_property_type_array: options.array ?? false,
  schema_property_values: options.values,
});

const SORTED = {
  empty: false,
  sorted: true,
  unsorted: false,
};

// Sorts in order, empty values last, as the server does.
const compareBySorts = <T>(sorts: SearchPaginationInput['sorts'] = []) => (a: T, b: T) => {
  for (const sort of sorts) {
    const field = sort.property as keyof T | undefined;
    if (!field) continue;
    const left = a[field] == null || a[field] === '' ? undefined : String(a[field]);
    const right = b[field] == null || b[field] === '' ? undefined : String(b[field]);
    if (left !== right) {
      if (left === undefined) return 1;
      if (right === undefined) return -1;
      return left.localeCompare(right) * (sort.direction?.toUpperCase() === 'DESC' ? -1 : 1);
    }
  }
  return 0;
};

export const searchPreview = <T>(catalog: PreviewCatalog<T>, input: SearchPaginationInput): Promise<{ data: Page<T> }> => {
  const found = catalog.items
    .filter(item => matches(catalog, item, input))
    .sort(compareBySorts<T>(input.sorts));
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
