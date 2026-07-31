import { useEffect, useState } from 'react';

import { fetchConditions, fetchSteps } from '../../../../actions/chaining/chaining-actions';
import {
  buildActionMetas,
  buildEventData,
  buildOutputProvidersMap,
  enrichActionMetasWithContracts,
} from '../../chaining/logic/logic-flow-helpers';
import {
  findUnprovisionedLogicWarningItems,
  type UnprovisionedLogicWarningItem,
} from '../../chaining/logic/logic-warning-utils';

const useUnprovisionedLogicWarnings = (workflowId: string | undefined): UnprovisionedLogicWarningItem[] => {
  const [warnings, setWarnings] = useState<UnprovisionedLogicWarningItem[]>([]);

  useEffect(() => {
    if (!workflowId) {
      setWarnings([]);
      return undefined;
    }

    let stale = false;
    const loadWarnings = async () => {
      try {
        const [stepsRes, conditionsRes] = await Promise.all([
          fetchSteps(workflowId),
          fetchConditions(workflowId),
        ]);
        const actionMetas = buildActionMetas(stepsRes.data ?? []);
        const enrichedActionMetas = await enrichActionMetasWithContracts(actionMetas);
        const providers = buildOutputProvidersMap(enrichedActionMetas);
        const { eventMetas } = buildEventData(conditionsRes.data ?? []);
        if (!stale) {
          setWarnings(findUnprovisionedLogicWarningItems(eventMetas, providers));
        }
      } catch {
        if (!stale) {
          setWarnings([]);
        }
      }
    };

    void loadWarnings();
    return () => {
      stale = true;
    };
  }, [workflowId]);

  return warnings;
};

export default useUnprovisionedLogicWarnings;
