import { useState } from 'react';

import { fetchThreatArsenalOlderVersionsInUse } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

/** The listed actions whose injects still run an older version: one call per page listed. */
const useOlderVersionsInUse = () => {
  const catalog = isFeatureEnabled('CATALOG');
  const [inUse, setInUse] = useState<Set<string>>(new Set());

  const load = (actions: ThreatArsenalAction[]) => {
    const actionIds = actions.filter(action => action.action_payload).map(action => action.injector_contract_id);
    if (!catalog || actionIds.length === 0) return;
    fetchThreatArsenalOlderVersionsInUse({ action_ids: actionIds }).then(({ data }: { data: string[] }) => setInUse((previous) => {
      const next = new Set([...previous].filter(actionId => !actionIds.includes(actionId)));
      data.forEach(actionId => next.add(actionId));
      return next;
    }));
  };

  return {
    isInUse: (actionId: string) => inUse.has(actionId),
    load,
  };
};

export default useOlderVersionsInUse;
