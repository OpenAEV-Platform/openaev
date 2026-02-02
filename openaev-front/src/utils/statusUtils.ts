import { type Theme } from '@mui/material';

import { HUMAN_EXPECTATION } from '../admin/components/common/injects/expectations/ExpectationUtils';
import colorStyles from '../components/Color';

const injectExpectationMap = {
  SUCCESS: {
    PREVENTION: 'Prevented',
    DETECTION: 'Detected',
    VULNERABILITY: 'Not Vulnerable',
  },
  FAILED: {
    PREVENTION: 'Not Prevented',
    DETECTION: 'Not Detected',
    VULNERABILITY: 'Vulnerable',
  },
  PARTIAL: {
    PREVENTION: 'Partially Prevented',
    DETECTION: 'Partially Detected',
    VULNERABILITY: 'Partially Vulnerable',
  },
  PENDING: {
    PREVENTION: 'Pending',
    DETECTION: 'Pending',
    VULNERABILITY: 'Pending',
  },
} as const;

type InjectExpectationStatus = keyof typeof injectExpectationMap;
type InjectExpectationType = keyof (typeof injectExpectationMap)[InjectExpectationStatus];

export function computeInjectExpectationLabel(
  status?: string,
  type?: string): string | undefined {
  if (!status || !type) return undefined;

  const normalizedStatus = status.toUpperCase() as InjectExpectationStatus;
  const normalizedType = type.toUpperCase() as InjectExpectationType;

  const result = injectExpectationMap[normalizedStatus]?.[normalizedType];
  if (result) return result;

  if (HUMAN_EXPECTATION.includes(type)) {
    return status;
  }

  return undefined;
}

export const computeStatusStyle = (status: string | undefined | null) => {
  const normalized = (status ?? '').toUpperCase();

  const statusMap: Record<string, typeof colorStyles[keyof typeof colorStyles]> = {
    'ERROR': colorStyles.red,
    'FAILED': colorStyles.red,
    'ASSET_INACTIVE': colorStyles.red,
    'NOT PREVENTED': colorStyles.red,
    'NOT DETECTED': colorStyles.red,
    'VULNERABLE': colorStyles.red,

    'MAYBE_PREVENTED': colorStyles.purple,
    'MAYBE_PARTIAL_PREVENTED': colorStyles.lightPurple,

    'PARTIAL': colorStyles.orange,
    'PAUSED': colorStyles.orange,
    'PARTIALLY PREVENTED': colorStyles.orange,
    'PARTIALLY DETECTED': colorStyles.orange,

    'QUEUING': colorStyles.yellow,

    'EXECUTING': colorStyles.blue,
    'PENDING': colorStyles.blue,
    'SCHEDULED': colorStyles.blue,

    'SUCCESS': colorStyles.green,
    'RUNNING': colorStyles.green,
    'PREVENTED': colorStyles.green,
    'DETECTED': colorStyles.green,

    'CANCELED': colorStyles.canceled,

    'FINISHED': colorStyles.grey,
    'NOT_PLANNED': colorStyles.grey,
  };

  return statusMap[normalized] ?? colorStyles.blueGrey;
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
    'canceled': colorStyles.canceled.color,
    'error': theme.palette.error.main,

  };

  return colorMap[normalized] ?? theme.palette.error.main;
};

export default getStatusColor;
