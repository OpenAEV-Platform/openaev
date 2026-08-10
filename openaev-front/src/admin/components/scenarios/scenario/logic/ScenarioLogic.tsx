import { useContext } from 'react';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import type { Scenario } from '../../../../../utils/api-types';
import Logic from '../../../chaining/logic/Logic';
import { PermissionsContext } from '../../../common/Context';

const ScenarioLogic = ({ readOnly = false }: { readOnly?: boolean }) => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  const { permissions } = useContext(PermissionsContext);
  // Scenario logic maps are never frozen: they are isolated per run by the copy performed at
  // launch (see ADR-005). They are read-only for an autonomous run (readOnly prop) or for a user
  // without the manage permission, in which case we just hide the edit actions without a banner.
  return (
    <Logic
      workflowId={scenario?.scenario_workflow_id}
      context="scenario"
      scenarioId={scenarioId}
      readOnly={readOnly || !permissions.canManage}
    />
  );
};

export default ScenarioLogic;
