export interface FacetFilters<K extends string> {
  keyword: string;
  values: Record<K, string[]>;
}

export interface FacetFilter<T, K extends string> {
  noFilters: FacetFilters<K>;
  hasFilters: (filters: FacetFilters<K>) => boolean;
  counts: (items: T[], filters: FacetFilters<K>, key: K) => Record<string, number>;
  values: (items: T[], key: K) => string[];
  apply: (items: T[], filters: FacetFilters<K>) => T[];
}

// Values are ORed within a facet, facets ANDed; a facet is ignored while its own counts are computed.
const createFacetFilter = <T, K extends string>(
  facetValues: Record<K, (item: T) => string[]>,
  text: (item: T) => string,
): FacetFilter<T, K> => {
  const keys = Object.keys(facetValues) as K[];
  const matchesKeyword = (item: T, keyword: string) => text(item).toLowerCase().includes(keyword.toLowerCase());
  const matches = (item: T, filters: FacetFilters<K>, ignored?: K) => matchesKeyword(item, filters.keyword)
    && keys
      .filter(key => key !== ignored && filters.values[key].length > 0)
      .every(key => facetValues[key](item).some(value => filters.values[key].includes(value)));

  return {
    noFilters: {
      keyword: '',
      values: keys.reduce((values, key) => ({
        ...values,
        [key]: [],
      }), {} as Record<K, string[]>),
    },
    hasFilters: filters => filters.keyword !== '' || keys.some(key => filters.values[key].length > 0),
    counts: (items, filters, key) => items
      .filter(item => matches(item, filters, key))
      .flatMap(item => facetValues[key](item))
      .reduce<Record<string, number>>((counts, value) => ({
        ...counts,
        [value]: (counts[value] ?? 0) + 1,
      }), {}),
    values: (items, key) => [...new Set(items.flatMap(item => facetValues[key](item)))].sort(),
    apply: (items, filters) => items.filter(item => matches(item, filters)),
  };
};

export default createFacetFilter;
