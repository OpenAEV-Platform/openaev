import { delReferential, getReferential, postReferential, putReferential } from '../utils/Action';
import { arrayOfOrganizations, organization } from './schemas';

export const fetchOrganizations = () => dispatch => getReferential(arrayOfOrganizations, '/api/organizations')(dispatch);

export const addOrganization = data => dispatch => postReferential(organization, '/api/organizations', data)(dispatch);

export const updateOrganization = (organizationId, data) => dispatch => putReferential(
  organization,
  `/api/organizations/${organizationId}`,
  data,
)(dispatch);

export const deleteOrganization = organizationId => dispatch => delReferential(
  `/api/organizations/${organizationId}`,
  'organizations',
  organizationId,
)(dispatch);
