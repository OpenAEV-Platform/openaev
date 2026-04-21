import { useParams } from 'react-router';

import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import { type Exercise } from '../../../../../utils/api-types';
import ScopeDefinition from '../chaining/ScopeDefinition';

const SimulationScope = () => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));

  if (!exercise?.exercise_workflow_id) return null;

  return (
    <ScopeDefinition workflowId={exercise.exercise_workflow_id} />
  );
};

export default SimulationScope;
