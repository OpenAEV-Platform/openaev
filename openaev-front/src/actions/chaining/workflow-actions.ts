import type { Dispatch } from 'redux';

import { getReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import type { ScopeAssetOutput, ScopeTeamOutput, WorkflowConfigurationInput } from '../../utils/api-types';
import { arrayOfAssetGroups } from '../asset_groups/assetgroup-schema';
import { arrayOfEndpoints } from '../assets/asset-schema';
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

export const fetchValidAssets = (workflowId: string): Promise<ScopeAssetOutput[]> => {
  const uri = `${WORKFLOW_URI}/${workflowId}/valid-assets`;
  return simpleCall(uri).then(response => response.data);
};

export const fetchValidTeams = (workflowId: string): Promise<ScopeTeamOutput[]> => {
  const uri = `${WORKFLOW_URI}/${workflowId}/valid-teams`;
  return simpleCall(uri).then(response => response.data);
};

// -- SCOPE INVENTORY --
// Workflow-scoped counterparts of /api/endpoints and /api/asset_groups: they only expose the
// assets referenced by the workflow scope rules, so a user merely granted on the parent
// simulation or scenario can read them without the global asset capabilities.

export const fetchWorkflowScopeEndpoints = (workflowId: string) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/scope/endpoints`;
  return getReferential(arrayOfEndpoints, uri)(dispatch);
};

export const findWorkflowScopeEndpoints = (workflowId: string, endpointIds: string[]) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/scope/endpoints/find`;
  return simplePostCall(uri, endpointIds);
};

export const fetchWorkflowScopeAssetGroups = (workflowId: string) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/scope/asset-groups`;
  return getReferential(arrayOfAssetGroups, uri)(dispatch);
};

export const findWorkflowScopeAssetGroups = (workflowId: string, assetGroupIds: string[]) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/scope/asset-groups/find`;
  return simplePostCall(uri, assetGroupIds);
};

// Injector contract used by one of the workflow's steps. Chaining steps do not persist their
// inject before execution, so this is the only lookup that works for a user granted on the
// parent simulation or scenario without the threat arsenal capabilities.
export const directFetchWorkflowInjectorContract = (workflowId: string, injectorContractId: string) => {
  return simpleCall(`${WORKFLOW_URI}/${workflowId}/injector_contracts/${injectorContractId}`);
};
