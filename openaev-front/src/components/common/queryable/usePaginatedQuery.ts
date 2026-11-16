import { keepPreviousData, type QueryKey, useQuery, type UseQueryOptions } from '@tanstack/react-query';
import { useEffect } from 'react';

import { type SearchPaginationInput } from '../../../utils/api-types';
import { type Page } from './Page';
import { type QueryableHelpers } from './QueryableHelpers';

/** The input state of a list: what `useQueryable` and `useQueryableWithLocalStorage` return. */
interface QueryableState {
  queryableHelpers: QueryableHelpers;
  searchPaginationInput: SearchPaginationInput;
}

const NO_ITEMS: never[] = [];
// The total is the query's: nothing to set by hand.
const derivedTotal = () => {};

/**
 * A paginated list read from a TanStack query: the query counterpart of `PaginationComponentV2`'s
 * fetching, rendered with `QueryableToolbar`. The caller owns the input (text search, filters,
 * sorts, page, size) and passes it as returned by `useQueryableWithLocalStorage` (kept in the URI
 * and localStorage) or `useQueryable` (pickers and dialogs: kept in memory). Each input is its own
 * query, so the page refreshes like any query (live changes, focus, reconnect) and its total comes
 * from the data. Another page, filter or sort keeps the previous page on screen
 * (`isPlaceholderData`) until the new one arrives, so the total and the page selector do not reset
 * in between.
 *
 * Changes go through the live router (`useLiveMutation`, `useApplyChanges`), never through the
 * pagination helpers: `handleChangeTotalElements` and `incrementTotalElements` do nothing here.
 */
const usePaginatedQuery = <TItem, TQueryKey extends QueryKey = QueryKey>(
  { queryableHelpers, searchPaginationInput }: QueryableState,
  queryOptionsOf: (input: SearchPaginationInput) => UseQueryOptions<Page<TItem>, unknown, Page<TItem>, TQueryKey>,
) => {
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
  // from another screen): restart from the first page, as `PaginationComponentV2` does. Only on
  // settled data: a cached page shown while it refetches may predate rows added since. Guarded on
  // pageNumber > 0 so an empty dataset (totalPages = 0, page 0) does not loop.
  useEffect(() => {
    if (page === undefined || query.isPlaceholderData || query.isFetching) return;
    if (page.pageable.pageNumber > 0 && page.totalPages <= page.pageable.pageNumber) {
      paginationHelpers.handleChangePage(0);
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
