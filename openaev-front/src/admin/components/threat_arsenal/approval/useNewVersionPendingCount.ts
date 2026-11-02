import { useEffect, useState } from 'react';

import { searchThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { type SearchPaginationInput } from '../../../../utils/api-types';

const PENDING_VERSION_FILTER_KEY = 'action_payload_pending_version';

/**
 * How many approved actions have a new version waiting, under the other current filters, for the
 * sidebar's "New version pending" row (the facet-count endpoint does not return it).
 */
const useNewVersionPendingCount = (searchPaginationInput: SearchPaginationInput): number | undefined => {
  const [count, setCount] = useState<number | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    const otherFilters = (searchPaginationInput.filterGroup?.filters ?? []).filter(f => f.key !== PENDING_VERSION_FILTER_KEY);
    searchThreatArsenalActions({
      ...searchPaginationInput,
      page: 0,
      size: 1,
      filterGroup: {
        mode: searchPaginationInput.filterGroup?.mode ?? 'and',
        filters: [...otherFilters, {
          id: generateFilterId(),
          key: PENDING_VERSION_FILTER_KEY,
          operator: 'eq',
          mode: 'or',
          values: ['true'],
        }],
      },
    })
      .then((response) => {
        if (!cancelled) setCount(response.data?.totalElements ?? 0);
      })
      .catch(() => {
        if (!cancelled) setCount(undefined);
      });
    return () => {
      cancelled = true;
    };
  }, [searchPaginationInput]);

  return count;
};

export default useNewVersionPendingCount;
