import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';

import { fetchThreatArsenalAction, searchThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { type ThreatArsenalAction, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { ACTION_PARAM, toThreatArsenalAction } from '../threatArsenalDeepLink';
import { type ApprovalStatus } from './approvalStatusUtils';

// Enough for a queue that is meant to be emptied, not browsed.
const QUEUE_SIZE = 100;

export interface QueueItem {
  action: ThreatArsenalAction;
  full?: ThreatArsenalActionFullOutput;
}

export const isFromCollector = (item: QueueItem) => item.full?.action_pending_version?.version_origin === 'COLLECTOR'
  || item.full?.action_approval_latest?.approval_origin === 'COLLECTOR'
  || item.action.action_payload?.payload_collector_type != null;

/**
 * Actions whose approval status is one of `statuses` (PENDING also matches approved actions with a
 * new version waiting), oldest first, each with its detail: who submitted the version and from where
 * are on the detail, and the queue stays small.
 */
interface QueueOptions {
  /** Load each action's detail (who submitted the version, from where); off for long lists. */
  details?: boolean;
  newestFirst?: boolean;
  size?: number;
}

export const useApprovalQueue = (
  statuses: ApprovalStatus[],
  keep: (item: QueueItem) => boolean = () => true,
  { details = true, newestFirst = false, size = QUEUE_SIZE }: QueueOptions = {},
) => {
  const [items, setItems] = useState<QueueItem[] | null>(null);
  const statusKey = statuses.join(',');
  // The filter may be a new function on every render; read the latest one when loading.
  const keepRef = useRef(keep);
  keepRef.current = keep;

  const load = useCallback(() => {
    searchThreatArsenalActions(buildSearchPagination({
      size,
      filterGroup: {
        mode: 'and',
        filters: [{
          id: generateFilterId(),
          key: 'action_payload_approval_status',
          operator: 'eq',
          mode: 'or',
          values: statusKey.split(','),
        }],
      },
      sorts: [{
        property: 'action_updated_at',
        direction: newestFirst ? 'DESC' : 'ASC',
      }],
    }))
      .then(async (response) => {
        const actions = (response.data?.content ?? []) as ThreatArsenalAction[];
        const fulls = details
          ? await Promise.all(actions.map(action => fetchThreatArsenalAction(action.injector_contract_id)
              .then(detail => detail.data as ThreatArsenalActionFullOutput)
              .catch(() => undefined)))
          : [];
        setItems(actions.map((action, index) => ({
          action,
          full: fulls[index],
        })).filter(item => keepRef.current(item)));
      })
      .catch(() => setItems([]));
  }, [statusKey, details, newestFirst, size]);

  useEffect(() => {
    load();
  }, [load]);

  return {
    items,
    reload: load,
    remove: (actionId: string) => setItems(prev => (prev ?? []).filter(item => item.action.injector_contract_id !== actionId)),
  };
};

/**
 * The action open in the review panel of a queue page, kept in sync with `?action=<id>` so a
 * notification (or a copied link) opens the exact version to look at.
 */
export const useQueueDrawer = () => {
  const [selected, setSelected] = useState<ThreatArsenalAction | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedActionId = searchParams.get(ACTION_PARAM);

  useEffect(() => {
    if (!linkedActionId) return undefined;
    let cancelled = false;
    fetchThreatArsenalAction(linkedActionId)
      .then((response) => {
        if (!cancelled) setSelected(toThreatArsenalAction(response.data as ThreatArsenalActionFullOutput));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [linkedActionId]);

  return {
    selected,
    open: (item: QueueItem) => setSelected(item.full ? toThreatArsenalAction(item.full) : item.action),
    close: () => {
      setSelected(null);
      if (linkedActionId) {
        searchParams.delete(ACTION_PARAM);
        setSearchParams(searchParams, { replace: true });
      }
    },
  };
};
