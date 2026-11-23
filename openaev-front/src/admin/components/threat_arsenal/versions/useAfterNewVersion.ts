import { useEffect, useState } from 'react';

import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { olderVersionsInUse } from './usages/usageCounts';
import useUsagesByVersion from './usages/useUsagesByVersion';
import usePayloadVersions from './usePayloadVersions';

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
  const versions = usePayloadVersions(saved?.injector_contract_id ?? '', !!saved);
  const usages = useUsagesByVersion(saved ? versions : null);

  useEffect(() => {
    if (!saved || !versions || !usages) return;
    onUpdate?.(saved, actionId);
    if (olderVersionsInUse(versions, usages, saved.injector_contract_id).length > 0) onShowSaved?.(saved);
    setSaved(null);
  }, [saved, versions, usages]);

  return (action: ThreatArsenalAction) => {
    if (action.injector_contract_id === actionId) {
      onUpdate?.(action, actionId);
      return;
    }
    setSaved(action);
  };
};

export default useAfterNewVersion;
