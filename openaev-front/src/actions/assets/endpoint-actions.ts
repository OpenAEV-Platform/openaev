import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type AssetBulkProcessingInput, type AssetBulkReplaceMarkingsInput, type AssetUpdateMarkingsInput, type Endpoint, type EndpointInput, type EndpointOutput, type SearchPaginationInput } from '../../utils/api-types';
import { arrayOfEndpoints, endpoint } from './asset-schema';

const ENDPOINT_URI = '/api/endpoints';

export const addEndpointAgentless = (data: EndpointInput) => (dispatch: Dispatch) => {
  const uri = `${ENDPOINT_URI}/agentless`;
  return postReferential(endpoint, uri, data)(dispatch);
};

export const updateEndpoint = (
  assetId: EndpointOutput['asset_id'],
  data: EndpointInput,
) => (dispatch: Dispatch) => {
  const uri = `${ENDPOINT_URI}/${assetId}`;
  return putReferential(endpoint, uri, data)(dispatch);
};

export const deleteEndpoint = (assetId: Endpoint['asset_id']) => (dispatch: Dispatch) => {
  const uri = `${ENDPOINT_URI}/${assetId}`;
  return delReferential(uri, endpoint.key, assetId)(dispatch);
};

export const fetchEndpoints = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfEndpoints, ENDPOINT_URI)(dispatch);
};

export const searchEndpoints = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${ENDPOINT_URI}/search`;
  return simplePostCall(uri, data);
};

// Unified asset inventory: returns every asset type; filters/sorts are limited to base asset fields.
export const searchAssets = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall('/api/assets/search', searchPaginationInput);
};

// Generic delete for the unified inventory, any asset type; security platforms rejected server-side.
export const deleteAsset = (assetId: string) => {
  return simpleDelCall(`/api/assets/${assetId}`);
};

// Replaces an asset's whole marking set; normalized so the response deep-merges, not replaces.
export const updateAssetMarkings = (
  assetId: string,
  data: AssetUpdateMarkingsInput,
  // AssetForm passes false here to avoid a second toast for what feels like one save.
  defaultSuccessBehavior: boolean = true,
) => (dispatch: Dispatch) => {
  const uri = `/api/assets/${assetId}/markings`;
  return putReferential(endpoint, uri, data, defaultSuccessBehavior)(dispatch);
};

// Bulk delete for the unified inventory: explicit ids or select-all-with-exclusions search input.
export const bulkDeleteAssets = (input: AssetBulkProcessingInput) => {
  return simpleDelCall('/api/assets', { data: input });
};

// Bulk mirror of updateAssetMarkings, same scope as bulkDeleteAssets; empty list clears all markings.
export const bulkReplaceAssetMarkings = (input: AssetBulkReplaceMarkingsInput) => {
  return simplePutCall('/api/assets/markings/bulk', input);
};

// Generic asset overview for the unified detail page: any asset type, with its category-specific fields.
export const fetchAssetOverview = (assetId: string) => {
  return simpleCall(`/api/assets/${assetId}`);
};

// "Injects played" for the asset detail page: every inject concerning it, same scope as the posture score.
export const searchInjectsForAsset = (assetId: string, searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`/api/assets/${assetId}/injects/search`, searchPaginationInput);
};

export const findEndpoints = (endpointIds: string[]) => {
  const data = endpointIds;
  const uri = `${ENDPOINT_URI}/find`;
  return simplePostCall(uri, data);
};

export const fetchEndpoint = (endpointId: string) => (dispatch: Dispatch) => {
  const uri = `${ENDPOINT_URI}/${endpointId}`;
  return getReferential(endpoint, uri)(dispatch);
};

export const searchEndpointAsOption = (searchText: string = '', sourceId: string = '', inputFilterOption: string = '') => {
  const params = {
    searchText,
    sourceId,
    inputFilterOption,
  };
  return simpleCall(`${ENDPOINT_URI}/options`, { params });
};

export const searchEndpointByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${ENDPOINT_URI}/options`, ids);
};

export const resolveHostnameToIps = (hostname: string) => {
  return simpleCall(`${ENDPOINT_URI}/resolve`, { params: { hostname } });
};

export const searchEndpointLinkedToFindingsAsOption = (searchText: string = '', sourceId: string = '') => {
  const params = {
    searchText,
    sourceId,
  };
  return simpleCall(`${ENDPOINT_URI}/findings/options`, { params });
};

// Filter options over the whole asset inventory: findings can attach to any asset, so propose them all.
export const searchAssetsAsOption = (searchText: string = '') => {
  return simpleCall('/api/assets/options', { params: { searchText } });
};

export const searchAssetsByIdAsOption = (ids: string[]) => {
  return simplePostCall('/api/assets/options', ids);
};

export const importEndpoints = (file: FormData, csvType: string) => {
  return simplePostCall(`/api/mappers/import/csv?csvType=` + csvType, file);
};

// -- SIMULATIONS --

export const fetchSimulationEndpoints = (simulationId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/endpoints`;
  return getReferential(arrayOfEndpoints, uri)(dispatch);
};

export const findSimulationEndpointsByIds = (simulationId: string, endpointIds: string[]) => {
  const uri = `/api/exercises/${simulationId}/endpoints/find`;
  return simplePostCall(uri, endpointIds);
};

// -- SCENARIOS --

export const fetchScenarioEndpoints = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/endpoints`;
  return getReferential(arrayOfEndpoints, uri)(dispatch);
};

export const findScenarioEndpointsByIds = (simulationId: string, endpointIds: string[]) => {
  const uri = `/api/scenarios/${simulationId}/endpoints/find`;
  return simplePostCall(uri, endpointIds);
};
