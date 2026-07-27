import { useEffect, useState } from 'react';

import { buildSearchPagination } from '../../components/common/queryable/QueryableUtils';
import { type SearchPaginationInput } from '../api-types';

// Resolves the total element count behind a paginated search endpoint with a
// minimal size-1 probe (only totalElements is read). Used by detail page heroes
// to surface headline counts (findings, injects played, ...) without loading
// the full lists. Returns null while loading (callers render '-') and 0 on
// error. Pass a memoized (useCallback) search function: the probe re-runs on
// every identity change.
const useSearchTotal = (
  search: (input: SearchPaginationInput) => Promise<{ data: { totalElements?: number } }>,
): number | null => {
  const [total, setTotal] = useState<number | null>(null);
  useEffect(() => {
    let cancelled = false;
    setTotal(null);
    search(buildSearchPagination({ size: 1 }))
      .then((result) => {
        if (!cancelled) setTotal(result.data.totalElements ?? 0);
      })
      .catch(() => {
        if (!cancelled) setTotal(0);
      });
    return () => {
      cancelled = true;
    };
  }, [search]);
  return total;
};

export default useSearchTotal;
