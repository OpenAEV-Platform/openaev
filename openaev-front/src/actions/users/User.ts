import { type Dispatch } from 'redux';

import { DATA_DELETE_SUCCESS } from '../../constants/ActionTypes';
import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type PlayerInput,
  type SearchPaginationInput,
  type Token,
  type UpdateProfileInput,
  type UpdateUserInfoInput,
  type User,
  type UserInput,
} from '../../utils/api-types';
import * as schema from '../Schema';

// region players
export const fetchPlayers = () => (dispatch: Dispatch) => getReferential(schema.arrayOfUsers, '/api/players')(dispatch);

export const addPlayer = (data: PlayerInput) => (dispatch: Dispatch) => postReferential(schema.user, '/api/players', data)(dispatch);

export const updatePlayer = (userId: User['user_id'], data: PlayerInput) => (dispatch: Dispatch) => putReferential(schema.user, `/api/players/${userId}`, data)(dispatch);

export const deletePlayer = (userId: User['user_id']) => (dispatch: Dispatch) => delReferential(`/api/players/${userId}`, 'users', userId)(dispatch);
// endregion

// region users
export const fetchUsers = () => (dispatch: Dispatch) => getReferential(schema.arrayOfUsers, '/api/users')(dispatch);

export const searchUsers = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = '/api/users/search';
  return simplePostCall(uri, data);
};

export const findUsers = (userIds: User['user_id'][]) => {
  const data = userIds;
  const uri = '/api/users/find';
  return simplePostCall(uri, data);
};

export const searchPlayersAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall('/api/players/options', { params });
};

export const searchPlayerByIdAsOption = (ids: User['user_id'][]) => {
  return simplePostCall('/api/players/options', ids);
};

export const addUser = (data: UserInput) => (dispatch: Dispatch) => postReferential(schema.user, '/api/users', data)(dispatch);

export const updateUser = (userId: User['user_id'], data: UserInput) => (dispatch: Dispatch) => putReferential(schema.user, `/api/users/${userId}`, data)(dispatch);

export const deleteUser = (userId: User['user_id']) => (dispatch: Dispatch) => delReferential(`/api/users/${userId}`, 'users', userId)(dispatch);
// endregion

// region me
export const ME_URI = '/api/me';

export const meTokens = () => (dispatch: Dispatch) => getReferential(schema.arrayOfTokens, `${ME_URI}/tokens`)(dispatch);

export const updateMePassword = (currentPassword: string, newPassword: string) => (dispatch: Dispatch) => putReferential(schema.user, `${ME_URI}/password`, {
  user_current_password: currentPassword,
  user_plain_password: newPassword,
})(dispatch);

export const updateMeProfile = (data: UpdateProfileInput, defaultSuccessBehavior: boolean = true) => (dispatch: Dispatch) => putReferential(schema.user, `${ME_URI}/profile`, data, defaultSuccessBehavior)(dispatch);

export const updateMeInformation = (data: UpdateUserInfoInput) => (dispatch: Dispatch) => putReferential(schema.user, `${ME_URI}/information`, data)(dispatch);

export const renewToken = (tokenId: Token['token_id']) => (dispatch: Dispatch) => postReferential(schema.token, `${ME_URI}/token/refresh`, { token_id: tokenId })(dispatch)
  .then((data: Token) => {
    dispatch({
      type: DATA_DELETE_SUCCESS,
      payload: {
        type: 'tokens',
        id: tokenId,
      },
    });
    return data;
  });
// endregion
