import { simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type WidgetLayout } from '../../utils/api-types';
import { type WidgetInputCustom } from '../../utils/api-types-custom';
import { CUSTOM_DASHBOARD_URI } from './customdashboard-action';

export const createCustomDashboardWidget = (customDashboardId: string, input: WidgetInputCustom) => {
  return simplePostCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets`, input);
};

export const updateCustomDashboardWidget = (customDashboardId: string, widgetId: string, input: WidgetInputCustom) => {
  return simplePutCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets/${widgetId}`, input);
};

export const updateCustomDashboardWidgetLayout = (customDashboardId: string, widgetId: string, input: WidgetLayout) => {
  return simplePutCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets/${widgetId}/layout`, input, {}, true, false);
};

export const deleteCustomDashboardWidget = (customDashboardId: string, widgetId: string) => {
  return simpleDelCall(`${CUSTOM_DASHBOARD_URI}/${customDashboardId}/widgets/${widgetId}`);
};
