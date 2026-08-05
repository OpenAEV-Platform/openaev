import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { Exercise } from '../../../../../utils/api-types';
import Logic from '../../../chaining/logic/Logic';

const SimulationLogic = () => {
  const { t } = useFormatter();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  // A launched simulation is frozen: the logic map is editable only while it is SCHEDULED
  // (UI "Draft" / "Scheduled"). See ADR-005. While the exercise is still loading (undefined),
  // keep it editable so the frozen banner never flashes before the status is known.
  const readOnly = !!exercise && exercise.exercise_status !== 'SCHEDULED';
  const readOnlyMessage = exercise?.exercise_scenario
    ? t('This simulation has been launched. Its logic map is read-only. Reset the simulation to edit it, or update the scenario and run it again.')
    : t('This simulation has been launched. Its logic map is read-only. Reset the simulation to edit it.');
  return (
    <Logic
      workflowId={exercise?.exercise_workflow_id}
      context="simulation"
      readOnly={readOnly}
      readOnlyMessage={readOnlyMessage}
    />
  );
};

export default SimulationLogic;
