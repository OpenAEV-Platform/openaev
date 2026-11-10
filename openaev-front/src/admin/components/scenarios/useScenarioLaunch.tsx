import { useState } from 'react';
import { useNavigate } from 'react-router';

import { createRunningExerciseFromScenario } from '../../../actions/scenarios/scenario-actions';
import { type Exercise } from '../../../utils/api-types';
import { type LaunchTarget } from './launch/scenarioLaunchBlockers';
import ScenarioLaunchCheckDrawer from './launch/ScenarioLaunchCheckDrawer';

// Launches a scenario from a card through the launch check (the same one as the scenario page):
// every blocker shows with its fix, then the simulation starts and opens.
const useScenarioLaunch = () => {
  const navigate = useNavigate();
  const [checking, setChecking] = useState<LaunchTarget | null>(null);

  const launch = (target: LaunchTarget) => setChecking(target);

  const launchDrawer = checking && (
    <ScenarioLaunchCheckDrawer
      scenario={checking}
      open
      onClose={() => setChecking(null)}
      onLaunch={() => createRunningExerciseFromScenario(checking.scenario_id)
        .then(({ data: exercise }: { data: Exercise }) => navigate(`/admin/simulations/${exercise.exercise_id}`))}
    />
  );

  return {
    launch,
    launchDrawer,
  };
};

export default useScenarioLaunch;
