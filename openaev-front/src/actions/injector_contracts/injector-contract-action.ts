import type { Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type InjectorContract, type InjectorContractAddInput,
  type InjectorContractUpdateInput,
  type InjectorContractUpdateMappingInput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import * as schema from '../Schema.js';

const INJECTOR_CONTRACT_URI = '/api/injector_contracts';

// -- CREATE --

export const addInjectorContract = (data: InjectorContractAddInput) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_CONTRACT_URI}`;
  return postReferential(schema.injectorContract, uri, data)(dispatch);
};

// -- READ --

export const fetchInjectorsContracts = () => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_CONTRACT_URI}`;
  return getReferential(schema.arrayOfInjectorContracts, uri)(dispatch);
};

export const fetchInjectorContract = (injectorContractId: InjectorContract['injector_contract_id']) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_CONTRACT_URI}/${injectorContractId}`;
  return getReferential(schema.injectorContract, uri)(dispatch);
};

export const directFetchInjectorContract = (injectorContractId: InjectorContract['injector_contract_id']) => {
  const uri = `${INJECTOR_CONTRACT_URI}/${injectorContractId}`;
  return simpleCall(uri);
};

export const searchInjectorContracts = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${INJECTOR_CONTRACT_URI}/search`;
  return simplePostCall(uri, data);
};

// -- UPDATE --

export const updateInjectorContract = (injectorContractId: InjectorContract['injector_contract_id'], data: InjectorContractUpdateInput) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_CONTRACT_URI}/${injectorContractId}`;
  return putReferential(schema.injectorContract, uri, data)(dispatch);
};

export const updateInjectorContractMapping = (injectorContractId: InjectorContract['injector_contract_id'], data: InjectorContractUpdateMappingInput) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_CONTRACT_URI}/${injectorContractId}/mapping`;
  return putReferential(schema.injectorContract, uri, data)(dispatch);
};

// -- DELETE --

export const deleteInjectorContract = (injectorContractId: InjectorContract['injector_contract_id']) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_CONTRACT_URI}/${injectorContractId}`;
  return delReferential(uri, schema.injectorContract.key, injectorContractId)(dispatch);
};
