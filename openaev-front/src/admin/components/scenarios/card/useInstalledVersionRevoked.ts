import { useEffect, useState } from 'react';

import { fetchScenarioVersions } from '../../../../actions/scenarios/scenario-update-actions';

/**
 * Whether the catalogue revoked the version a scenario is installed on. Read only for a scenario with an
 * update to offer, which is the way out: one call per such card.
 */
const useInstalledVersionRevoked = (scenarioId: string, enabled: boolean) => {
  const [revoked, setRevoked] = useState(false);
  useEffect(() => {
    if (!enabled) {
      setRevoked(false);
      return;
    }
    fetchScenarioVersions(scenarioId)
      .then(versions => setRevoked(versions.scenario_versions.some(version => version.scenario_version_current
        && version.scenario_version_status === 'REVOKED')))
      .catch(() => setRevoked(false));
  }, [scenarioId, enabled]);
  return revoked;
};

export default useInstalledVersionRevoked;
