import { type Dispatch } from 'redux';

import { getReferential, simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { Pagination, TenantSettingsUpdateInput, WidgetToEntitiesInput } from '../../utils/api-types';
import * as schema from '../Schema';

const TENANT_SETTINGS_URI = '/api/tenant-settings';

type AppDispatch = Dispatch;

export type TenantSettings = { platform_home_dashboard?: string };

export const fetchTenantSettings = () => (dispatch: AppDispatch) => {
  return getReferential(schema.tenantSettings, TENANT_SETTINGS_URI)(dispatch);
};

export const updateTenantSettings = (data: TenantSettingsUpdateInput) => {
  return simplePutCall(TENANT_SETTINGS_URI, data);
};

// -- HOME DASHBOARD --

export const fetchTenantHomeDashboard = () => {
  return simpleCall(`${TENANT_SETTINGS_URI}/home-dashboard`);
};

export const tenantHomeDashboardCount = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${TENANT_SETTINGS_URI}/home-dashboard/count/${widgetId}`, parameters);
};

export const tenantHomeDashboardAverage = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${TENANT_SETTINGS_URI}/home-dashboard/average/${widgetId}`, parameters);
};

export const tenantHomeDashboardSeries = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${TENANT_SETTINGS_URI}/home-dashboard/series/${widgetId}`, parameters);
};

export const tenantHomeDashboardEntities = (widgetId: string, parameters: Record<string, string | undefined>, pagination?: Pagination) => {
  return simplePostCall(`${TENANT_SETTINGS_URI}/home-dashboard/entities/${widgetId}`, {
    parameters,
    pagination,
  });
};

export const tenantHomeWidgetToEntitiesRuntime = (widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall(`${TENANT_SETTINGS_URI}/home-dashboard/entities-runtime/${widgetId}`, input);
};

export const tenantHomeDashboardAttackPaths = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${TENANT_SETTINGS_URI}/home-dashboard/attack-paths/${widgetId}`, parameters);
};
