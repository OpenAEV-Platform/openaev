import { keepPreviousData, type QueryKey, useQuery, type UseQueryOptions } from '@tanstack/react-query';
import { useEffect } from 'react';

import { type SearchPaginationInput } from '../../../utils/api-types';
import { type Page } from './Page';
import { type QueryableHelpers } from './QueryableHelpers';
import { useQueryableWithLocalStorage } from './useQueryableWithLocalStorage';

const NO_ITEMS: never[] = [];
// The total is the query's: nothing to set by hand.
const derivedTotal = () => {};

/**
 * A paginated list read from a TanStack query: the query counterpart of `PaginationComponentV2`'s
 * fetching, rendered with `QueryableToolbar`. The input (text search, filters, sorts, page, size)
 * is the one of `useQueryableWithLocalStorage`, with the same URI and localStorage persistence;
 * each input is its own query, so the page refreshes like any query (live changes, focus,
 * reconnect) and its total comes from the data. Another page, filter or sort keeps the previous
 * page on screen (`isPlaceholderData`) until the new one arrives, so the total and the page
 * selector do not reset in between.
 *
 * Changes go through the live router (`useLiveMutation`, `useApplyChanges`), never through the
 * pagination helpers: `handleChangeTotalElements` and `incrementTotalElements` do nothing here.
 */
const usePaginatedQuery = <TItem, TQueryKey extends QueryKey = QueryKey>(
  localStorageKey: string,
  initSearchPaginationInput: Partial<SearchPaginationInput>,
  queryOptionsOf: (input: SearchPaginationInput) => UseQueryOptions<Page<TItem>, unknown, Page<TItem>, TQueryKey>,
) => {
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(localStorageKey, initSearchPaginationInput);
  const query = useQuery({
    ...queryOptionsOf(searchPaginationInput),
    placeholderData: keepPreviousData,
  });
  const page = query.data;
  const { paginationHelpers, uriHelpers } = queryableHelpers;

  useEffect(() => {
    uriHelpers?.updateUri();
  }, [searchPaginationInput]);

  // The current page fell past the end (its last rows deleted, a narrower filter, state restored
  // from another screen): move to the new last page. Only on settled data: a cached page shown
  // while it refetches may predate rows added since. Guarded on pageNumber > 0 so an empty dataset
  // (totalPages = 0, page 0) does not loop.
  useEffect(() => {
    if (page === undefined || query.isPlaceholderData || query.isFetching) return;
    if (page.pageable.pageNumber > 0 && page.totalPages <= page.pageable.pageNumber) {
      paginationHelpers.handleChangePage(Math.max(0, page.totalPages - 1));
    }
  }, [page, query.isPlaceholderData, query.isFetching]);

  const totalElements = page?.totalElements ?? 0;
  const helpers: QueryableHelpers = {
    ...queryableHelpers,
    paginationHelpers: {
      ...paginationHelpers,
      getTotalElements: () => totalElements,
      handleChangeTotalElements: derivedTotal,
      incrementTotalElements: derivedTotal,
    },
  };

  return {
    query,
    items: page?.content ?? (NO_ITEMS as TItem[]),
    searchPaginationInput,
    queryableHelpers: helpers,
  };
};

export default usePaginatedQuery;
