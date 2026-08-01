import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import type { Scenario } from '../../../../../utils/api-types';
import AutonomousReadOnlyBanner from '../../../chaining/AutonomousReadOnlyBanner';
import Logic from '../../../chaining/logic/Logic';

const ScenarioLogic = ({ readOnly = false }: { readOnly?: boolean }) => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  return (
    <>
      {readOnly && <AutonomousReadOnlyBanner />}
      <Logic workflowId={scenario?.scenario_workflow_id} context="scenario" readOnly={readOnly} />
    </>
  );
};

export default ScenarioLogic;
