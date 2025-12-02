import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import { arrayOfGroups, group } from './schemas';

export const fetchGroups = () => dispatch => getReferential(arrayOfGroups, '/api/groups')(dispatch);

export const fetchGroup = groupId => dispatch => getReferential(group, `/api/groups/${groupId}`)(dispatch);

export const searchGroups = (paginationInput) => {
  const data = paginationInput;
  const uri = '/api/groups/search';
  return simplePostCall(uri, data);
};

export const addGroup = data => dispatch => postReferential(group, '/api/groups', data)(dispatch);

export const updateGroupInformation = (groupId, data) => dispatch => putReferential(
  group,
  `/api/groups/${groupId}/information`,
  data,
)(dispatch);

export const updateGroupUsers = (groupId, data) => dispatch => putReferential(group, `/api/groups/${groupId}/users`, data)(dispatch);

export const deleteGroup = groupId => dispatch => delReferential(`/api/groups/${groupId}`, 'groups', groupId)(dispatch);

export const updateGroupRoles = (groupId, data) => dispatch => putReferential(group, `/api/groups/${groupId}/roles`, data)(dispatch);
