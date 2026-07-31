import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { searchExerciseHealthchecks } from '../../../../../actions/Exercise';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import { type Exercise, type HealthCheck, type WorkflowScopeRuleOutput } from '../../../../../utils/api-types';
import ScopeDefinition from '../../../chaining/ScopeDefinition';
import Healthchecks from '../../../common/healthchecks/Healthchecks';

const SimulationScope = () => {
  const isScopeDefinitionEmptyHealthcheck = (healthcheck: HealthCheck): boolean =>
    healthcheck.type === 'SCOPE_DEFINITION' && healthcheck.detail === 'EMPTY';
  const hasAllowlistEntry = (workflowScopeRules: WorkflowScopeRuleOutput[] = []): boolean =>
    workflowScopeRules.some((rule: WorkflowScopeRuleOutput) =>
      rule.workflow_scope_rule_selected_mode === 'ALLOWLIST'
      && !!rule.workflow_scope_rule_value?.trim(),
    );
  const SCOPE_DEFINITION_EMPTY_WARNING: HealthCheck = {
    creation_date: '',
    detail: 'EMPTY',
    status: 'WARNING',
    type: 'SCOPE_DEFINITION',
  };

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
  const isSimulationChaining = !!exercise?.exercise_workflow_id;
  // Scope page is chaining-only; keep state explicit so helpers stay readable.
  const isScopeMissing = isSimulationChaining
    && (
      !hasAllowlistEntry(workflowConfiguration?.workflow_scope_rules ?? [])
      || healthchecks.some(isScopeDefinitionEmptyHealthcheck)
    );
  const healthchecksForBanner = (() => {
    if (!isSimulationChaining) {
      return healthchecks;
    }
    const withoutScopeDefinition = healthchecks.filter((healthcheck: HealthCheck) => healthcheck.type !== 'SCOPE_DEFINITION');
    if (!isScopeMissing) {
      return withoutScopeDefinition;
    }
    const scopeDefinitionHealthcheck = healthchecks.find(isScopeDefinitionEmptyHealthcheck) ?? SCOPE_DEFINITION_EMPTY_WARNING;
    return [...withoutScopeDefinition, scopeDefinitionHealthcheck];
  })();

  useEffect(() => {
    if (exercise?.exercise_workflow_id) {
      searchExerciseHealthchecks(exerciseId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
    }
  }, [exerciseId, exercise, workflowConfiguration]);

  if (!exercise?.exercise_workflow_id) return null;

  return (
    <div>
      {!!healthchecksForBanner?.length && (
        <Healthchecks
          healthchecks={healthchecksForBanner}
          exerciseId={exerciseId}
        />
      )}
      <ScopeDefinition workflowId={exercise.exercise_workflow_id} />
    </div>
  );
};

export default SimulationScope;
