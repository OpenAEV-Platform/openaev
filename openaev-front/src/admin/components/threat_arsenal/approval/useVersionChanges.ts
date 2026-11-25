import { useEffect, useState } from 'react';

import { fetchThreatArsenalChanges } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { type PayloadFieldChange } from '../../../../components/common/content_versions/payloadFieldChanges';
import { type PayloadFieldChangeOutput } from '../../../../utils/api-types';

/**
 * What a version changes, field by field, compared with another version of the same payload (the
 * version in use); without one, everything the version has is added. Undefined while loading.
 */
const useVersionChanges = (actionId?: string, comparedToActionId?: string): PayloadFieldChange[] | undefined => {
  const [changes, setChanges] = useState<PayloadFieldChange[] | undefined>(undefined);

  useEffect(() => {
    setChanges(undefined);
    if (!actionId) {
      return undefined;
    }
    let cancelled = false;
    fetchThreatArsenalChanges(actionId, comparedToActionId)
      .then((response: { data: PayloadFieldChangeOutput[] }) => {
        if (!cancelled) setChanges(response.data);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [actionId, comparedToActionId]);

  return changes;
};

export default useVersionChanges;
