import type { Dispatch } from 'redux';

import { delReferential, postReferential, simplePostCall } from '../../../utils/Action';
import type { SearchPaginationInput, UserInput, UserOutput } from '../../../utils/api-types';
import { tenantUser } from './user-tenant-schema';

// TODO: replace with dynamic tenant from context once multi-tenancy frontend is implemented
export const DEFAULT_TENANT_UUID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

const tenantUserUri = (tenantId: string) => `/api/tenants/${tenantId}/users`;

// -- CREATE --

export const addTenantUser = (tenantId: string, data: UserInput) => (dispatch: Dispatch) => {
  return postReferential(tenantUser, tenantUserUri(tenantId), data)(dispatch);
};

// -- SEARCH --

export const searchTenantUsers = (tenantId: string, paginationInput: SearchPaginationInput) => {
  const uri = `${tenantUserUri(tenantId)}/search`;
  return simplePostCall(uri, paginationInput);
};


// -- DELETE --

export const deleteTenantUser = (tenantId: string, userId: UserOutput['user_id']) => (dispatch: Dispatch) => {
  const uri = `${tenantUserUri(tenantId)}/${userId}`;
  return delReferential(uri, 'users', userId)(dispatch);
};

