import { simpleCall, simplePostCall } from '../../utils/Action';
import type { CustomDashboard, EsAttackPath, EsBase, EsCountInterval, EsSeries, WidgetToEntitiesInput, WidgetToEntitiesOutput } from '../../utils/api-types';

export const SETTINGS_URI = '/api/settings';
export const fetchHomeDashboard = () => {
  return simpleCall<CustomDashboard>(`${SETTINGS_URI}/home-dashboard`);
};

export const homeDashboardCount = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsCountInterval>(`${SETTINGS_URI}/home-dashboard/count/${widgetId}`, parameters);
};

export const homeDashboardSeries = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsSeries[]>(`${SETTINGS_URI}/home-dashboard/series/${widgetId}`, parameters);
};

export const homeDashboardEntities = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsBase[]>(`${SETTINGS_URI}/home-dashboard/entities/${widgetId}`, parameters);
};

export const homeWidgetToEntitiesRuntime = (widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall<WidgetToEntitiesOutput>(`${SETTINGS_URI}/home-dashboard/entities-runtime/${widgetId}`, input);
};
export const homeDashboardAttackPaths = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsAttackPath[]>(`${SETTINGS_URI}/home-dashboard/attack-paths/${widgetId}`, parameters);
};
