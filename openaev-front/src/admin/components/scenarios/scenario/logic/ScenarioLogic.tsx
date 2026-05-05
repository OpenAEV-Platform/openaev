import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import type { Scenario } from '../../../../../utils/api-types';
import Logic from '../../../chaining/logic/Logic';

const ScenarioLogic = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  return <Logic workflowId={scenario?.scenario_workflow_id} />;
};

export default ScenarioLogic;
