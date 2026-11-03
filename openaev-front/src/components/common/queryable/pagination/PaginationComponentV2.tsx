import { useEffect } from 'react';

import { type SearchPaginationInput } from '../../../../utils/api-types';
import { type Page } from '../Page';
import QueryableToolbar, { type QueryableToolbarProps } from '../QueryableToolbar';

interface Props<T> extends QueryableToolbarProps {
  fetch: (input: SearchPaginationInput) => Promise<{ data: Page<T> }>;
  setContent: (data: T[]) => void;
  setLoading?: (loading: boolean) => void;
  reloadContentCount?: number;
}

/**
 * A paginated list that fetches its own page: on every change of the input, `fetch` runs and its
 * content goes to `setContent`, around the controls of `QueryableToolbar`. A list read from a
 * TanStack query uses `usePaginatedQuery` with `QueryableToolbar` instead.
 */
const PaginationComponentV2 = <T extends object>({
  fetch,
  setContent,
  setLoading,
  reloadContentCount = 0,
  ...toolbarProps
}: Props<T>) => {
  const { searchPaginationInput, queryableHelpers, contextId } = toolbarProps;

  useEffect(() => {
    // Modify URI
    if (queryableHelpers.uriHelpers) {
      queryableHelpers.uriHelpers.updateUri();
    }

    // Fetch data. The stale flag (set by the effect cleanup when the search
    // input changes or the component unmounts) ensures a superseded request
    // can no longer overwrite the newer results or reset the page from old
    // totals. Loading is cleared in finally so a rejected search (network or
    // API error) never leaves callers stuck in a perpetual loading state.
    let stale = false;
    setLoading?.(true);
    fetch(searchPaginationInput)
      .then((result: { data: Page<T> }) => {
        if (stale) {
          return;
        }
        const { data } = result;
        setContent(data.content);
        queryableHelpers.paginationHelpers.handleChangeTotalElements(data.totalElements);
        // The current page fell past the end (dataset shrank, narrower filter,
        // state restored from another screen): restart from the first page.
        // Guarded on pageNumber > 0 so an empty dataset (totalPages = 0,
        // page 0) does not trigger an endless reset/refetch loop.
        if (data.pageable.pageNumber > 0 && data.totalPages <= data.pageable.pageNumber) {
          queryableHelpers.paginationHelpers.handleChangePage(0);
        }
      })
      .catch(() => {
        // The API layer (simpleCall/simplePostCall) already notified the user
        // and rethrows; swallow the rejection so it does not surface as an
        // unhandled promise rejection.
      })
      .finally(() => {
        if (!stale) {
          setLoading?.(false);
        }
      });
    return () => {
      stale = true;
    };
    // `fetch` is intentionally not a dependency: many callers pass inline
    // closures (new identity on every render), so depending on it would
    // refetch in a loop. When the effect runs it always uses the latest
    // render's `fetch`. `contextId` IS a dependency so a scope switch that
    // leaves the search input untouched (e.g. navigating from one simulation
    // to another with a shared storage key) still refetches with the latest
    // closure instead of keeping the previous scope's rows.
  }, [searchPaginationInput, reloadContentCount, contextId]);

  return <QueryableToolbar {...toolbarProps} />;
};

export default PaginationComponentV2;
