import type { Dispatch } from 'redux';

import { getReferential, postReferential, putReferential, simpleCall, simpleDelCall, simplePostCall } from '../../../utils/Action';
import { type SearchPaginationInput, type TenantInput, type TenantOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { TENANT_URI } from '../../../utils/url-helper';
import { arrayOfTenants, tenant } from './tenant-schema';

// -- CREATE --

export const addTenant = (data: TenantInput) => (dispatch: Dispatch) => {
  return postReferential(tenant, TENANT_URI, data)(dispatch);
};

// -- READ --

export const fetchTenants = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfTenants, TENANT_URI)(dispatch);
};

// -- SEARCH --

export const searchTenants = (paginationInput: SearchPaginationInput) => {
  const uri = '/api/tenants/search';
  return simplePostCall(uri, paginationInput);
};

// -- UPDATE --

export const updateTenant
  = (tenantId: TenantOutput['tenant_id'], data: TenantInput) =>
    (dispatch: Dispatch) => {
      const uri = `${TENANT_URI}/${tenantId}`;
      return putReferential(tenant, uri, data)(dispatch);
    };

// -- SOFT DELETE --

export const softDeleteTenant = (tenantId: TenantOutput['tenant_id']) => {
  const uri = `${TENANT_URI}/${tenantId}`;
  return simpleDelCall(uri, undefined, true, false)
    .then((response) => {
      MESSAGING$.notifySuccess('The tenant has been successfully deactivated.');
      return response;
    });
};

// -- REACTIVATE --

export const reactivateTenant = (tenantId: TenantOutput['tenant_id']) => {
  const uri = `${TENANT_URI}/${tenantId}/reactivate`;
  return simplePostCall(uri, {})
    .then((response) => {
      MESSAGING$.notifySuccess('The tenant has been successfully reactivated.');
      return response;
    });
};

// -- OPTIONS --

export const searchTenantAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${TENANT_URI}/options`, { params });
};

export const searchTenantByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${TENANT_URI}/options`, ids);
};
