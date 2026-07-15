import { simplePostCall } from '../../utils/Action';
import { type Pagination, type Widget, type WidgetToEntitiesInput } from '../../utils/api-types';

export const DASHBOARD_URI = '/api/dashboards';

// -- AD-HOC (non-persisted) WIDGET QUERIES --
// Used by hardcoded platform dashboards: the full widget configuration is sent
// to the backend instead of referencing a stored widget.

export const adHocSeries = (widgetConfig: Widget['widget_config'], parameters?: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/adhoc/series`, {
    widget_config: widgetConfig,
    parameters,
  });
};

export const adHocCount = (widgetConfig: Widget['widget_config'], parameters?: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/adhoc/count`, {
    widget_config: widgetConfig,
    parameters,
  });
};

export const adHocAverage = (widgetConfig: Widget['widget_config'], parameters?: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/adhoc/average`, {
    widget_config: widgetConfig,
    parameters,
  });
};

export const adHocEntities = (widgetConfig: Widget['widget_config'], parameters?: Record<string, string | undefined>, pagination?: Pagination) => {
  return simplePostCall(`${DASHBOARD_URI}/adhoc/entities`, {
    widget_config: widgetConfig,
    parameters,
    pagination,
  });
};

export const adHocEntitiesRuntime = (
  widgetType: Widget['widget_type'],
  widgetConfig: Widget['widget_config'],
  input: WidgetToEntitiesInput,
) => {
  return simplePostCall(`${DASHBOARD_URI}/adhoc/entities-runtime`, {
    widget_type: widgetType,
    widget_config: widgetConfig,
    ...input,
  });
};

export const average = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/average/${widgetId}`, parameters);
};

export const count = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/count/${widgetId}`, parameters);
};

export const series = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/series/${widgetId}`, parameters);
};

export const entities = (widgetId: string, parameters: Record<string, string | undefined>, pagination?: Pagination | null) => {
  return simplePostCall(`${DASHBOARD_URI}/entities/${widgetId}`, {
    parameters,
    pagination,
  });
};

export const widgetToEntitiesRuntime = (widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall(`${DASHBOARD_URI}/entities-runtime/${widgetId}`, input);
};

export const attackPaths = (widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${DASHBOARD_URI}/attack-paths/${widgetId}`, parameters);
};
