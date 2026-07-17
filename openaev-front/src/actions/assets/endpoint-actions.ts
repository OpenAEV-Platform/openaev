import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type Endpoint, type EndpointInput, type EndpointOutput, type SearchPaginationInput } from '../../utils/api-types';
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

// Unified asset inventory: returns EVERY asset type (endpoints, AI targets, identities, cloud /
// web / network / generic). Endpoints keep their agents/platform; other types list with those
// empty. Filters/sorts must reference base asset fields (no endpoint-only platform/arch facets).
export const searchAssets = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall('/api/assets/search', searchPaginationInput);
};

// Generic delete for the unified inventory: removes any asset type (endpoint, AI target, or any
// other category) by id. Security platforms are rejected server-side (managed in their own area).
export const deleteAsset = (assetId: string) => {
  return simpleDelCall(`/api/assets/${assetId}`);
};

// Generic asset overview for the unified detail page: returns any asset type with its
// category-relevant fields (endpoints keep agents/platform; AI targets expose connection metadata).
export const fetchAssetOverview = (assetId: string) => {
  return simpleCall(`/api/assets/${assetId}`);
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
