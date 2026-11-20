import { useCallback, useEffect, useState } from 'react';

import { fetchScenarioRevisions, fetchScenarioVersions } from '../../../../../actions/scenarios/scenario-update-actions';
import { type ScenarioRevisionOutput, type ScenarioVersionsOutput } from '../../../../../utils/api-types';

// The catalogue versions of a scenario and the revisions it kept, reloaded after an update or a rollback.
const useScenarioVersions = (scenarioId: string) => {
  const [versions, setVersions] = useState<ScenarioVersionsOutput | null>(null);
  const [revisions, setRevisions] = useState<ScenarioRevisionOutput[]>([]);

  const load = useCallback(() => {
    fetchScenarioVersions(scenarioId).then(setVersions);
    fetchScenarioRevisions(scenarioId).then(setRevisions);
  }, [scenarioId]);
  useEffect(load, [load]);

  return {
    versions,
    revisions,
    load,
  };
};

export default useScenarioVersions;
