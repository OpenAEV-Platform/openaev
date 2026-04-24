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
    // -- Common --
    'SUCCESS': colorStyles.green,
    'ERROR': colorStyles.red,

    // -- ExecutionTraceStatus --
    // Success statuses
    'SUCCESS_WITH_CLEANUP_FAIL': colorStyles.orange,
    'WARNING': colorStyles.green,
    'ACCESS_DENIED': colorStyles.green,

    // Error statuses
    'COMMAND_NOT_FOUND': colorStyles.red,
    'COMMAND_CANNOT_BE_EXECUTED': colorStyles.red,
    'PREREQUISITE_FAILED': colorStyles.red,
    'INVALID_USAGE': colorStyles.red,
    'TIMEOUT': colorStyles.red,
    'INTERRUPTED': colorStyles.red,
    // @deprecated — rerouted to error in backend
    'MAYBE_PREVENTED': colorStyles.red,
    'MAYBE_PARTIAL_PREVENTED': colorStyles.red,

    // Not counted statuses (from ExecutionTraceStatus)
    'ASSET_AGENTLESS': colorStyles.blueGrey,
    'AGENT_INACTIVE': colorStyles.blueGrey,
    'INFO': colorStyles.blue,

    // -- ExecutionStatus --
    // Inject-level statuses (from ExecutionStatus)
    'PARTIAL': colorStyles.orange,
    'EXECUTING': colorStyles.blue,
    'PENDING': colorStyles.blue,
    'QUEUING': colorStyles.yellow,
    'DRAFT': colorStyles.blueGrey,

    // Expectation display labels
    'FAILED': colorStyles.red,
    'ASSET_INACTIVE': colorStyles.red,
    'NOT PREVENTED': colorStyles.red,
    'NOT DETECTED': colorStyles.red,
    'VULNERABLE': colorStyles.red,
    'PARTIALLY PREVENTED': colorStyles.orange,
    'PARTIALLY DETECTED': colorStyles.orange,
    'PREVENTED': colorStyles.green,
    'DETECTED': colorStyles.green,

    // Simulation statuses
    'RUNNING': colorStyles.green,
    'SCHEDULED': colorStyles.blue,
    'PAUSED': colorStyles.orange,
    'CANCELED': colorStyles.canceled,

    'FINISHED': colorStyles.grey,
    'NOT_PLANNED': colorStyles.grey,
  };

  return statusMap[normalized] ?? colorStyles.blueGrey;
};

export const getStatusColor = (theme: Theme, status: string | undefined): string => {
  const normalized = (status ?? '').toLowerCase();

  const colorMap: Record<string, string> = {
    // -- Common --
    'success': theme.palette.success.main,
    'error': theme.palette.error.main,

    // -- ExecutionTraceStatus --
    // Success statuses
    'warning': theme.palette.success.main,
    'access_denied': theme.palette.success.main,
    // Error statuses
    'command_not_found': theme.palette.error.main,
    'command_cannot_be_executed': theme.palette.error.main,
    'invalid_usage': theme.palette.error.main,
    'timeout': theme.palette.error.main,
    'interrupted': theme.palette.error.main,
    // @deprecated — rerouted to error in backend
    'maybe_prevented': colorStyles.purple.color,
    'maybe_partial_prevented': colorStyles.lightPurple.color,
    // Not counted statuses
    'asset_agentless': theme.palette.grey['500'],
    'agent_inactive': theme.palette.grey['500'],

    // -- ExecutionStatus --
    'partial': colorStyles.orange.color,
    'executing': colorStyles.blue.color,
    'pending': theme.palette.grey['500'],
    'queuing': colorStyles.yellow.color,
    'draft': theme.palette.grey['500'],

    // -- Expectation display labels --
    // Success
    'prevented': theme.palette.success.main,
    'detected': theme.palette.success.main,
    'not vulnerable': theme.palette.success.main,
    'successful': theme.palette.success.main,
    '100': theme.palette.success.main,
    'ok': theme.palette.success.main,
    // Partial
    'partially prevented': theme.palette.warning.main,
    'partially detected': theme.palette.warning.main,
    'success_with_cleanup_fail': theme.palette.warning.main,
    // Failed
    'failed': theme.palette.error.main,
    'undetected': theme.palette.error.main,
    'unprevented': theme.palette.error.main,
    'vulnerable': theme.palette.error.main,
    '0': theme.palette.error.main,

    // -- Simulation statuses --
    'running': theme.palette.success.main,
    'on-going': theme.palette.success.main,
    'scheduled': colorStyles.blue.color,
    'paused': theme.palette.warning.main,
    'canceled': colorStyles.canceled.color,
    'finished': theme.palette.grey['500'],
    'not_planned': theme.palette.grey['500'],

    // -- Misc --
    'update': colorStyles.orange.color,
    'replace': theme.palette.error.main,
  };

  return colorMap[normalized] ?? theme.palette.error.main;
};

export default getStatusColor;
