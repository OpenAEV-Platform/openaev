import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { searchExerciseHealthchecks } from '../../../../../actions/Exercise';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import { type Exercise, type HealthCheck } from '../../../../../utils/api-types';
import ScopeDefinition from '../../../chaining/ScopeDefinition';
import Healthchecks from '../../../common/healthchecks/Healthchecks';

const SimulationScope = ({ readOnly = false }: { readOnly?: boolean }) => {
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  const { workflowConfiguration } = useHelper(
    (helper: WorkflowConfigurationHelper) => ({
      workflowConfiguration: exercise?.exercise_workflow_id
        ? helper.getWorkflowConfiguration(exercise.exercise_workflow_id)
        : undefined,
    }),
  );

  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);

  useEffect(() => {
    if (exercise?.exercise_workflow_id) {
      searchExerciseHealthchecks(exerciseId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
    }
  }, [exerciseId, exercise, workflowConfiguration]);

  if (!exercise?.exercise_workflow_id) return null;

  // The empty-scope shortfall is already surfaced in the hero; drop the duplicate inline banner.
  const visibleHealthchecks = healthchecks.filter(healthcheck => healthcheck.type !== 'SCOPE_DEFINITION');

  return (
    <div>
      {!!visibleHealthchecks.length && (
        <Healthchecks
          healthchecks={visibleHealthchecks}
          exerciseId={exerciseId}
        />
      )}
      <ScopeDefinition workflowId={exercise.exercise_workflow_id} readOnly={readOnly} />
    </div>
  );
};

export default SimulationScope;
