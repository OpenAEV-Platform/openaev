import { useEffect, useRef, useState } from 'react';

import { fetchThreatArsenalOlderVersionsInUse } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

const payloadActionIds = (actions: ThreatArsenalAction[]) => actions.filter(action => action.action_payload).map(action => action.injector_contract_id);

/** The listed actions whose injects still run an older version: one call for the actions newly listed. */
const useOlderVersionsInUse = (actions: ThreatArsenalAction[]) => {
  const catalog = isFeatureEnabled('CATALOG');
  const [inUse, setInUse] = useState<Set<string>>(new Set());
  const listed = useRef<Set<string>>(new Set());

  const load = (actionIds: string[]) => {
    if (!catalog || actionIds.length === 0) return;
    fetchThreatArsenalOlderVersionsInUse({ action_ids: actionIds }).then(({ data }: { data: string[] }) => setInUse((previous) => {
      const next = new Set([...previous].filter(actionId => !actionIds.includes(actionId)));
      data.forEach(actionId => next.add(actionId));
      return next;
    }));
  };

  const actionIds = payloadActionIds(actions);
  useEffect(() => {
    const newlyListed = actionIds.filter(actionId => !listed.current.has(actionId));
    listed.current = new Set(actionIds);
    load(newlyListed);
  }, [actionIds.join()]);

  return {
    isInUse: (actionId: string) => inUse.has(actionId),
    reload: (action: ThreatArsenalAction) => load(payloadActionIds([action])),
  };
};

export default useOlderVersionsInUse;
