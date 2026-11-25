import { useCallback, useEffect, useState } from 'react';

import { fetchScenarioRevisions, fetchScenarioVersions, searchScenarioHistory } from '../../../../../actions/scenarios/scenario-update-actions';
import useContentHistory from '../../../../../components/common/content_history/useContentHistory';
import { type ScenarioRevisionOutput, type ScenarioVersionsOutput } from '../../../../../utils/api-types';

// The catalogue versions of a scenario, the revisions it kept and its history, reloaded after an update or a rollback; null while they load.
const useScenarioVersions = (scenarioId: string) => {
  const [versions, setVersions] = useState<ScenarioVersionsOutput | null>(null);
  const [revisions, setRevisions] = useState<ScenarioRevisionOutput[] | null>(null);
  const [loads, setLoads] = useState(0);
  const history = useContentHistory(searchScenarioHistory, scenarioId, loads);

  const load = useCallback(() => {
    fetchScenarioVersions(scenarioId).then(setVersions);
    fetchScenarioRevisions(scenarioId).then(setRevisions);
  }, [scenarioId]);
  useEffect(load, [load]);

  const reload = () => {
    load();
    setLoads(count => count + 1);
  };

  return {
    versions,
    revisions,
    history,
    reload,
  };
};

export default useScenarioVersions;
