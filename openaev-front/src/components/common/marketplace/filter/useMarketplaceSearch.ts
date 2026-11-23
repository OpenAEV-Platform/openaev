import { useEffect, useState } from 'react';

import { type CatalogFacetCounts } from '../../../../actions/catalog/catalog-helper';
import { type SearchPaginationInput, type SortField } from '../../../../utils/api-types';
import { type Page } from '../../queryable/Page';
import { buildSearchPagination, listStateFingerprint } from '../../queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../queryable/useQueryableWithLocalStorage';

interface Options<T> {
  storageKey: string;
  defaultSort: SortField;
  search: (input: SearchPaginationInput) => Promise<{ data: Page<T> }>;
  fetchFacetCounts: (input: SearchPaginationInput) => Promise<CatalogFacetCounts>;
}

// An Available tab searched on the server: its items page by page, and the counts of its facets.
const useMarketplaceSearch = <T>({ storageKey, defaultSort, search, fetchFacetCounts }: Options<T>) => {
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [facetCounts, setFacetCounts] = useState<CatalogFacetCounts | null>(null);
  const [reloadCount, setReloadCount] = useState(0);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    storageKey,
    buildSearchPagination({ sorts: [defaultSort] }),
  );
  const { page } = searchPaginationInput;
  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();

  // A next page leaves the counts as they are.
  const listState = listStateFingerprint(searchPaginationInput);
  useEffect(() => {
    let stale = false;
    fetchFacetCounts(searchPaginationInput)
      .then(counts => !stale && setFacetCounts(counts))
      .catch(() => undefined);
    return () => {
      stale = true;
    };
  }, [listState, reloadCount]);

  return {
    items,
    searchPaginationInput,
    queryableHelpers,
    facetCounts,
    loadingFirstPage: loading && page === 0,
    filtered: (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0 || !!searchPaginationInput.textSearch,
    resetFilters: () => {
      queryableHelpers.filterHelpers.handleClearAllFilters();
      queryableHelpers.textSearchHelpers.handleTextSearch('');
    },
    reload: () => setReloadCount(count => count + 1),
    pagination: {
      fetch: search,
      setContent: setItems,
      setLoading,
      searchPaginationInput,
      queryableHelpers,
      reloadContentCount: reloadCount,
      resultsCount: true,
      infiniteScroll: true,
    },
    loadMore: {
      hasMore: items.length < totalElements,
      loading: loading && page > 0,
      paused: loading && page === 0,
      onLoadMore: () => queryableHelpers.paginationHelpers.handleChangePage(page + 1),
    },
  };
};

export default useMarketplaceSearch;
