import type { Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../../utils/Action';
import { type SearchPaginationInput, type TenantInput, type TenantOutput } from '../../utils/api-types';
import { arrayOfTenants, tenant } from './tenant-schema';

export const TENANT_URI = '/api/tenants';

// -- SEARCH --

export const searchTenants = (paginationInput: SearchPaginationInput) => {
  const uri = '/api/tenants/search';
  return simplePostCall(uri, paginationInput);
};

// -- READ --

export const fetchTenant = (tenantId: TenantOutput['tenant_id']) => (dispatch: Dispatch) => {
  const uri = `${TENANT_URI}/${tenantId}`;
  return getReferential(arrayOfTenants, uri)(dispatch);
};

// -- CREATE --

export const addTenant = (data: TenantInput) => (dispatch: Dispatch) => {
  return postReferential(tenant, TENANT_URI, data)(dispatch);
};

// -- UPDATE --

export const updateTenant
  = (tenantId: TenantOutput['tenant_id'], data: TenantInput) =>
    (dispatch: Dispatch) => {
      const uri = `${TENANT_URI}/${tenantId}`;
      return putReferential(tenant, uri, data)(dispatch);
    };

// -- DELETE --

export const deleteTenant
  = (tenantId: TenantOutput['tenant_id']) =>
    (dispatch: Dispatch) => {
      const uri = `${TENANT_URI}/${tenantId}`;
      return delReferential(uri, 'tenants', tenantId)(dispatch);
    };
