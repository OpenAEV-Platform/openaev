import { delSubResourceReferential, postReferential } from '../utils/Action';
import * as schema from './Schema';

export const addGrant = (groupId, data) => (dispatch) => {
  const uri = `/api/groups/${groupId}/grants`;
  return postReferential(schema.group, uri, data)(dispatch);
};

export const deleteGrant = (groupId, grantId) => (dispatch) => {
  const uri = `/api/groups/${groupId}/grants/${grantId}`;
  return delSubResourceReferential(schema.group, uri)(dispatch);
};
