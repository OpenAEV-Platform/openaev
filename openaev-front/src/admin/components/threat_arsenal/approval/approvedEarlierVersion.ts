import { useEffect, useState } from 'react';

import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { type PayloadVersionOutput } from '../../../../utils/api-types';

// One request per version shown, shared by every card, row and page that asks.
const cache = new Map<string, Promise<boolean>>();

/**
 * Whether another version of the same action was approved: then the version `actionId` is a new
 * version of an action in use (the approved one keeps running), otherwise a new action that nothing
 * can use yet, even after several saves (superseded ones were never approved).
 */
export const hasApprovedEarlierVersion = (actionId: string): Promise<boolean> => {
  const known = cache.get(actionId);
  if (known) return known;
  const loading = fetchThreatArsenalVersions(actionId)
    .then((response: { data: PayloadVersionOutput[] }) => response.data.some(version => version.payload_action_id !== actionId
      && version.payload_installed
      && version.payload_approval_status === 'APPROVED'))
    .catch(() => {
      cache.delete(actionId);
      return false;
    });
  cache.set(actionId, loading);
  return loading;
};

/**
 * {@link hasApprovedEarlierVersion} for a component; `fallback` (a guess from the version count)
 * until it is known. Only asked when `enabled` (the version waits or was turned down).
 */
export const useApprovedEarlierVersion = (actionId: string | undefined, enabled: boolean, fallback: boolean): boolean => {
  const [known, setKnown] = useState<boolean | undefined>(undefined);
  useEffect(() => {
    setKnown(undefined);
    if (!actionId || !enabled) return undefined;
    let cancelled = false;
    hasApprovedEarlierVersion(actionId).then((result) => {
      if (!cancelled) setKnown(result);
    });
    return () => {
      cancelled = true;
    };
  }, [actionId, enabled]);
  return known ?? fallback;
};
