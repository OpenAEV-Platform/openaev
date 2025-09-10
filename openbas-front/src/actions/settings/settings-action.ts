import { simpleCall, simplePostCall } from '../../utils/Action';

export const SETTINGS_URI = '/api/settings';
export const fetchHomeDashboard = () => {
  return simpleCall(`${SETTINGS_URI}/home_dashboard`);
};

export const homeDashboardCount = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${SETTINGS_URI}/home_dashboard/count/${widgetId}`, parameters);
};

export const homeDashboardSeries = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${SETTINGS_URI}/home_dashboard/series/${widgetId}`, parameters);
};

export const homeDashboardEntities = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${SETTINGS_URI}/home_dashboard/entities/${widgetId}`, parameters);
};

export const homeDashboardAttackPaths = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${SETTINGS_URI}/home_dashboard/attack-paths/${widgetId}`, parameters);
};
