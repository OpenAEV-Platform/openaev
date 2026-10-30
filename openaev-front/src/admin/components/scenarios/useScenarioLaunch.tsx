import { useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchScenarioTargets } from '../../../actions/catalog/scenario-catalog-actions';
import { createRunningExerciseFromScenario } from '../../../actions/scenarios/scenario-actions';
import { type Exercise } from '../../../utils/api-types';
import ScenarioLaunchTargetsDrawer from './catalog/ScenarioLaunchTargetsDrawer';

// Launches a scenario and opens its simulation; an inject without target asks for one first.
const useScenarioLaunch = () => {
  const navigate = useNavigate();
  const [askTargets, setAskTargets] = useState<{
    scenarioId: string;
    scenarioName: string;
  } | null>(null);

  const launch = (scenarioId: string, scenarioName: string) => fetchScenarioTargets(scenarioId).then(({ data }) => {
    if (data.injects.some(inject => !inject.is_targeted)) {
      setAskTargets({
        scenarioId,
        scenarioName,
      });
      return;
    }
    createRunningExerciseFromScenario(scenarioId)
      .then(({ data: exercise }: { data: Exercise }) => navigate(`/admin/simulations/${exercise.exercise_id}`));
  });

  const targetsDrawer = askTargets && (
    <ScenarioLaunchTargetsDrawer
      scenarioId={askTargets.scenarioId}
      scenarioName={askTargets.scenarioName}
      onClose={() => setAskTargets(null)}
    />
  );

  return {
    launch,
    targetsDrawer,
  };
};

export default useScenarioLaunch;
