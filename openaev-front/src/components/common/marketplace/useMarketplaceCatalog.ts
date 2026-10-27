import { useEffect, useState } from 'react';

import { type SearchPaginationInput, type SortField } from '../../../utils/api-types';
import { type Page } from '../queryable/Page';
import { buildSearchPagination } from '../queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../queryable/useQueryableWithLocalStorage';

interface Options<T> {
  storageKey: string;
  defaultSorts: SortField[];
  search: (input: SearchPaginationInput) => Promise<{ data: Page<T> }>;
  fetchFacets: (input: SearchPaginationInput) => Promise<{ data: Record<string, Record<string, number>> }>;
  installOne?: (id: string) => Promise<string[]>;
  installAll?: () => Promise<string[]>;
}

// Search, facets and installation state of a marketplace "Available" tab.
const NOTHING_INSTALLED = () => Promise.resolve([]);

const useMarketplaceCatalog = <T>({
  storageKey,
  defaultSorts,
  search,
  fetchFacets,
  installOne = NOTHING_INSTALLED,
  installAll = NOTHING_INSTALLED,
}: Options<T>) => {
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [facetCounts, setFacetCounts] = useState<Record<string, Record<string, number>> | null>(null);
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    storageKey,
    buildSearchPagination({ sorts: defaultSorts }),
  );

  const fetchItems = (input: SearchPaginationInput) => {
    setLoading(true);
    // Loading ends with the results (below): ending it before shows the empty state for a frame.
    return search(input).catch((error) => {
      setLoading(false);
      throw error;
    });
  };
  const setLoadedItems = (loaded: T[]) => {
    setItems(loaded);
    setLoading(false);
  };

  useEffect(() => {
    fetchFacets(searchPaginationInput).then(({ data }) => setFacetCounts(data));
  }, [searchPaginationInput]);

  const markInstalled = (ids: string[]) => setInstalledIds(previous => new Set([...previous, ...ids]));

  const hasActiveFilters = (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || !!searchPaginationInput.textSearch;
  const handleResetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  return {
    items,
    setItems: setLoadedItems,
    loading,
    fetchItems,
    queryableHelpers,
    searchPaginationInput,
    facetCounts,
    hasActiveFilters,
    handleResetFilters,
    isInstalled: (id: string) => installedIds.has(id),
    markInstalled,
    install: (id: string) => installOne(id).then(markInstalled),
    installAll: () => installAll().then(markInstalled),
  };
};

export default useMarketplaceCatalog;
