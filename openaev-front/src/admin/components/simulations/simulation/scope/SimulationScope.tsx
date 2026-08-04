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

const SimulationScope = ({ readOnly = false }: { readOnly?: boolean }) => {
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

  // The empty-scope shortfall is already surfaced in the hero; drop the duplicate inline banner.
  const visibleHealthchecks = healthchecks.filter(healthcheck => healthcheck.type !== 'SCOPE_DEFINITION');

  // Read-only for two independent reasons: an autonomous run (router-provided) OR a launched
  // simulation - the scope is editable only while SCHEDULED (see ADR-005).
  const launched = exercise.exercise_status !== 'SCHEDULED';
  const effectiveReadOnly = readOnly || launched;
  const resolveReadOnlyMessage = () => {
    if (readOnly) {
      return t('This simulation is driven by the autonomous attack path. Its scope is read-only.');
    }
    if (exercise.exercise_scenario) {
      return t('This simulation has been launched. Its scope is read-only. Reset the simulation to edit it, or update the scenario and run it again.');
    }
    return t('This simulation has been launched. Its scope is read-only. Reset the simulation to edit it.');
  };
  const readOnlyMessage = resolveReadOnlyMessage();

  return (
    <div>
      {effectiveReadOnly && <LogicReadOnlyBanner message={readOnlyMessage} />}
      {!!visibleHealthchecks.length && (
        <Healthchecks
          healthchecks={visibleHealthchecks}
          exerciseId={exerciseId}
        />
      )}
      <ScopeDefinition workflowId={exercise.exercise_workflow_id} readOnly={effectiveReadOnly} />
    </div>
  );
};

export default SimulationScope;
