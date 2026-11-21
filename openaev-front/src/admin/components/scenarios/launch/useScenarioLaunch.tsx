import { useState } from 'react';

import { launchScenario } from '../../../../actions/scenarios/scenario-actions';
import { type ScenarioTargets, toTargetsInput } from '../targets/scenarioTargets';
import { type LaunchTarget } from './launchTarget';
import ScenarioLaunchCheckDrawer from './ScenarioLaunchCheckDrawer';
import useOpenLaunchedSimulation from './useOpenLaunchedSimulation';

// Every launch goes through the launch check, then opens the simulation it started.
const useScenarioLaunch = (onLaunched?: () => void) => {
  const openLaunchedSimulation = useOpenLaunchedSimulation();
  const [checking, setChecking] = useState<LaunchTarget | null>(null);

  const launchWith = (scenario: LaunchTarget) => (targets: ScenarioTargets) => launchScenario(scenario.scenarioId, toTargetsInput(targets))
    .then(({ simulation_id }) => {
      onLaunched?.();
      openLaunchedSimulation(simulation_id, scenario.type);
    });

  const launchDrawer = checking && (
    <ScenarioLaunchCheckDrawer
      scenario={checking}
      onLaunch={launchWith(checking)}
      onClose={() => setChecking(null)}
    />
  );

  return {
    launch: setChecking,
    launchDrawer,
  };
};

export default useScenarioLaunch;
