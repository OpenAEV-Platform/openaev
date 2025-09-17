import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import * as schema from './Schema';

// region players
export const fetchPlayers = () => dispatch => getReferential(schema.arrayOfUsers, '/api/players')(dispatch);

export const addPlayer = data => dispatch => postReferential(schema.user, '/api/players', data)(dispatch);

export const updatePlayer = (userId, data) => dispatch => putReferential(schema.user, `/api/players/${userId}`, data)(dispatch);

export const deletePlayer = userId => dispatch => delReferential(`/api/players/${userId}`, 'users', userId)(dispatch);
// endregion

// region users
export const fetchUsers = () => dispatch => getReferential(schema.arrayOfUsers, '/api/users')(dispatch);

export const searchUsers = (paginationInput) => {
  const data = paginationInput;
  const uri = '/api/users/search';
  return simplePostCall(uri, data);
};

export const findUsers = (userIds) => {
  const data = userIds;
  const uri = '/api/users/find';
  return simplePostCall(uri, data);
};

export const addUser = data => dispatch => postReferential(schema.user, '/api/users', data)(dispatch);

export const updateUserPassword = (userId, data) => dispatch => putReferential(schema.user, `/api/users/${userId}/password`, data)(dispatch);

export const updateUser = (userId, data) => dispatch => putReferential(schema.user, `/api/users/${userId}`, data)(dispatch);

export const deleteUser = userId => dispatch => delReferential(`/api/users/${userId}`, 'users', userId)(dispatch);
// endregion

// region me
export const ME_URI = '/api/me';

export const meTokens = () => dispatch => getReferential(schema.arrayOfTokens, '/api/me/tokens')(dispatch);

export const updateMePassword = (currentPassword, newPassword) => dispatch => putReferential(schema.user, '/api/me/password', {
  user_current_password: currentPassword,
  user_plain_password: newPassword,
})(dispatch);

export const updateMeProfile = data => dispatch => putReferential(schema.user, '/api/me/profile', data)(dispatch);

export const updateMeInformation = data => dispatch => putReferential(schema.user, '/api/me/information', data)(dispatch);

export const renewToken = tokenId => dispatch => postReferential(schema.token, '/api/me/token/refresh', { token_id: tokenId })(dispatch);
// endregion
