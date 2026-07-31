import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { searchScenarioHealthcheks } from '../../../../../actions/scenarios/scenario-actions';
import type { ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type HealthCheck, type Scenario, type WorkflowScopeRuleOutput } from '../../../../../utils/api-types';
import ScopeDefinition from '../../../chaining/ScopeDefinition';
import Healthchecks from '../../../common/healthchecks/Healthchecks';

const ScenarioScope = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  const { workflowConfiguration } = useHelper(
    (helper: WorkflowConfigurationHelper) => ({
      workflowConfiguration: scenario?.scenario_workflow_id
        ? helper.getWorkflowConfiguration(scenario.scenario_workflow_id)
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
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [scenarioId, scenario, workflowConfiguration]);

  if (!scenario?.scenario_workflow_id) return null;

  return (
    <div>
      {!!healthchecks?.length && (
        <Healthchecks
          healthchecks={healthchecks}
          scenarioId={scenarioId}
        />
      )}
      <ScopeDefinition workflowId={scenario.scenario_workflow_id} />
    </div>
  );
};

export default ScenarioScope;
