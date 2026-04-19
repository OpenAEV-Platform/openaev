import type { Dispatch } from 'redux';

import { delReferential, postReferential, putReferential, simplePostCall } from '../../../utils/Action';
import { type SearchPaginationInput, type UserInput, type UserOutput } from '../../../utils/api-types';
import { PLATFORM_USER_SCHEMA_KEY, platformUser } from './platform-user-schema';

export const PLATFORM_USERS_URI = '/api/platform-users';

// -- CREATE --

export const addPlatformUser = (data: UserInput) => (dispatch: Dispatch) => {
  return postReferential(platformUser, PLATFORM_USERS_URI, data)(dispatch);
};

// -- SEARCH --

export const searchPlatformUsers = (paginationInput: SearchPaginationInput) => {
  const uri = `${PLATFORM_USERS_URI}/search`;
  return simplePostCall(uri, paginationInput);
};

// -- UPDATE --

export const updatePlatformUser
  = (userId: UserOutput['user_id'], data: UserInput) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_USERS_URI}/${userId}`;
      return putReferential(platformUser, uri, data)(dispatch);
    };

// -- DELETE --

export const deletePlatformUser
  = (userId: UserOutput['user_id']) =>
    (dispatch: Dispatch) => {
      const uri = `${PLATFORM_USERS_URI}/${userId}`;
      return delReferential(uri, PLATFORM_USER_SCHEMA_KEY, userId)(dispatch);
    };
