import { delReferential, getReferential, postReferential, putReferential } from '../utils/Action';
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
