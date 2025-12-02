import { simplePostCall } from '../../utils/Action';
import { type EsAttackPath, type EsBase, type EsCountInterval, type EsSeries, type WidgetToEntitiesInput, type WidgetToEntitiesOutput } from '../../utils/api-types';

export const DASHBOARD_URI = '/api/dashboards';

export const count = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsCountInterval>(`${DASHBOARD_URI}/count/${widgetId}`, parameters);
};

export const series = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsSeries[]>(`${DASHBOARD_URI}/series/${widgetId}`, parameters);
};

export const entities = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsBase[]>(`${DASHBOARD_URI}/entities/${widgetId}`, parameters);
};

export const widgetToEntitiesRuntime = (widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall<WidgetToEntitiesOutput>(`${DASHBOARD_URI}/entities-runtime/${widgetId}`, input);
};

export const attackPaths = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsAttackPath[]>(`${DASHBOARD_URI}/attack-paths/${widgetId}`, parameters);
};
