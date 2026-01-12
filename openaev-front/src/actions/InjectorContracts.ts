import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../utils/Action';
import {
  type InjectorContract,
  type InjectorContractAddInput,
  type InjectorContractUpdateInput,
  type InjectorContractUpdateMappingInput,
  type SearchPaginationInput,
} from '../utils/api-types';
import * as schema from './Schema';

const INJECTOR_CONTRACT_URI = '/api/injector_contracts';

export const fetchInjectorContract = (injectorContractId: InjectorContract['injector_contract_id']) => (dispatch: Dispatch) => {
  return getReferential(schema.injectorContract, `${INJECTOR_CONTRACT_URI}/${injectorContractId}`)(dispatch);
};

export const directFetchInjectorContract = (injectorContractId: InjectorContract['injector_contract_id']) => {
  return simpleCall(`${INJECTOR_CONTRACT_URI}/${injectorContractId}`);
};

export const fetchInjectorsContracts = () => (dispatch: Dispatch) => {
  return getReferential(schema.arrayOfInjectorContracts, `${INJECTOR_CONTRACT_URI}`)(dispatch);
};

export const searchInjectorContracts = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${INJECTOR_CONTRACT_URI}/search`;
  return simplePostCall(uri, data);
};

export const updateInjectorContract = (injectorContractId: InjectorContract['injector_contract_id'], data: InjectorContractUpdateInput) => (dispatch: Dispatch) => {
  return putReferential(schema.injectorContract, `${INJECTOR_CONTRACT_URI}/${injectorContractId}`, data)(dispatch);
};

export const updateInjectorContractMapping = (injectorContractId: InjectorContract['injector_contract_id'], data: InjectorContractUpdateMappingInput) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_CONTRACT_URI}/${injectorContractId}/mapping`;
  return putReferential(schema.injectorContract, uri, data)(dispatch);
};

export const addInjectorContract = (data: InjectorContractAddInput) => (dispatch: Dispatch) => {
  return postReferential(schema.injectorContract, `${INJECTOR_CONTRACT_URI}`, data)(dispatch);
};

// This action must use InjectorContractSearchPaginationInput to stay
// synchronized with the search route filters
export const fetchDomainCounts = (data: SearchPaginationInput) => {
  const uri = `${INJECTOR_CONTRACT_URI}/domain-counts`;
  return simplePostCall(uri, data);
};

export const deleteInjectorContract = (injectorContractId: InjectorContract['injector_contract_id']) => (dispatch: Dispatch) => {
  return delReferential(`${INJECTOR_CONTRACT_URI}/${injectorContractId}`, 'injectorcontracts', injectorContractId)(dispatch);
};
