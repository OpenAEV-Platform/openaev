import { DATA_DELETE_SUCCESS } from '../../constants/ActionTypes';
import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import { getCurrentTenantId } from '../../utils/url-helper';
import * as schema from '../Schema';

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

export const searchPlayersAsOption = (searchText = '') => {
  const params = { searchText };
  return simpleCall('/api/players/options', { params });
};

export const searchPlayerByIdAsOption = (ids) => {
  return simplePostCall('/api/players/options', ids);
};

export const addUser = data => dispatch => postReferential(schema.user, '/api/users', data)(dispatch);

export const updateUser = (userId, data) => dispatch => putReferential(schema.user, `/api/users/${userId}`, data)(dispatch);

export const deleteUser = userId => dispatch => delReferential(`/api/users/${userId}`, 'users', userId)(dispatch);
// endregion

// region me
export const ME_URI = '/api/me';

export const meTokens = () => dispatch => getReferential(schema.arrayOfTokens, '/api/me/tokens')(dispatch);

// The calls returning the user are tenant-prefixed: '/api/me' is exempt from the automatic tenant
// prefix, and its response would carry the organization of the default tenant, overwriting the one
// of the tenant the user is browsing in the store.
const tenantMeUri = path => `/api/tenants/${getCurrentTenantId()}/me/${path}`;

export const updateMePassword = (currentPassword, newPassword) => dispatch => putReferential(schema.user, tenantMeUri('password'), {
  user_current_password: currentPassword,
  user_plain_password: newPassword,
})(dispatch);

export const updateMeEmail = (currentPassword, newEmail) => dispatch => putReferential(schema.user, tenantMeUri('email'), {
  user_current_password: currentPassword,
  user_email: newEmail,
})(dispatch);

export const updateMeProfile = data => dispatch => putReferential(schema.user, tenantMeUri('profile'), data)(dispatch);

export const updateMeInformation = data => dispatch => putReferential(schema.user, tenantMeUri('information'), data)(dispatch);

export const renewToken = tokenId => dispatch => postReferential(schema.token, '/api/me/token/refresh', { token_id: tokenId })(dispatch)
  .then((data) => {
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
