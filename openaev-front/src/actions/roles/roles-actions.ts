import type { Dispatch } from 'redux';

import type { RoleCreateInput } from '../../admin/components/settings/roles/RoleForm';
import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../../utils/Action';
import { type RoleOutput, type SearchPaginationInput } from '../../utils/api-types';
import * as schema from '../Schema';

const ROLES_URI = '/api/roles';
export const searchRoles = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${ROLES_URI}/search`;
  return simplePostCall(uri, data);
};

export const fetchRoles = () => (dispatch: Dispatch): Promise<RoleOutput[]> => {
  const uri = `${ROLES_URI}`;
  return getReferential(schema.arrayOfRoles, uri)(dispatch);
};

export const deleteRole = (roleId: RoleOutput['role_id']) => (dispatch: Dispatch) => {
  const uri = `${ROLES_URI}/${roleId}`;
  return delReferential(uri, 'roles', roleId)(dispatch);
};

export const createRole = (data: RoleCreateInput) => (dispatch: Dispatch) => {
  const uri = `${ROLES_URI}`;
  return postReferential(schema.role, uri, data)(dispatch);
};

export const updateRole = (roleId: RoleOutput['role_id'], data: RoleCreateInput) => (dispatch: Dispatch) => {
  const uri = `${ROLES_URI}/${roleId}`;
  return putReferential(schema.role, uri, data)(dispatch);
};
