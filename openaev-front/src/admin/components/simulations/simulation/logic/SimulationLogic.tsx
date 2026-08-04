import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { Exercise } from '../../../../../utils/api-types';
import Logic from '../../../chaining/logic/Logic';

const SimulationLogic = ({ readOnly = false }: { readOnly?: boolean }) => {
  const { t } = useFormatter();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  // Two independent reasons the logic map is read-only: an autonomous run (the AI owns the map,
  // passed in by the router) OR a launched simulation - the map is editable only while SCHEDULED
  // (UI "Draft" / "Scheduled"), see ADR-005. While the exercise is still loading, only the incoming
  // (autonomous) flag applies, so the frozen banner never flashes before the status is known.
  const launched = !!exercise && exercise.exercise_status !== 'SCHEDULED';
  const effectiveReadOnly = readOnly || launched;
  const readOnlyMessage = readOnly
    ? t('This simulation is driven by the autonomous attack path. Its logic map is read-only.')
    : (exercise?.exercise_scenario
        ? t('This simulation has been launched. Its logic map is read-only. Reset the simulation to edit it, or update the scenario and run it again.')
        : t('This simulation has been launched. Its logic map is read-only. Reset the simulation to edit it.'));
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
