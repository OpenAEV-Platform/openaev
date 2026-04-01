import type { Dispatch } from 'redux';

import { postReferential, putReferential, simpleDelCall, simplePostCall } from '../../../utils/Action';
import { type SearchPaginationInput, type TenantInput, type TenantOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { tenant } from './tenant-schema';

export const TENANT_URI = '/api/tenants';

// -- CREATE --

export const addTenant = (data: TenantInput) => (dispatch: Dispatch) => {
  return postReferential(tenant, TENANT_URI, data)(dispatch);
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
