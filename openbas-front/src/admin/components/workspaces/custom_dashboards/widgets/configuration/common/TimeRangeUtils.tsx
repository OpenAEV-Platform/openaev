import type { CustomDashboard } from '../../../../../../../utils/api-types';
import { type ParameterOption } from '../../../CustomDashboardContext';

export const ALL_TIME_TIME_RANGE = 'ALL_TIME';
export const CUSTOM_TIME_RANGE = 'CUSTOM';
export const LAST_QUARTER_TIME_RANGE = 'LAST_QUARTER';

export const getTimeRangeFromDashboard = (dashboard: CustomDashboard | undefined, dashboardParametersValues: Record<string, ParameterOption>) => {
  const dashboardParametersModel = dashboard ? dashboard['custom_dashboard_parameters'] : undefined;
  const timeRangeParameterId = dashboardParametersModel?.find(param => param.custom_dashboards_parameter_type === 'timeRange')?.custom_dashboards_parameter_id;
  if (!timeRangeParameterId) return undefined;
  return dashboardParametersValues[timeRangeParameterId].value;
};

export type TimeRangeItem = {
  value: string;
  label_key: string;
  label: string;
};

export const getTimeRangeItems = (t: (text: string) => string): TimeRangeItem[] => [
  {
    value: ALL_TIME_TIME_RANGE,
    label_key: 'All time',
    label: t('All time'),
  },
  {
    value: CUSTOM_TIME_RANGE,
    label_key: 'Custom range',
    label: t('Custom range'),
  },
  {
    value: 'LAST_DAY',
    label_key: 'Last 24 hours',
    label: t('Last 24 hours'),
  },
  {
    value: 'LAST_WEEK',
    label_key: 'Last 7 days',
    label: t('Last 7 days'),
  },
  {
    value: 'LAST_MONTH',
    label_key: 'Last month',
    label: t('Last month'),
  },
  {
    value: LAST_QUARTER_TIME_RANGE,
    label_key: 'Last 3 months',
    label: t('Last 3 months'),
  },
  {
    value: 'LAST_SEMESTER',
    label_key: 'Last 6 months',
    label: t('Last 6 months'),
  },
  {
    value: 'LAST_YEAR',
    label_key: 'Last year',
    label: t('Last year'),
  },
];

export const getTimeRangeItemsWithDefault = (t: (text: string) => string): TimeRangeItem[] => [
  {
    value: 'DEFAULT',
    label_key: 'Dashboard time range',
    label: t('Dashboard time range'),
  },
  ...getTimeRangeItems(t),
];

export const getTimeRangeItem = (
  value: string | undefined,
  t: (text: string) => string,
): TimeRangeItem | undefined => {
  if (!value) return undefined;
  return getTimeRangeItemsWithDefault(t).find(i => i.value === value);
};
