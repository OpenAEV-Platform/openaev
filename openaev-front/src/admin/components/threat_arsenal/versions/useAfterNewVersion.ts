import { useEffect, useState } from 'react';

import { fetchThreatArsenalOlderVersionsInUse } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ThreatArsenalAction } from '../../../../utils/api-types';

/**
 * After a save that added a version: the new card replaces this one, and when injects are left on older
 * versions, its Versions tab opens on them (the tab says what was saved; the user picks what moves).
 */
const useAfterNewVersion = (
  actionId: string,
  onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void,
  onShowSaved?: (action: ThreatArsenalAction) => void,
) => {
  const [saved, setSaved] = useState<ThreatArsenalAction | null>(null);

  useEffect(() => {
    if (!saved) return;
    const savedId = saved.injector_contract_id;
    fetchThreatArsenalOlderVersionsInUse({ action_ids: [savedId] })
      .then(({ data }: { data: Record<string, number> }) => data[savedId] ?? 0)
      .catch(() => 0)
      .then((olderUsages) => {
        onUpdate?.(saved, actionId);
        if (olderUsages > 0) onShowSaved?.(saved);
        setSaved(null);
      });
  }, [saved]);

  return (action: ThreatArsenalAction) => {
    if (action.injector_contract_id === actionId) {
      onUpdate?.(action, actionId);
      return;
    }
    setSaved(action);
  };
};

export default useAfterNewVersion;
