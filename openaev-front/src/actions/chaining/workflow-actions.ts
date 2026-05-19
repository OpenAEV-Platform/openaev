import type { Dispatch } from 'redux';

import { getReferential, putReferential } from '../../utils/Action';
import type { WorkflowConfigurationInput } from '../../utils/api-types';
import workflowConfigurationSchema from './workflow-schema';

const WORKFLOW_URI = '/api/workflows';

export const fetchWorkflowConfiguration = (workflowId: string) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/configuration`;
  return getReferential(workflowConfigurationSchema(workflowId), uri)(dispatch);
};

export const updateWorkflowConfiguration = (workflowId: string, data: WorkflowConfigurationInput) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/configuration`;
  return putReferential(workflowConfigurationSchema(workflowId), uri, data)(dispatch);
};
