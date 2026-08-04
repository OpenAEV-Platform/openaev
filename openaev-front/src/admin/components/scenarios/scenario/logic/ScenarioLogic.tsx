import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import type { Scenario } from '../../../../../utils/api-types';
import Logic from '../../../chaining/logic/Logic';

const ScenarioLogic = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  // Scenario logic maps are never frozen: they are isolated per run by the copy performed at
  // launch (see ADR-005), so they stay fully editable.
  return <Logic workflowId={scenario?.scenario_workflow_id} context="scenario" readOnly={false} />;
};

export default ScenarioLogic;
