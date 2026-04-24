import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { searchScenarioHealthcheks } from '../../../../../actions/scenarios/scenario-actions';
import type { ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type HealthCheck, type Scenario } from '../../../../../utils/api-types';
import Healthchecks from '../../../common/healthchecks/Healthchecks';
import ScopeDefinition from '../../../simulations/simulation/chaining/ScopeDefinition';

const ScenarioScope = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));

  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);

  useEffect(() => {
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [scenarioId, scenario]);

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
