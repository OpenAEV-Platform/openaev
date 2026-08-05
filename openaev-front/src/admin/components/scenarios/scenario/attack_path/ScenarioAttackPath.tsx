import { type FunctionComponent, useMemo } from 'react';
import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type Scenario } from '../../../../../utils/api-types';
import { useAutonomousRunForScenario } from '../../../autonomous/useAutonomousRunForSimulation';
import SimulationAttackPath from '../../../simulations/simulation/attack_path/SimulationAttackPath';

// Scenario-context Attack Path (issue 6647). Reuses the simulation explorer, but a scenario groups
// several runs, so the simulation picker is shown and restricted to this scenario's simulations
// (defaulting to the most recent). The scenario is already loaded by the parent Index route.
const ScenarioAttackPath: FunctionComponent = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  // An autonomous scenario is never launched by hand (the AI drives its single run, the operator
  // restarts from the hero), so the empty-state "Launch a simulation" CTA is suppressed here. The
  // scenario is already loaded by the parent route, so pass its autonomous flag to skip the run
  // lookup on a manual scenario.
  const { run: autonomousRun } = useAutonomousRunForScenario(
    scenarioId,
    scenario ? scenario.scenario_autonomous === true : undefined,
  );
  // Stable reference so the explorer's picker effect does not refetch on every render.
  const exerciseIds = useMemo(() => scenario?.scenario_exercises ?? [], [scenario?.scenario_exercises]);
  return (
    <SimulationAttackPath
      scenarioExerciseIds={exerciseIds}
      scenarioId={scenarioId}
      hideLaunchCta={!!autonomousRun}
    />
  );
};

export default ScenarioAttackPath;
