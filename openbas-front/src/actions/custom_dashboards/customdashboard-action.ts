import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type CustomDashboardInput, type SearchPaginationInput } from '../../utils/api-types';

export const CUSTOM_DASHBOARD_URI = '/api/custom-dashboards';

// -- CRUD --

export const createCustomDashboard = (input: CustomDashboardInput) => {
  return simplePostCall(CUSTOM_DASHBOARD_URI, input);
};

export const searchCustomDashboards = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${CUSTOM_DASHBOARD_URI}/search`, searchPaginationInput);
};

export const fetchCustomDashboard = (id: string) => {
  return simpleCall(`${CUSTOM_DASHBOARD_URI}/${id}`);
};

export const updateCustomDashboard = (id: string, input: CustomDashboardInput) => {
  return simplePutCall(`${CUSTOM_DASHBOARD_URI}/${id}`, input);
};

export const deleteCustomDashboard = (id: string) => {
  return simpleDelCall(`${CUSTOM_DASHBOARD_URI}/${id}`);
};

// -- OPTION --

export const searchCustomDashboardAsOptions = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${CUSTOM_DASHBOARD_URI}/options`, { params });
};

export const searchCustomDashboardByIdAsOptions = (ids: string[]) => {
  return simplePostCall(`${CUSTOM_DASHBOARD_URI}/options`, ids);
};
