import { useEffect, useRef, useState } from 'react';

import { fetchThreatArsenalOlderVersionsInUse } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

const payloadActionIds = (actions: ThreatArsenalAction[]) => actions.filter(action => action.action_payload).map(action => action.injector_contract_id);

/** How many injects still run an older version of each listed action: one call for the actions newly listed. */
const useOlderVersionsInUse = (actions: ThreatArsenalAction[]) => {
  const catalog = isFeatureEnabled('CATALOG');
  const [usages, setUsages] = useState<Record<string, number>>({});
  const listed = useRef<Set<string>>(new Set());

  const load = (actionIds: string[]) => {
    if (!catalog || actionIds.length === 0) return;
    fetchThreatArsenalOlderVersionsInUse({ action_ids: actionIds }).then(({ data }: { data: Record<string, number> }) => setUsages(previous => ({
      ...Object.fromEntries(Object.entries(previous).filter(([actionId]) => !actionIds.includes(actionId))),
      ...data,
    })));
  };

  const actionIds = payloadActionIds(actions);
  useEffect(() => {
    const newlyListed = actionIds.filter(actionId => !listed.current.has(actionId));
    listed.current = new Set(actionIds);
    load(newlyListed);
  }, [actionIds.join()]);

  return {
    olderUsages: (actionId: string) => usages[actionId] ?? 0,
    reload: (action: ThreatArsenalAction) => load(payloadActionIds([action])),
  };
};

export default useOlderVersionsInUse;
