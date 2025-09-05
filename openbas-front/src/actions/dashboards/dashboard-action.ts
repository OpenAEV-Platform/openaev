import { simplePostCall } from '../../utils/Action';
import { type ListConfiguration } from '../../utils/api-types-custom';

export const DASHBOARD_URI = '/api/dashboards';

export const count = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/count/${widgetId}`, parameters);
};

export const series = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/series/${widgetId}`, parameters);
};

export const entities = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/entities/${widgetId}`, parameters);
};

export const entitiesWithNoWidget = (customDashboardId: string, parameters: Record<string, string>, widgetConfiguration: ListConfiguration) => {
  return simplePostCall(`${DASHBOARD_URI}/entities/widgetless`, {
    customDashboardId,
    parameters,
    widgetConfiguration,
  });
};

export const attackPaths = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/attack-paths/${widgetId}`, parameters);
};
