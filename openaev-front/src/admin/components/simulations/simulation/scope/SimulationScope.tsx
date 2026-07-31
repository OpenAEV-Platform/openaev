import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { searchExerciseHealthchecks } from '../../../../../actions/Exercise';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import { type Exercise, type HealthCheck, type WorkflowScopeRuleOutput } from '../../../../../utils/api-types';
import ScopeDefinition from '../../../chaining/ScopeDefinition';
import Healthchecks from '../../../common/healthchecks/Healthchecks';

const SimulationScope = () => {
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
  const hasAllowlistScope = (workflowConfiguration?.workflow_scope_rules ?? []).some((rule: WorkflowScopeRuleOutput) =>
    rule.workflow_scope_rule_selected_mode === 'ALLOWLIST'
    && !!rule.workflow_scope_rule_value?.trim(),
  );
  const healthchecksForBanner = useMemo(() => {
    const withoutScopeDefinition = healthchecks.filter((hc: HealthCheck) => hc.type !== 'SCOPE_DEFINITION');
    if (!hasAllowlistScope) {
      const scopeDefinitionHealthcheck = healthchecks.find((hc: HealthCheck) =>
        hc.type === ('SCOPE_DEFINITION' as HealthCheck['type']) && hc.detail === 'EMPTY',
      ) ?? {
        creation_date: '',
        detail: 'EMPTY',
        status: 'WARNING',
        type: 'SCOPE_DEFINITION',
      };
      return [...withoutScopeDefinition, scopeDefinitionHealthcheck];
    }
    return withoutScopeDefinition;
  }, [hasAllowlistScope, healthchecks]);

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
