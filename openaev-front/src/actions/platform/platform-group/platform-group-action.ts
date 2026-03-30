import type { Dispatch } from 'redux';

import {
  delReferential,
  postReferential,
  putReferential,
  simpleCall,
  simplePostCall,
  simplePutCall,
} from '../../../utils/Action';
import type { PlatformGroupInput, PlatformGroupOutput, SearchPaginationInput } from '../../../utils/api-types';
import { PLATFORM_GROUP_SCHEMA_KEY, platformGroup } from './platform-group-schema';

export const PLATFORM_GROUPS_URI = '/api/platform-groups';

// -- CREATE --

export const addPlatformGroup = (data: PlatformGroupInput) => (dispatch: Dispatch) => {
  return postReferential(platformGroup, PLATFORM_GROUPS_URI, data)(dispatch);
};

// -- READ --

export const fetchPlatformGroupUserIds = (platformGroupId: PlatformGroupOutput['platform_group_id']) => {
  return simpleCall(`${PLATFORM_GROUPS_URI}/${platformGroupId}/users`);
};

export const fetchPlatformGroupRoleIds = (platformGroupId: PlatformGroupOutput['platform_group_id']) => {
  return simpleCall(`${PLATFORM_GROUPS_URI}/${platformGroupId}/platform-roles`);
};

// -- SEARCH --

export const searchPlatformGroups = (paginationInput: SearchPaginationInput) => {
  const uri = `${PLATFORM_GROUPS_URI}/search`;
  return simplePostCall(uri, paginationInput);
};

// -- UPDATE --

export const updatePlatformGroup
  = (platformGroupId: PlatformGroupOutput['platform_group_id'], data: PlatformGroupInput) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_GROUPS_URI}/${platformGroupId}`;
      return putReferential(platformGroup, uri, data)(dispatch);
    };

export const updatePlatformGroupUsers
  = (platformGroupId: PlatformGroupOutput['platform_group_id'], data: { platform_group_users: string[] }) => {
    const uri = `${PLATFORM_GROUPS_URI}/${platformGroupId}/users`;
    return simplePutCall(uri, data);
  };

export const updatePlatformGroupRoles
  = (platformGroupId: PlatformGroupOutput['platform_group_id'], data: { platform_group_platform_roles: string[] }) => {
    const uri = `${PLATFORM_GROUPS_URI}/${platformGroupId}/platform-roles`;
    return simplePutCall(uri, data);
  };

// -- DELETE --

export const deletePlatformGroup
  = (platformGroupId: PlatformGroupOutput['platform_group_id']) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_GROUPS_URI}/${platformGroupId}`;
      return delReferential(uri, PLATFORM_GROUP_SCHEMA_KEY, platformGroupId)(dispatch);
    };
