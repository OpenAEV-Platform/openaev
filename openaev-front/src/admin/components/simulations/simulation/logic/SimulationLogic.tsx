import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { Exercise } from '../../../../../utils/api-types';
import useSimulationPermissions from '../../../../../utils/permissions/useSimulationPermissions';
import Logic from '../../../chaining/logic/Logic';

const SimulationLogic = ({ isAutonomous = false }: { isAutonomous?: boolean }) => {
  const { t } = useFormatter();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  const permissions = useSimulationPermissions(exerciseId, exercise);
  // Two independent reasons the logic map is read-only: an autonomous run (the AI owns the map,
  // passed in by the router) OR a launched simulation - the map is editable only while SCHEDULED
  // (UI "Draft" / "Scheduled"), see ADR-005. While the exercise is still loading, only the incoming
  // (autonomous) flag applies, so the frozen banner never flashes before the status is known.
  const launched = !!exercise && exercise.exercise_status !== 'SCHEDULED';
  const effectiveReadOnly = isAutonomous || launched || !permissions.canManage;
  const resolveReadOnlyMessage = () => {
    if (isAutonomous) {
      return t('This simulation is driven by the autonomous attack path. Its logic map is read-only.');
    }
    if (launched) {
      if (exercise?.exercise_scenario) {
        return t('This simulation has been launched. Its logic map is read-only. Reset the simulation to edit it, or update the scenario and run it again.');
      }
      return t('This simulation has been launched. Its logic map is read-only. Reset the simulation to edit it.');
    }
    // Read-only purely because the user lacks the manage permission: hide the edit
    // actions without a banner (consistent with ScenarioLogic).
    return undefined;
  };
  const readOnlyMessage = resolveReadOnlyMessage();
  return (
    <Logic
      workflowId={exercise?.exercise_workflow_id}
      context="simulation"
      exerciseId={exerciseId}
      readOnly={effectiveReadOnly}
      readOnlyMessage={effectiveReadOnly ? readOnlyMessage : undefined}
    />
  );
};

export default SimulationLogic;
