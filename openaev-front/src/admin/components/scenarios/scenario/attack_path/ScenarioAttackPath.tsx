import { type FunctionComponent, useMemo } from 'react';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type Scenario } from '../../../../../utils/api-types';
import SimulationAttackPath from '../../../simulations/simulation/attack_path/SimulationAttackPath';

// Scenario-context Attack Path (issue 6647). Reuses the simulation explorer, but a scenario groups
// several runs, so the simulation picker is shown and restricted to this scenario's simulations
// (defaulting to the most recent). The scenario is already loaded by the parent Index route.
//
// Autonomy is a launch-time MODE now, not a scenario type: a chained scenario is always relaunchable
// (normal or autonomous), so this view no longer suppresses the launch CTA nor forces action-centric
// rendering off the scenario. The live autonomous cockpit lives on the simulation detail page, which
// applies action-centric rendering per run.
const ScenarioAttackPath: FunctionComponent = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  // Stable reference so the explorer's picker effect does not refetch on every render.
  const exerciseIds = useMemo(() => scenario?.scenario_exercises ?? [], [scenario?.scenario_exercises]);
  return (
    <SimulationAttackPath
      scenarioExerciseIds={exerciseIds}
      scenarioId={scenarioId}
    />
  );
};

export default ScenarioAttackPath;
