import { type Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import { type Endpoint, type EndpointInput, type EndpointOutput, type EndpointOverviewOutput, type Option, type SearchPaginationInput } from '../../utils/api-types';
import { arrayOfEndpoints, endpoint } from '../schemas';

const ENDPOINT_URI = '/api/endpoints';

export const addEndpointAgentless = (data: EndpointInput) => (dispatch: Dispatch) => {
  const uri = `${ENDPOINT_URI}/agentless`;
  return postReferential<Endpoint>(endpoint, uri, data)(dispatch);
};

export const updateEndpoint = (
  assetId: EndpointOutput['asset_id'],
  data: EndpointInput,
) => (dispatch: Dispatch) => {
  const uri = `${ENDPOINT_URI}/${assetId}`;
  return putReferential<Endpoint>(endpoint, uri, data)(dispatch);
};

export const deleteEndpoint = (assetId: Endpoint['asset_id']) => (dispatch: Dispatch) => {
  const uri = `${ENDPOINT_URI}/${assetId}`;
  return delReferential(uri, endpoint.key, assetId)(dispatch);
};

export const fetchEndpoints = () => (dispatch: Dispatch) => {
  return getReferential<Endpoint[]>(arrayOfEndpoints, ENDPOINT_URI)(dispatch);
};

export const searchEndpoints = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${ENDPOINT_URI}/search`;
  return simplePostCall<Page<EndpointOutput>>(uri, data);
};

export const findEndpoints = (endpointIds: string[]) => {
  const data = endpointIds;
  const uri = `${ENDPOINT_URI}/find`;
  return simplePostCall<Endpoint[]>(uri, data);
};

export const fetchEndpoint = (endpointId: string) => (dispatch: Dispatch) => {
  const uri = `/api/endpoints/${endpointId}`;
  return getReferential<EndpointOverviewOutput>(endpoint, uri)(dispatch);
};

export const searchEndpointAsOption = (searchText: string = '', sourceId: string = '', inputFilterOption: string = '') => {
  const params = {
    searchText,
    sourceId,
    inputFilterOption,
  };
  return simpleCall<Option[]>(`${ENDPOINT_URI}/options`, { params });
};

export const searchEndpointByIdAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${ENDPOINT_URI}/options`, ids);
};

export const searchEndpointLinkedToFindingsAsOption = (searchText: string = '', sourceId: string = '') => {
  const params = {
    searchText,
    sourceId,
  };
  return simpleCall<Option[]>(`${ENDPOINT_URI}/findings/options`, { params });
};

export const importEndpoints = (file: FormData, targetType: string) => {
  return simplePostCall<void>(`/api/mappers/import/csv?targetType=` + targetType, file);
};

// -- SIMULATIONS --

export const fetchSimulationEndpoints = (simulationId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/endpoints`;
  return getReferential<Endpoint[]>(arrayOfEndpoints, uri)(dispatch);
};

export const findSimulationEndpointsByIds = (simulationId: string, endpointIds: string[]) => {
  const uri = `/api/exercises/${simulationId}/endpoints/find`;
  return simplePostCall<EndpointOutput[]>(uri, endpointIds);
};

// -- SCENARIOS --

export const fetchScenarioEndpoints = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/endpoints`;
  return getReferential<Endpoint[]>(arrayOfEndpoints, uri)(dispatch);
};

export const findScenarioEndpointsByIds = (simulationId: string, endpointIds: string[]) => {
  const uri = `/api/scenarios/${simulationId}/endpoints/find`;
  return simplePostCall<EndpointOutput[]>(uri, endpointIds);
};
