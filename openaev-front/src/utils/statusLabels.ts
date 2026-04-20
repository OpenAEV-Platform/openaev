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

/**
 * Returns the human-readable display label for a status key.
 * Falls back to the raw status if no mapping exists.
 */
export const getStatusLabel = (status: string | undefined | null): string => {
  if (!status) return '';
  return statusLabelMap[status.toUpperCase()] ?? status;
};

// -- STATUS TOOLTIPS --

const statusTooltipMap: Record<string, string> = {
  // -- ExecutionTraceStatus (Agent level) --
  SUCCESS_WITH_CLEANUP_FAIL: 'The main command executed successfully, but the cleanup step failed. Check cleanup prerequisites and logs on the target.',
  WARNING: 'The command completed but produced stderr output. Review stderr for potential issues.',
  ACCESS_DENIED: 'The command was denied due to insufficient privileges. This confirms the security control is working — the agent attempted execution but was blocked.',
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
export const getStatusTooltip = (status: string | undefined | null): string | undefined => {
  if (!status) return undefined;
  return statusTooltipMap[status.toUpperCase()];
};
