import { useState } from 'react';

import { type SortHelpers } from '../../queryable/sort/SortHelpers';
import { type MarketplaceToolbarState } from '../toolbar/MarketplaceToolbar';
import { type FacetFilter, type FacetFilters } from './facetFilter';

export interface FacetState<K extends string> {
  filters: FacetFilters<K>;
  counts: (key: K) => Record<string, number>;
  values: (key: K) => string[];
  onToggle: (key: K, value: string) => void;
}

interface Options<T, K extends string, S extends string> {
  items: T[];
  facetFilter: FacetFilter<T, K>;
  sortValues: Record<S, (item: T) => string>;
  defaultSort: S;
  // Grouped by this key first, as the sections show them; no key last.
  groupKey?: (item: T) => string | undefined;
}

const byGroup = <T>(groupKey: (item: T) => string | undefined) => (a: T, b: T) => {
  const first = groupKey(a);
  const second = groupKey(b);
  if (first === second) return 0;
  if (!first) return 1;
  if (!second) return -1;
  return first.localeCompare(second);
};

// A catalogue is small: its search, facets and sort apply in memory.
const useMarketplaceFilters = <T, K extends string, S extends string>({ items, facetFilter, sortValues, defaultSort, groupKey }: Options<T, K, S>) => {
  const [filters, setFilters] = useState<FacetFilters<K>>(facetFilter.noFilters);
  const [sort, setSort] = useState<{
    field: S;
    asc: boolean;
  }>({
    field: defaultSort,
    asc: true,
  });

  const sortHelpers: SortHelpers = {
    getSortBy: () => sort.field,
    getSortAsc: () => sort.asc,
    handleDirectedSort: (field, asc) => setSort({
      field: field as S,
      asc,
    }),
    handleSort: field => setSort({
      field: field as S,
      asc: field === sort.field ? !sort.asc : true,
    }),
  };
  const bySort = (a: T, b: T) => sortValues[sort.field](a).localeCompare(sortValues[sort.field](b)) * (sort.asc ? 1 : -1);
  const compare = groupKey ? (a: T, b: T) => byGroup(groupKey)(a, b) || bySort(a, b) : bySort;
  const shown = facetFilter.apply(items, filters).sort(compare);

  const facetState: FacetState<K> = {
    filters,
    counts: key => facetFilter.counts(items, filters, key),
    values: key => facetFilter.values(items, key),
    onToggle: (key, value) => setFilters((previous) => {
      const selected = previous.values[key];
      return {
        ...previous,
        values: {
          ...previous.values,
          [key]: selected.includes(value) ? selected.filter(other => other !== value) : [...selected, value],
        },
      };
    }),
  };
  const toolbar: MarketplaceToolbarState = {
    keyword: filters.keyword,
    onSearch: keyword => setFilters(previous => ({
      ...previous,
      keyword: keyword ?? '',
    })),
    sortHelpers,
    count: shown.length,
  };

  return {
    shown,
    filtered: facetFilter.hasFilters(filters),
    resetFilters: () => setFilters(facetFilter.noFilters),
    facetState,
    toolbar,
  };
};

export default useMarketplaceFilters;
