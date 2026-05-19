import type { Dispatch } from 'redux';

import { delReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../../utils/Action';
import type { PlatformRoleInput, PlatformRoleOutput, SearchPaginationInput } from '../../../utils/api-types';
import { PLATFORM_ROLE_SCHEMA_KEY, platformRole } from './platform-role-schema';

export const PLATFORM_ROLES_URI = '/api/platform-roles';

// -- CREATE --

export const addPlatformRole = (data: PlatformRoleInput) => (dispatch: Dispatch) => {
  return postReferential(platformRole, PLATFORM_ROLES_URI, data)(dispatch);
};

// -- READ --

export const fetchPlatformRoleCapabilities = (platformRoleId: string) => {
  return simpleCall(`${PLATFORM_ROLES_URI}/${platformRoleId}/capabilities`);
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
  = (platformRoleId: PlatformRoleOutput['platform_role_id'], data: PlatformRoleInput) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_ROLES_URI}/${platformRoleId}`;
      return putReferential(platformRole, uri, data)(dispatch);
    };

// -- DELETE --

export const deletePlatformRole
  = (platformRoleId: PlatformRoleOutput['platform_role_id']) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_ROLES_URI}/${platformRoleId}`;
      return delReferential(uri, PLATFORM_ROLE_SCHEMA_KEY, platformRoleId)(dispatch);
    };
