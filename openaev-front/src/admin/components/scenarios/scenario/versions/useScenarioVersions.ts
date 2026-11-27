import { useState } from 'react';

import { fetchScenarioRevisions, fetchScenarioVersions, searchScenarioHistory } from '../../../../../actions/scenarios/scenario-update-actions';
import useContentHistory from '../../../../../components/common/content_history/useContentHistory';
import { type ScenarioRevisionOutput, type ScenarioVersionsOutput } from '../../../../../utils/api-types';
import useLoadedOnKey from '../../../../../utils/hooks/useLoadedOnKey';

// The catalogue versions of a scenario, the revisions it kept and its history, reloaded after an update or a rollback; null while they load or when they failed.
const useScenarioVersions = (scenarioId: string) => {
  const [loads, setLoads] = useState(0);
  const key = `${scenarioId}#${loads}`;
  const versions = useLoadedOnKey<ScenarioVersionsOutput | null>(key, () => fetchScenarioVersions(scenarioId), null);
  const revisions = useLoadedOnKey<ScenarioRevisionOutput[] | null>(key, () => fetchScenarioRevisions(scenarioId), null);
  const history = useContentHistory(searchScenarioHistory, scenarioId, loads);

  return {
    versions: versions.value,
    revisions: revisions.value,
    failed: (!versions.loading && versions.value === null) || (!revisions.loading && revisions.value === null),
    history,
    reload: () => setLoads(count => count + 1),
  };
};

export default useScenarioVersions;
