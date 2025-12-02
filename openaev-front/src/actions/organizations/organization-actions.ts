import { type Page } from '../../components/common/queryable/Page';
import { simpleCall, simplePostCall } from '../../utils/Action';
import type { Option, Organization, SearchPaginationInput } from '../../utils/api-types';

const ORGANIZATION_URI = '/api/organizations';

export const searchOrganizations = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${ORGANIZATION_URI}/search`;
  return simplePostCall<Page<Organization>>(uri, data);
};

export const searchOrganizationsByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${ORGANIZATION_URI}/options`, { params });
};

export const searchOrganizationByIdAsOptions = (ids: string[]) => {
  return simplePostCall<Option[]>(`${ORGANIZATION_URI}/options`, ids);
};
