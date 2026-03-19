import type { Dispatch } from 'redux';

import { delReferential, postReferential, simplePostCall } from '../../../utils/Action';
import type { SearchPaginationInput, UserInput, UserOutput } from '../../../utils/api-types';
import { tenantUri } from '../../../utils/tenant-uri';
import { tenantUser } from './user-tenant-schema';

export { DEFAULT_TENANT_UUID } from '../../../utils/tenant-uri';

export const addTenantUser = (tenantId: string, data: UserInput) => (dispatch: Dispatch) => {
  return postReferential(tenantUser, tenantUri('/users', tenantId), data)(dispatch);
};

// -- SEARCH --

export const searchTenantUsers = (tenantId: string, paginationInput: SearchPaginationInput) => {
  return simplePostCall(tenantUri('/users/search', tenantId), paginationInput);
};

// -- DELETE --

export const deleteTenantUser = (tenantId: string, userId: UserOutput['user_id']) => (dispatch: Dispatch) => {
  return delReferential(tenantUri(`/users/${userId}`, tenantId), 'users', userId)(dispatch);
};

