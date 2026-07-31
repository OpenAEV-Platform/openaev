import type { UnprovisionedLogicWarningItem } from './logic-warning-utils';

export const CHAINING_UPDATED_EVENT = 'openaev:chaining-updated';

export interface ChainingUpdatedDetail {
  workflowId: string;
  logicWarnings?: UnprovisionedLogicWarningItem[];
}

export const emitChainingUpdated = (workflowId: string, logicWarnings?: UnprovisionedLogicWarningItem[]) => {
  window.dispatchEvent(new CustomEvent<ChainingUpdatedDetail>(CHAINING_UPDATED_EVENT, {
    detail: {
      workflowId,
      logicWarnings,
    },
  }));
};
