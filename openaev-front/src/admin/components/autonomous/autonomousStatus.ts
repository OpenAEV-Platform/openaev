import { type AutonomousRun, type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';

/**
 * An autonomous run is "active" while it still owns a live simulation: created, running, paused or
 * waiting for operator input. Deleting the scenario tears both down, so delete is only allowed once
 * the run has reached a terminal state. Single source of truth shared by the scenario hero, the
 * scenario list and the simulation list so every delete guard reads the same rule.
 */
export const isAutonomousRunActive = (run: AutonomousRun | null | undefined): boolean => {
  const status = run?.autonomous_run_status;
  // PLANNING (the orchestrator is still building the scenario's logic) is active so the cockpit
  // polls it. PLANNED is deliberately excluded: finished logic is settled and can be launched
  // (normal or autonomous) or deleted, and it never owns a live simulation.
  return status === 'CREATED'
    || status === 'PLANNING'
    || status === 'RUNNING'
    || status === 'PAUSED'
    || status === 'WAITING_INPUT';
};

/** Whether a run row exists at all (any status). Distinct from "active" - a settled run still exists. */
export const hasAutonomousRun = (run: AutonomousRun | null | undefined): boolean => !!run;

/**
 * A run is "settled" once it exists but is no longer active: PLANNED (finished, built logic) or a terminal
 * COMPLETED / FAILED / CANCELED. This is the state where the scenario keeps a durable, read-only AI
 * outcome (timeline, gaps, proofs) while scope/logic unlock for editing and the hero offers
 * Rebuild / Relaunch.
 */
export const isAutonomousRunSettled = (run: AutonomousRun | null | undefined): boolean =>
  hasAutonomousRun(run) && !isAutonomousRunActive(run);

/**
 * Maps an autonomous run status to an MUI palette color. Kept in its own module (not on a component
 * file) so it can be shared without tripping react-refresh. The run-status chip is rendered from
 * this single source of truth wherever the status is shown - currently the scenario hero's left
 * chips row, next to severity / category, mirroring how a simulation shows its ExerciseStatus.
 */
const autonomousRunStatusColor = (
  status: AutonomousRunStatus,
): 'default' | 'info' | 'warning' | 'success' | 'error' => {
  switch (status) {
    case 'RUNNING':
      return 'info';
    case 'PLANNING':
    case 'PLANNED':
    case 'WAITING_INPUT':
    case 'PAUSED':
      return 'warning';
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
    case 'CANCELED':
      return 'error';
    default:
      return 'default';
  }
};

export default autonomousRunStatusColor;
