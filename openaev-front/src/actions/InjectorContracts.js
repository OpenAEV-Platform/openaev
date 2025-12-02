import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../utils/Action';
import { arrayOfInjectorContracts, injectorContract } from './schemas';

export const fetchInjectorContract = injectorContractId => (dispatch) => {
  const uri = `/api/injector_contracts/${injectorContractId}`;
  return getReferential(injectorContract, uri)(dispatch);
};

export const directFetchInjectorContract = (injectorContractId) => {
  const uri = `/api/injector_contracts/${injectorContractId}`;
  return simpleCall(uri);
};

export const fetchInjectorsContracts = () => (dispatch) => {
  const uri = '/api/injector_contracts';
  return getReferential(arrayOfInjectorContracts, uri)(dispatch);
};

export const searchInjectorContracts = (paginationInput) => {
  const data = paginationInput;
  const uri = '/api/injector_contracts/search';
  return simplePostCall(uri, data);
};

export const updateInjectorContract = (injectorContractId, data) => (dispatch) => {
  const uri = `/api/injector_contracts/${injectorContractId}`;
  return putReferential(injectorContract, uri, data)(dispatch);
};

export const updateInjectorContractMapping = (injectorContractId, data) => (dispatch) => {
  const uri = `/api/injector_contracts/${injectorContractId}/mapping`;
  return putReferential(injectorContract, uri, data)(dispatch);
};

export const addInjectorContract = data => (dispatch) => {
  const uri = '/api/injector_contracts';
  return postReferential(injectorContract, uri, data)(dispatch);
};

export const deleteInjectorContract = injectorContractId => (dispatch) => {
  const uri = `/api/injector_contracts/${injectorContractId}`;
  return delReferential(uri, 'injectorcontracts', injectorContractId)(dispatch);
};
