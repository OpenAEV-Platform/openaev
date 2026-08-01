import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { searchScenarioHealthcheks } from '../../../../../actions/scenarios/scenario-actions';
import type { ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type HealthCheck, type Scenario } from '../../../../../utils/api-types';
import AutonomousReadOnlyBanner from '../../../chaining/AutonomousReadOnlyBanner';
import ScopeDefinition from '../../../chaining/ScopeDefinition';
import Healthchecks from '../../../common/healthchecks/Healthchecks';

const ScenarioScope = ({ readOnly = false }: { readOnly?: boolean }) => {
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

  useEffect(() => {
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [scenarioId, scenario, workflowConfiguration]);

  if (!scenario?.scenario_workflow_id) return null;

  return (
    <div>
      {readOnly && <AutonomousReadOnlyBanner />}
      {!!healthchecks?.length && (
        <Healthchecks
          healthchecks={healthchecks}
          scenarioId={scenarioId}
        />
      )}
      <ScopeDefinition workflowId={scenario.scenario_workflow_id} readOnly={readOnly} />
    </div>
  );
};

export default ScenarioScope;
