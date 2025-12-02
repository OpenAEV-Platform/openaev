import type { Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import { postReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type CustomDashboard, type CustomDashboardInput, type Option, type SearchPaginationInput } from '../../utils/api-types';
import { customDashboard } from '../schemas';

export const CUSTOM_DASHBOARD_URI = '/api/custom-dashboards';

// -- CRUD --

export const createCustomDashboard = (input: CustomDashboardInput) => {
  return simplePostCall<CustomDashboard>(CUSTOM_DASHBOARD_URI, input);
};

export const searchCustomDashboards = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall<Page<CustomDashboard>>(`${CUSTOM_DASHBOARD_URI}/search`, searchPaginationInput);
};

export const fetchCustomDashboard = (id: string) => {
  return simpleCall<CustomDashboard>(`${CUSTOM_DASHBOARD_URI}/${id}`);
};

export const updateCustomDashboard = (id: string, input: CustomDashboardInput) => {
  return simplePutCall<CustomDashboard>(`${CUSTOM_DASHBOARD_URI}/${id}`, input);
};

export const deleteCustomDashboard = (id: string) => {
  return simpleDelCall(`${CUSTOM_DASHBOARD_URI}/${id}`);
};

// -- OPTION --

export const searchCustomDashboardAsOptions = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${CUSTOM_DASHBOARD_URI}/options`, { params });
};

export const searchCustomDashboardByIdAsOptions = (ids: string[]) => {
  return simplePostCall<Option[]>(`${CUSTOM_DASHBOARD_URI}/options`, ids);
};

export const searchCustomDashboardAsOptionsByResourceId = (resourceId: string) => {
  return simpleCall<Option[]>(`${CUSTOM_DASHBOARD_URI}/resource/${resourceId}/options`);
};

// -- EXPORT --
export const exportCustomDashboard = (id: string) => {
  return simpleCall<Blob>(`${CUSTOM_DASHBOARD_URI}/${id}/export`, {
    headers: { Accept: 'application/zip' },
    responseType: 'blob',
  });
};

// -- IMPORT --
export const importCustomDashboard = (content: FormData) => (dispatch: Dispatch) => {
  return postReferential<CustomDashboard>(customDashboard, `${CUSTOM_DASHBOARD_URI}/import`, content)(dispatch);
};
