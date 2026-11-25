import { useState } from 'react';

import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { type OlderVersionInUse } from './usages/usageCounts';
import { moveUsages } from './usages/usageMove';
import useNotifyMoves from './usages/useNotifyMoves';

/** Picks the usages left on older versions, by version or all at once, and moves them to the new one. Nothing is picked first. */
const useNewVersionMove = (action: ThreatArsenalAction, olderVersions: OlderVersionInUse[]) => {
  const notifyMoves = useNotifyMoves();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [moving, setMoving] = useState(false);

  const allIds = olderVersions.flatMap(({ usages }) => usages.payload_usages.map(usage => usage.usage_id));
  const idsOf = (older: OlderVersionInUse) => older.usages.payload_usages.map(usage => usage.usage_id);
  const selectedIn = (older: OlderVersionInUse) => idsOf(older).filter(id => selected.has(id)).length;

  const toggleVersion = (older: OlderVersionInUse) => {
    const ids = idsOf(older);
    const on = selectedIn(older) !== ids.length;
    setSelected((previous) => {
      const next = new Set(previous);
      ids.forEach(id => (on ? next.add(id) : next.delete(id)));
      return next;
    });
  };

  // Resolves whether the move went through; a failed call already told the user.
  const move = async () => {
    const targetId = action.action_payload?.payload_id;
    if (!targetId) return false;
    setMoving(true);
    try {
      const results = await Promise.all(olderVersions
        .map(older => ({
          older,
          chosen: older.usages.payload_usages.filter(usage => selected.has(usage.usage_id)),
        }))
        .filter(({ chosen }) => chosen.length > 0)
        .map(({ older, chosen }) => moveUsages(older.version.payload_action_id, targetId, chosen)));
      notifyMoves(results, action.action_payload_version ?? '');
      return true;
    } catch {
      return false;
    } finally {
      setMoving(false);
    }
  };

  return {
    selectedCount: selected.size,
    totalCount: allIds.length,
    allSelected: allIds.length > 0 && selected.size === allIds.length,
    selectAll: () => setSelected(new Set(allIds)),
    clear: () => setSelected(new Set()),
    selectedIn,
    toggleVersion,
    moving,
    move,
  };
};

export default useNewVersionMove;
