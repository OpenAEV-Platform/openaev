import { DATA_DELETE_BATCH_SUCCESS } from '../constants/ActionTypes';
import { delReferential, getReferential, postReferential, putReferential, simpleDelCall } from '../utils/Action';
import * as schema from './Schema';

export const fetchOrganizations = () => dispatch => getReferential(schema.arrayOfOrganizations, '/api/organizations')(dispatch);

export const addOrganization = data => dispatch => postReferential(schema.organization, '/api/organizations', data)(dispatch);

export const updateOrganization = (organizationId, data) => dispatch => putReferential(
  schema.organization,
  `/api/organizations/${organizationId}`,
  data,
)(dispatch);

export const deleteOrganization = organizationId => dispatch => delReferential(
  `/api/organizations/${organizationId}`,
  'organizations',
  organizationId,
)(dispatch);

export const bulkDeleteOrganizations = input => dispatch => simpleDelCall('/api/organizations', { data: input }).then((response) => {
  const deletedIds = response.data ?? [];
  // Drop the deleted organizations from the referential store in a single pass
  dispatch({
    type: DATA_DELETE_BATCH_SUCCESS,
    payload: deletedIds.map(id => ({
      id,
      type: 'organizations',
    })),
  });
  return response;
});
