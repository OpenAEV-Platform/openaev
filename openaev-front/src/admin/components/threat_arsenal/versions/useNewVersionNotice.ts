import { useEffect, useState } from 'react';

import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { type OlderVersionInUse, usageCount } from './usages/usageCounts';
import useVersionsUsages from './usages/useVersionsUsages';
import usePayloadVersions from './usePayloadVersions';
import useSkipNewVersionDialog from './useSkipNewVersionDialog';

/**
 * After a save that added a version: offers to move the injects left on older ones. The new card
 * replaces this one once that is done, as this card's menu hosts the dialog.
 */
const useNewVersionNotice = (actionId: string, onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void) => {
  const [saved, setSaved] = useState<ThreatArsenalAction | null>(null);
  const [skipsDialog] = useSkipNewVersionDialog();
  const versions = usePayloadVersions(saved?.injector_contract_id ?? '', !!saved);
  const usages = useVersionsUsages(saved ? versions : null);

  const loaded = !!versions && !!usages;
  const olderVersions: OlderVersionInUse[] = versions && usages
    ? versions
        .filter(version => version.payload_action_id !== saved?.injector_contract_id)
        .map(version => ({
          version,
          usages: usages[version.payload_id],
        }))
        .filter(older => usageCount(older.usages) > 0)
    : [];

  const replaceCard = (action: ThreatArsenalAction) => {
    onUpdate?.(action, actionId);
    setSaved(null);
  };

  useEffect(() => {
    if (saved && loaded && olderVersions.length === 0) replaceCard(saved);
  }, [saved, loaded, olderVersions.length]);

  const afterSave = (action: ThreatArsenalAction) => {
    if (action.injector_contract_id === actionId || skipsDialog) {
      onUpdate?.(action, actionId);
      return;
    }
    setSaved(action);
  };

  return {
    notice: saved && olderVersions.length > 0
      ? {
          action: saved,
          olderVersions,
        }
      : null,
    afterSave,
    closeNotice: () => {
      if (saved) replaceCard(saved);
    },
  };
};

export default useNewVersionNotice;
