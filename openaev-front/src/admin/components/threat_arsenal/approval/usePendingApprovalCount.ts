import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';

import { fetchThreatArsenalFacetCounts } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { type ThreatArsenalFacetCounts } from '../useThreatArsenalFacetCounts';

/**
 * How many actions wait for approval (new actions and new versions), for an approver's menu. Read
 * again on every navigation so the count follows decisions without polling. Undefined when disabled
 * (no "Approve content") or not loaded yet.
 */
const usePendingApprovalCount = (enabled: boolean): number | undefined => {
  const { pathname } = useLocation();
  const [count, setCount] = useState<number | undefined>(undefined);

  useEffect(() => {
    if (!enabled) {
      setCount(undefined);
      return undefined;
    }
    let cancelled = false;
    fetchThreatArsenalFacetCounts(buildSearchPagination({}))
      .then((response) => {
        if (!cancelled) setCount((response.data as ThreatArsenalFacetCounts | null)?.approvals?.PENDING ?? 0);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [enabled, pathname]);

  return count;
};

export default usePendingApprovalCount;
