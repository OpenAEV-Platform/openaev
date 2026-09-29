import type { Dispatch } from 'redux';

import { delReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../../utils/Action';
import type { RoleInput, RoleOutput, SearchPaginationInput } from '../../../utils/api-types';
import { PLATFORM_ROLE_SCHEMA_KEY, platformRole } from './platform-role-schema';

const PLATFORM_ROLES_URI = '/api/platform-roles';

// -- CREATE --

export const createPlatformRole = (data: RoleInput) => (dispatch: Dispatch) => {
  return postReferential(platformRole, PLATFORM_ROLES_URI, data)(dispatch);
};

// -- READ --

export const fetchPlatformRoleById = (platformRoleId: RoleOutput['role_id']) => {
  return simpleCall(`${PLATFORM_ROLES_URI}/${platformRoleId}`);
};

// -- SEARCH --

export const searchPlatformRoles = (paginationInput: SearchPaginationInput) => {
  const uri = `${PLATFORM_ROLES_URI}/search`;
  return simplePostCall(uri, paginationInput);
};

export const findPlatformRoles = (platformRoleIds: string[]) => {
  const uri = `${PLATFORM_ROLES_URI}/find`;
  return simplePostCall(uri, platformRoleIds);
};

// -- UPDATE --

export const updatePlatformRole
  = (platformRoleId: RoleOutput['role_id'], data: RoleInput) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_ROLES_URI}/${platformRoleId}`;
      return putReferential(platformRole, uri, data)(dispatch);
    };

// -- DELETE --

export const deletePlatformRole
  = (platformRoleId: RoleOutput['role_id']) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_ROLES_URI}/${platformRoleId}`;
      return delReferential(uri, PLATFORM_ROLE_SCHEMA_KEY, platformRoleId)(dispatch);
    };
