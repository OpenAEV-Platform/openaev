import { useState } from 'react';

import { launchScenario } from '../../../../actions/scenarios/scenario-actions';
import { type ScenarioTargets, toTargetsInput } from '../targets/scenarioTargets';
import { type LaunchTarget } from './launchTarget';
import ScenarioLaunchCheckDrawer from './ScenarioLaunchCheckDrawer';
import { type LaunchCheckState } from './useLaunchCheck';
import useOpenLaunchedSimulation from './useOpenLaunchedSimulation';

// Every launch goes through the launch check, then opens the simulation it started. With a shared check, the
// drawer shows that one, read again as it opens (what launches is current) and as it closes (what was fixed
// there shows where it opened from).
const useScenarioLaunch = (onLaunched?: () => void, sharedCheck?: LaunchCheckState) => {
  const openLaunchedSimulation = useOpenLaunchedSimulation();
  const [checking, setChecking] = useState<LaunchTarget | null>(null);

  // The drawer shows a failed launch: no error toast too.
  const launchWith = (scenario: LaunchTarget) => (targets: ScenarioTargets) => launchScenario(scenario.scenarioId, toTargetsInput(targets), false)
    .then(({ simulation_id }) => {
      onLaunched?.();
      openLaunchedSimulation(simulation_id, scenario.type);
    });

  const launchDrawer = checking && (
    <ScenarioLaunchCheckDrawer
      scenario={checking}
      onLaunch={launchWith(checking)}
      onClose={() => {
        setChecking(null);
        sharedCheck?.checkAgain();
      }}
      sharedCheck={sharedCheck}
    />
  );

  const launch = (scenario: LaunchTarget) => {
    sharedCheck?.checkAgain();
    setChecking(scenario);
  };

  return {
    launch,
    launchDrawer,
  };
};

export default useScenarioLaunch;
