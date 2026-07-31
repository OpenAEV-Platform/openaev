export const CHAINING_UPDATED_EVENT = 'openaev:chaining-updated';

export interface ChainingUpdatedDetail { workflowId: string }

export const emitChainingUpdated = (workflowId: string) => {
  window.dispatchEvent(new CustomEvent<ChainingUpdatedDetail>(CHAINING_UPDATED_EVENT, { detail: { workflowId } }));
};
