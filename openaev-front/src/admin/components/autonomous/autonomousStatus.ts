import { type AutonomousRun, type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';

/**
 * An autonomous run is "active" while it still owns a live simulation: created, running, paused or
 * waiting for operator input. Deleting the scenario tears both down, so delete is only allowed once
 * the run has reached a terminal state. Single source of truth shared by the scenario hero, the
 * scenario list and the simulation list so every delete guard reads the same rule.
 */
export const isAutonomousRunActive = (run: AutonomousRun | null | undefined): boolean => {
  const status = run?.autonomous_run_status;
  return status === 'CREATED'
    || status === 'RUNNING'
    || status === 'PAUSED'
    || status === 'WAITING_INPUT';
};

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
