import { type ChipSeverity } from '@filigran/design-system';
import { type Theme } from '@mui/material';

import { HUMAN_EXPECTATION_TYPES } from '../admin/components/common/injects/expectations/Expectation';
import colorStyles from '../components/Color';
import { capitalize } from './String';

type ExpectationResult = 'SUCCESS' | 'PARTIAL' | 'FAILED';

// Result labels of the technical expectation types. A label is coloured as the result it stands for,
// so a new type only needs its entry here.
const EXPECTATION_RESULT_LABELS: Record<string, Record<ExpectationResult, string>> = {
  PREVENTION: {
    SUCCESS: 'Prevented',
    PARTIAL: 'Partially Prevented',
    FAILED: 'Not Prevented',
  },
  DETECTION: {
    SUCCESS: 'Detected',
    PARTIAL: 'Partially Detected',
    FAILED: 'Not Detected',
  },
  LOGGING: {
    SUCCESS: 'Logged',
    PARTIAL: 'Partially Logged',
    FAILED: 'Not Logged',
  },
  VULNERABILITY: {
    SUCCESS: 'Not vulnerable',
    PARTIAL: 'Partially Vulnerable',
    FAILED: 'Vulnerable',
  },
};

// Upper-cased result label -> the result it stands for.
const RESULT_BY_EXPECTATION_LABEL: Record<string, ExpectationResult> = Object.fromEntries(
  Object.values(EXPECTATION_RESULT_LABELS).flatMap(labels =>
    Object.entries(labels).map(([result, label]) => [label.toUpperCase(), result as ExpectationResult])),
);

export function computeInjectExpectationLabel(
  status?: string,
  type?: string): string | undefined {
  if (!status || !type) return undefined;

  const normalizedStatus = status.toUpperCase();
  const labels = EXPECTATION_RESULT_LABELS[type.toUpperCase()];
  if (labels) {
    if (normalizedStatus === 'PENDING') return 'Pending';
    const label = labels[normalizedStatus as ExpectationResult];
    if (label) return label;
  }

  if (HUMAN_EXPECTATION_TYPES.includes(type.toUpperCase())) {
    return capitalize(status);
  }

  return undefined;
}

// The product's chip palette expressed on the library severity axis, one table for every status chip.
// Green reads "low" (the system green), blue "info", orange "medium" for a status (partial, paused),
// red "critical", greys and the brown "canceled" neutral, the violets "info". Ruled on 2026-09-17.
export const colorStyleSeverity: Record<keyof typeof colorStyles, ChipSeverity> = {
  green: 'low',
  blue: 'info',
  red: 'critical',
  orange: 'medium',
  yellow: 'medium',
  purple: 'info',
  lightPurple: 'info',
  blueGrey: 'neutral',
  grey: 'neutral',
  canceled: 'neutral',
};

const STATUS_COLOR_STYLE: Record<string, keyof typeof colorStyles> = {
  // -- Common --
  SUCCESS: 'green',
  EXECUTED: 'green',
  ERROR: 'red',
  // -- ExecutionTraceStatus --
  EXECUTED_WITH_CLEANUP_FAILURE: 'orange',
  WARNING: 'green',
  ACCESS_DENIED: 'green',
  COMMAND_NOT_FOUND: 'red',
  COMMAND_CANNOT_BE_EXECUTED: 'red',
  PREREQUISITE_FAILED: 'red',
  INVALID_USAGE: 'red',
  TIMEOUT: 'red',
  INTERRUPTED: 'red',
  MAYBE_PREVENTED: 'red',
  MAYBE_PARTIAL_PREVENTED: 'red',
  ASSET_AGENTLESS: 'blueGrey',
  AGENT_INACTIVE: 'blueGrey',
  ASSET_CLEARANCE_RESTRICTED: 'blueGrey',
  INFO: 'blue',
  // -- ExecutionStatus --
  PARTIAL: 'orange',
  EXECUTING: 'blue',
  PENDING: 'blue',
  QUEUING: 'yellow',
  DRAFT: 'blueGrey',
  DISABLED: 'blueGrey',
  // Expectation display labels (the per-type result labels resolve through their result)
  FAILED: 'red',
  ASSET_INACTIVE: 'red',
  // Simulation statuses
  RUNNING: 'green',
  SCHEDULED: 'blue',
  PAUSED: 'orange',
  CANCELED: 'canceled',
  FINISHED: 'grey',
  NOT_PLANNED: 'grey',
};

const statusColorStyle = (status: string | undefined | null): keyof typeof colorStyles => {
  const normalized = (status ?? '').toUpperCase();
  const result = RESULT_BY_EXPECTATION_LABEL[normalized];
  return STATUS_COLOR_STYLE[normalized] ?? (result ? STATUS_COLOR_STYLE[result] : undefined) ?? 'blueGrey';
};

/** The library severity for a status chip; unknown statuses read neutral, like the grey they had. */
export const statusSeverity = (status: string | undefined | null): ChipSeverity =>
  colorStyleSeverity[statusColorStyle(status)];

export const computeStatusStyle = (status: string | undefined | null) => colorStyles[statusColorStyle(status)];

export const getStatusColor = (theme: Theme, status: string | undefined): string => {
  const normalized = (status ?? '').toLowerCase();

  const colorMap: Record<string, string> = {
    // -- Common --
    'success': theme.palette.success.main,
    'executed': theme.palette.success.main,
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
    'asset_clearance_restricted': theme.palette.grey['500'],
    'info': colorStyles.blue.color,

    // -- ExecutionStatus --
    'partial': colorStyles.orange.color,
    'executing': colorStyles.blue.color,
    'pending': theme.palette.grey['500'],
    'queuing': colorStyles.yellow.color,
    'draft': theme.palette.grey['500'],

    // -- Expectation display labels (the per-type result labels resolve through their result) --
    // Success
    'successful': theme.palette.success.main,
    '100': theme.palette.success.main,
    'ok': theme.palette.success.main,
    // Partial
    'executed_with_cleanup_failure': theme.palette.warning.main,
    // Failed
    'failed': theme.palette.error.main,
    'undetected': theme.palette.error.main,
    'unprevented': theme.palette.error.main,
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

  const resultColor: Record<ExpectationResult, string> = {
    SUCCESS: theme.palette.success.main,
    PARTIAL: theme.palette.warning.main,
    FAILED: theme.palette.error.main,
  };
  const result = RESULT_BY_EXPECTATION_LABEL[normalized.toUpperCase()];
  return colorMap[normalized] ?? (result ? resultColor[result] : undefined) ?? theme.palette.error.main;
};

export default getStatusColor;
