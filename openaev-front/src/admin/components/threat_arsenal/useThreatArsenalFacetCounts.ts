import { useEffect, useState } from 'react';

import { fetchThreatArsenalFacetCounts } from '../../../actions/threat_arsenals/threatArsenal-actions';
import { type SearchPaginationInput } from '../../../utils/api-types';

export interface ThreatArsenalFacetCounts {
  platforms: Record<string, number>;
  statuses: Record<string, number>;
}

// Platform and status counts under the current filters; null until the first answer.
const useThreatArsenalFacetCounts = (
  searchPaginationInput: SearchPaginationInput,
): ThreatArsenalFacetCounts | null => {
  const [counts, setCounts] = useState<ThreatArsenalFacetCounts | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchThreatArsenalFacetCounts(searchPaginationInput)
      .then((response) => {
        if (!cancelled) setCounts((response.data ?? null) as ThreatArsenalFacetCounts | null);
      })
      .catch(() => {
        if (!cancelled) setCounts(null);
      });
    return () => {
      cancelled = true;
    };
  }, [searchPaginationInput]);

  return counts;
};

export default useThreatArsenalFacetCounts;
