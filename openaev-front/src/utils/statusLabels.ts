/**
 * Display labels and tooltips for execution trace statuses.
 *
 * These maps override the raw backend enum values with user-friendly text at
 * the frontend level, so labels and descriptions can be adjusted on the fly
 * without a backend change if product requirements evolve again.
 */

// -- STATUS DISPLAY LABELS --
const statusLabelMap: Record<string, string> = {
  // -- ExecutionTraceStatus (Agent level) --
  SUCCESS: 'Executed',
  SUCCESS_WITH_CLEANUP_FAIL: 'Cleanup failed',
  WARNING: 'Executed with warning',
  ACCESS_DENIED: 'Access denied',
  COMMAND_NOT_FOUND: 'Command not found',
  COMMAND_CANNOT_BE_EXECUTED: 'Command cannot be executed',
  PREREQUISITE_FAILED: 'Prerequisite failed',
  INVALID_USAGE: 'Invalid usage',
  TIMEOUT: 'Timeout',
  INTERRUPTED: 'Interrupted',
  AGENT_INACTIVE: 'Agent inactive',
  ASSET_AGENTLESS: 'Asset agentless',
};

// -- REMOVE THIS PART IN #5643 --
const injectStatusLabelMap: Record<string, string> = {
  SUCCESS: 'EXECUTED',
  EXECUTING: 'RUNNING',
};

export const getInjectStatusLabel = (status: string | undefined | null): string => {
  if (!status) return '';
  return injectStatusLabelMap[status.toUpperCase()] ?? status;
};

const traceStatusLabelMap: Record<string, string> = {
  SUCCESS: 'EXECUTED',
  SUCCESS_WITH_CLEANUP_FAIL: 'EXECUTED_WITH_CLEANUP_FAILURE',
  WARNING: 'EXECUTED_WITH_WARNING',
  QUEUING: 'PENDING IN QUEUE',
};

export const getTraceStatusLabel = (status: string | undefined | null): string => {
  if (!status) return '';
  return traceStatusLabelMap[status.toUpperCase()] ?? status;
};

// ---

/**
 * Returns the human-readable display label for a status key.
 * Falls back to the raw status if no mapping exists.
 */
export const getStatusLabel = (status: string | undefined | null): string => {
  if (!status) return '';
  return statusLabelMap[status.toUpperCase()] ?? status;
};

// -- STATUS TOOLTIPS --

const agentStatusTooltipMap: Record<string, string> = {
  // -- ExecutionTraceStatus (Agent level) --
  SUCCESS: 'The inject ran successfully.',
  SUCCESS_WITH_CLEANUP_FAIL: 'The main command executed successfully, but the cleanup step failed. Check cleanup prerequisites and logs on the target.',
  WARNING: 'The command completed but produced stderr output. Review stderr for potential issues.',
  ACCESS_DENIED: 'The command was denied due to insufficient privileges. This confirms the security control is working — the agent attempted execution but was blocked.',
  ERROR: 'The command failed with an unexpected error. Check the agent logs and stderr output for details',
  COMMAND_NOT_FOUND: 'The command was not found on the target. Ensure the tool is installed and available in the system PATH.',
  COMMAND_CANNOT_BE_EXECUTED: 'The command exists but cannot be executed. Check file permissions and ensure the binary has execute rights.',
  PREREQUISITE_FAILED: 'A prerequisite check failed before the main command could run. Review prerequisite dependencies and ensure they are met on the target.',
  INVALID_USAGE: 'The command was invoked with incorrect arguments or syntax. Verify the inject parameters and command.',
  TIMEOUT: 'The agent did not complete execution within the allowed time threshold. Consider investigating target performance.',
  INTERRUPTED: 'The inject was interrupted before completion. This may be caused by a system signal, user intervention, or resource constraint.',
  AGENT_INACTIVE: 'This agent was not active during the inject execution. Check your asset connectivity.',
};

/**
 * Returns the explanatory tooltip text for a given status.
 * Returns undefined if no tooltip is defined (no tooltip will be shown).
 */
export const getAgentStatusTooltip = (status: string | undefined | null): string | undefined => {
  if (!status) return undefined;
  return agentStatusTooltipMap[status.toUpperCase()];
};

const injectStatusTooltipMap: Record<string, string> = {
  SUCCESS: 'The inject completed successfully. All targets were processed and results have been recorded.',
  ERROR: 'The inject could not be completed. No result was recorded for any target. Review the inject configuration or check the execution details for more information.',
  PARTIAL: 'The inject completed on some targets but not all. Review the individual target results for more details.',
  DRAFT: 'This inject has not been executed yet. It is saved as a draft and can be edited before being queued for execution.',
  QUEUING: 'The inject is queued and waiting to be dispatched. Execution will begin based on scheduling order.',
  EXECUTING: 'The inject is currently being executed across target.',
  PENDING: 'The inject has been scheduled but execution has not started yet. The platform is waiting for the execution window or for agent availability before dispatching.',
};

export const getInjectStatusTooltip = (status: string): string => {
  return injectStatusTooltipMap[status.toUpperCase()] ?? status;
};
