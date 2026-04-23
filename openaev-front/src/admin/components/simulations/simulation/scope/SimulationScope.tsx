import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { searchExerciseHealthchecks } from '../../../../../actions/Exercise';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import { type Exercise, type HealthCheck } from '../../../../../utils/api-types';
import Healthchecks from '../../../common/healthchecks/Healthchecks';
import ScopeDefinition from '../chaining/ScopeDefinition';

const SimulationScope = () => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));

  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);

  useEffect(() => {
    if (exercise?.exercise_workflow_id) {
      searchExerciseHealthchecks(exerciseId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
    }
  }, [exerciseId, exercise]);

  if (!exercise?.exercise_workflow_id) return null;

  return (
    <div>
      {!!healthchecks?.length && (
        <Healthchecks
          healthchecks={healthchecks}
          exerciseId={exerciseId}
        />
      )}
      <ScopeDefinition workflowId={exercise.exercise_workflow_id} />
    </div>
  );
};

export default SimulationScope;
