export const ALL_TIME_TIME_RANGE = 'ALL_TIME';
export const CUSTOM_TIME_RANGE = 'CUSTOM';
export const LAST_QUARTER_TIME_RANGE = 'LAST_QUARTER';

export const getTimeRangeItems = (t: (text: string) => string) => [
  {
    value: ALL_TIME_TIME_RANGE,
    label: t('All time'),
  },
  {
    value: CUSTOM_TIME_RANGE,
    label: t('Custom range'),
  },
  {
    value: 'LAST_DAY',
    label: t('Last 24 hours'),
  },
  {
    value: 'LAST_WEEK',
    label: t('Last 7 days'),
  },
  {
    value: 'LAST_MONTH',
    label: t('Last month'),
  },
  {
    value: LAST_QUARTER_TIME_RANGE,
    label: t('Last 3 months'),
  },
  {
    value: 'LAST_SEMESTER',
    label: t('Last 6 months'),
  },
  {
    value: 'LAST_YEAR',
    label: t('Last year'),
  },
];

export const getTimeRangeItemsWithDefault = (t: (text: string) => string) => [
  {
    value: 'DEFAULT',
    label: t('Dashboard time range'),
  },
  ...getTimeRangeItems(t),
];
