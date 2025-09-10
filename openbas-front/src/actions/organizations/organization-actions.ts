import { simpleCall, simplePostCall } from '../../utils/Action';
import type { SearchPaginationInput } from '../../utils/api-types';

const ORGANIZATION_URI = '/api/organizations';

export const searchOrganizations = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${ORGANIZATION_URI}/search`;
  return simplePostCall(uri, data);
};

export const searchOrganizationsByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${ORGANIZATION_URI}/options`, { params });
};

export const searchOrganizationByIdAsOptions = (ids: string[]) => {
  return simplePostCall(`${ORGANIZATION_URI}/options`, ids);
};
