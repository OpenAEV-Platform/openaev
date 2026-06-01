import type { Dispatch } from 'redux';

import { getReferential, putReferential, simpleCall } from '../../utils/Action';
import type { WorkflowConfigurationInput } from '../../utils/api-types';
import workflowConfigurationSchema from './workflow-schema';

const WORKFLOW_URI = '/api/workflows';

export interface ScopeAssetOutput {
  asset_id: string;
  asset_name: string;
  asset_type: string;
  asset_external_reference: string;
}

export const fetchWorkflowConfiguration = (workflowId: string) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/configuration`;
  return getReferential(workflowConfigurationSchema(workflowId), uri)(dispatch);
};

export const updateWorkflowConfiguration = (workflowId: string, data: WorkflowConfigurationInput) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/configuration`;
  return putReferential(workflowConfigurationSchema(workflowId), uri, data)(dispatch);
};

export const fetchValidAssets = (workflowId: string): Promise<ScopeAssetOutput[]> => {
  const uri = `${WORKFLOW_URI}/${workflowId}/valid-assets`;
  return simpleCall(uri).then(response => response.data);
};
