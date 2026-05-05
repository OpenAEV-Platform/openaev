import { useParams } from 'react-router';

import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import type { Exercise } from '../../../../../utils/api-types';
import Logic from '../../../chaining/logic/Logic';

const SimulationLogic = () => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  return <Logic workflowId={exercise?.exercise_workflow_id} />;
};

export default SimulationLogic;
