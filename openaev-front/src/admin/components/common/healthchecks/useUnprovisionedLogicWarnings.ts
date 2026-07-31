import { useEffect, useState } from 'react';

import { fetchConditions, fetchSteps } from '../../../../actions/chaining/chaining-actions';
import { CHAINING_UPDATED_EVENT, type ChainingUpdatedDetail } from '../../chaining/logic/chaining-refresh-events';
import {
  buildActionMetas,
  buildEventData,
  buildOutputProvidersMap,
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
        const providers = buildOutputProvidersMap(actionMetas);
        const { eventMetas } = buildEventData(conditionsRes.data ?? []);
        if (!stale) {
          setWarnings(findUnprovisionedLogicWarningItems(eventMetas, providers));
        }
      } catch {
        // Keep previous warnings on transient request failures.
      }
    };

    void loadWarnings();
    const onChainingUpdated = (event: Event) => {
      const customEvent = event as CustomEvent<ChainingUpdatedDetail>;
      if (customEvent.detail?.workflowId === workflowId) {
        if (customEvent.detail.logicWarnings) {
          setWarnings(customEvent.detail.logicWarnings);
        } else {
          void loadWarnings();
        }
      }
    };
    window.addEventListener(CHAINING_UPDATED_EVENT, onChainingUpdated as EventListener);
    return () => {
      stale = true;
      window.removeEventListener(CHAINING_UPDATED_EVENT, onChainingUpdated as EventListener);
    };
  }, [workflowId]);

  return warnings;
};

export default useUnprovisionedLogicWarnings;
