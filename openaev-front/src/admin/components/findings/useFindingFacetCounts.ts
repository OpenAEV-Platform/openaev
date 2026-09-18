import { useEffect, useState } from 'react';

import { fetchStableFindingFacetCounts } from '../../../actions/findings/finding-actions';
import { type SearchPaginationInput } from '../../../utils/api-types';

export interface FindingSourceFacet {
  source_id: string;
  source_name: string;
  source_type: string;
  source_count: number;
}

export interface FindingFacetCounts {
  severities: Record<string, number>;
  types: Record<string, number>;
  cloud_providers: Record<string, number>;
  sources: FindingSourceFacet[];
}

const useFindingFacetCounts = (
  searchPaginationInput: SearchPaginationInput,
): FindingFacetCounts | null => {
  const [counts, setCounts] = useState<FindingFacetCounts | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchStableFindingFacetCounts(searchPaginationInput)
      .then((response) => {
        if (!cancelled) setCounts((response.data ?? null) as FindingFacetCounts | null);
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

export default useFindingFacetCounts;
