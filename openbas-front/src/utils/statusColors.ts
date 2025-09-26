import { type Theme } from '@mui/material';

import colorStyles from '../components/Color';

export const computeStatusStyle = (status: string | undefined | null) => {
  const normalized = (status ?? '').toUpperCase();

  const statusMap: Record<string, typeof colorStyles[keyof typeof colorStyles]> = {
    ERROR: colorStyles.red,
    ASSET_INACTIVE: colorStyles.red,
    MAYBE_PREVENTED: colorStyles.purple,
    MAYBE_PARTIAL_PREVENTED: colorStyles.lightPurple,
    PARTIAL: colorStyles.orange,
    QUEUING: colorStyles.yellow,
    EXECUTING: colorStyles.blue,
    PENDING: colorStyles.blue,
    SUCCESS: colorStyles.green,
    CANCELED: colorStyles.white,
    FINISHED: colorStyles.grey,
    SCHEDULED: colorStyles.blue,
    RUNNING: colorStyles.green,
    PAUSED: colorStyles.orange,
    NOT_PLANNED: colorStyles.grey,
  };

  return statusMap[normalized] ?? colorStyles.blueGrey;
};

// Compute color for status - Manual expectations
export const computeColorStyle = (status: string | undefined) => {
  if (status === 'PENDING') {
    return colorStyles.blueGrey;
  }
  if (status === 'SUCCESS') {
    return colorStyles.green;
  }
  if (status === 'PARTIAL') {
    return colorStyles.orange;
  }
  return colorStyles.red;
};

export const getStatusColor = (theme: Theme, status: string | undefined): string => {
  const normalized = (status ?? '').toLowerCase();

  const colorMap: Record<string, string> = {
    // Success
    'prevented': theme.palette.success.main,
    'detected': theme.palette.success.main,
    'not vulnerable': theme.palette.success.main,
    'successful': theme.palette.success.main,
    'finished': theme.palette.grey['500'],
    'success': theme.palette.success.main,
    '100': theme.palette.success.main,
    'ok': theme.palette.success.main,

    // Partial
    'partial': colorStyles.orange.color,
    'partially prevented': theme.palette.warning.main,
    'partially detected': theme.palette.warning.main,
    'update': colorStyles.orange.color,
    'paused': theme.palette.warning.main,
    'maybe_prevented': colorStyles.purple.color,
    'maybe_partial_prevented': colorStyles.lightPurple.color,

    // Pending
    'pending': theme.palette.grey['500'],
    'scheduled': colorStyles.blue.color,
    'queuing': colorStyles.yellow.color,
    'executing': colorStyles.blue.color,
    'draft': theme.palette.grey['500'],
    'on-going': theme.palette.success.main,
    'running': theme.palette.success.main,
    'not_planned': theme.palette.grey['500'],

    // Failed
    'failed': theme.palette.error.main,
    'undetected': theme.palette.error.main,
    'unprevented': theme.palette.error.main,
    'vulnerable': theme.palette.error.main,
    '0': theme.palette.error.main,
    'replace': theme.palette.error.main,
    'canceled': colorStyles.white.color,
    'error': theme.palette.error.main,

  };

  return colorMap[normalized] ?? theme.palette.error.main;
};

export default getStatusColor;
