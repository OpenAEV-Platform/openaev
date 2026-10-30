import { type Theme } from '@mui/material/styles';

export type Severity = 'success' | 'error' | 'warning' | 'info' | 'muted';

// Classify each execution trace status into a display severity. Kept local to
// the trace views so the log styling can evolve without touching the shared
// status color/label maps.
const SEVERITY_BY_STATUS: Record<string, Severity> = {
  EXECUTED: 'success',
  SUCCESS: 'success',
  ACCESS_DENIED: 'success', // the control blocked the attack - a good outcome
  WARNING: 'warning',
  EXECUTED_WITH_CLEANUP_FAILURE: 'warning',
  PARTIAL: 'warning',
  MAYBE_PREVENTED: 'warning',
  MAYBE_PARTIAL_PREVENTED: 'warning',
  ERROR: 'error',
  COMMAND_NOT_FOUND: 'error',
  COMMAND_CANNOT_BE_EXECUTED: 'error',
  PREREQUISITE_FAILED: 'error',
  INVALID_USAGE: 'error',
  TIMEOUT: 'error',
  INTERRUPTED: 'error',
  AGENT_OVERLOADED: 'error',
  INFO: 'info',
  PENDING: 'muted',
  QUEUING: 'muted',
  ASSET_AGENTLESS: 'muted',
  AGENT_INACTIVE: 'muted',
};

// Shared severity resolvers so callers (e.g. the agent timeline) can color
// their own markers consistently with the trace rows.
export const severityForStatus = (status?: string): Severity =>
  SEVERITY_BY_STATUS[status?.toUpperCase() ?? ''] ?? 'muted';

export const severityColor = (theme: Theme, severity: Severity): string => {
  switch (severity) {
    case 'success':
      return theme.palette.success.main;
    case 'error':
      return theme.palette.error.main;
    case 'warning':
      return theme.palette.warning.main;
    case 'info':
      return theme.palette.info.main;
    default:
      return theme.palette.text.disabled;
  }
};
