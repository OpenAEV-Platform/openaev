import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { searchScenarioHealthcheks } from '../../../../../actions/scenarios/scenario-actions';
import type { ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type HealthCheck, type Scenario, type WorkflowScopeRuleOutput } from '../../../../../utils/api-types';
import ScopeDefinition from '../../../chaining/ScopeDefinition';
import Healthchecks from '../../../common/healthchecks/Healthchecks';

const ScenarioScope = () => {
  const isScopeDefinitionEmptyHealthcheck = (healthcheck: HealthCheck): boolean =>
    healthcheck.type === 'SCOPE_DEFINITION' && healthcheck.detail === 'EMPTY';
  const SCOPE_DEFINITION_EMPTY_WARNING: HealthCheck = {
    creation_date: '',
    detail: 'EMPTY',
    status: 'WARNING',
    type: 'SCOPE_DEFINITION',
  };

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
  const isScenarioChaining = !!scenario?.scenario_workflow_id;
  const hasAllowlistEntry = (workflowConfiguration?.workflow_scope_rules ?? []).some(
    (rule: WorkflowScopeRuleOutput) =>
      rule.workflow_scope_rule_selected_mode === 'ALLOWLIST'
      && !!rule.workflow_scope_rule_value?.trim(),
  );
  // Scope page is chaining-only; keep state explicit so helpers stay readable.
  const isScopeMissing = isScenarioChaining
    && (
      !hasAllowlistEntry
      || healthchecks.some(isScopeDefinitionEmptyHealthcheck)
    );
  const hasScopeDefinitionWarning = healthchecks.some(isScopeDefinitionEmptyHealthcheck);
  const healthchecksForBanner = isScopeMissing && !hasScopeDefinitionWarning
    ? [...healthchecks, SCOPE_DEFINITION_EMPTY_WARNING]
    : healthchecks;

  useEffect(() => {
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [scenarioId, scenario, workflowConfiguration]);

  if (!scenario?.scenario_workflow_id) return null;

  return (
    <div>
      {!!healthchecksForBanner?.length && (
        <Healthchecks
          healthchecks={healthchecksForBanner}
          scenarioId={scenarioId}
        />
      )}
      <ScopeDefinition workflowId={scenario.scenario_workflow_id} />
    </div>
  );
};

export default ScenarioScope;
