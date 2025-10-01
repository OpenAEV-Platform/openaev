import { simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type WidgetLayout } from '../../utils/api-types';
import { type WidgetInput } from '../../utils/api-types-custom';
import { CUSTOM_DASHBOARD_URI } from './customdashboard-action';

export const createCustomDashboardWidget = (customDashboardId: string, input: WidgetInput) => {
  return simplePostCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets`, input);
};

export const updateCustomDashboardWidget = (customDashboardId: string, widgetId: string, input: WidgetInput) => {
  return simplePutCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets/${widgetId}`, input);
};

export const updateCustomDashboardWidgetLayout = (customDashboardId: string, widgetId: string, input: WidgetLayout) => {
  return simplePutCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets/${widgetId}/layout`, input, {}, true, false);
};

export const deleteCustomDashboardWidget = (customDashboardId: string, widgetId: string) => {
  return simpleDelCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets/${widgetId}`);
};
