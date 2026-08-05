import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { searchExerciseHealthchecks } from '../../../../../actions/Exercise';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type HealthCheck } from '../../../../../utils/api-types';
import LogicReadOnlyBanner from '../../../chaining/logic/LogicReadOnlyBanner';
import ScopeDefinition from '../../../chaining/ScopeDefinition';
import Healthchecks from '../../../common/healthchecks/Healthchecks';

const SimulationScope = () => {
  const { t } = useFormatter();
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

  // The scope is frozen once the simulation has been launched (see ADR-005).
  const readOnly = exercise.exercise_status !== 'SCHEDULED';
  const readOnlyMessage = exercise.exercise_scenario
    ? t('This simulation has been launched. Its scope is read-only. Reset the simulation to edit it, or update the scenario and run it again.')
    : t('This simulation has been launched. Its scope is read-only. Reset the simulation to edit it.');

  return (
    <div>
      {readOnly && <LogicReadOnlyBanner message={readOnlyMessage} />}
      {!!healthchecks?.length && (
        <Healthchecks
          healthchecks={healthchecks}
          exerciseId={exerciseId}
        />
      )}
      <ScopeDefinition workflowId={exercise.exercise_workflow_id} readOnly={readOnly} />
    </div>
  );
};

export default SimulationScope;
