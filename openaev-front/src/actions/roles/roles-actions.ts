import type { Dispatch } from 'redux';

import { delReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import { type RoleInput, type RoleOutput, type SearchPaginationInput } from '../../utils/api-types';
import { role, ROLE_SCHEMA_KEY } from './role-schema';

const ROLES_URI = '/api/roles';

// -- CREATE --

export const createRole = (data: RoleInput) => (dispatch: Dispatch) => {
  const uri = `${ROLES_URI}`;
  return postReferential(role, uri, data)(dispatch);
};

// -- READ --

export const findRoles = (roleIds: RoleOutput['role_id'][]) =>
  // No tenant /find endpoint: the list is fetched once and indexed client-side.
  simpleCall(ROLES_URI).then((result: { data: RoleOutput[] }) => {
    const rolesById = new Map((result.data ?? []).map(role => [role.role_id, role]));
    return roleIds
      .map(roleId => rolesById.get(roleId))
      .filter((role): role is RoleOutput => role !== undefined);
  });

// -- SEARCH --

export const searchRoles = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${ROLES_URI}/search`;
  return simplePostCall(uri, data);
};

// -- UPDATE --

export const updateRole = (roleId: RoleOutput['role_id'], data: RoleInput) => (dispatch: Dispatch) => {
  const uri = `${ROLES_URI}/${roleId}`;
  return putReferential(role, uri, data)(dispatch);
};

// -- DELETE --

export const deleteRole = (roleId: RoleOutput['role_id']) => (dispatch: Dispatch) => {
  const uri = `${ROLES_URI}/${roleId}`;
  return delReferential(uri, ROLE_SCHEMA_KEY, roleId)(dispatch);
};
