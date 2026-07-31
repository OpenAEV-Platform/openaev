import { type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';

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
