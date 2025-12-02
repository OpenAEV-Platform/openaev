import { delSubResourceReferential, postReferential } from '../utils/Action';
import { group } from './schemas';

export const addGrant = (groupId, data) => (dispatch) => {
  const uri = `/api/groups/${groupId}/grants`;
  return postReferential(group, uri, data)(dispatch);
};

export const deleteGrant = (groupId, grantId) => (dispatch) => {
  const uri = `/api/groups/${groupId}/grants/${grantId}`;
  return delSubResourceReferential(group, uri)(dispatch);
};
