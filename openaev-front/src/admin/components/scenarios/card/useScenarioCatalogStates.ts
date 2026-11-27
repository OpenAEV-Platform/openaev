import { useEffect, useRef, useState } from 'react';

import { fetchScenarioCatalogStates } from '../../../../actions/scenarios/scenario-update-actions';
import { type ScenarioCatalogStateOutput } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

/** The catalogue state of each listed scenario installed from the catalogue: one call for those newly listed. */
const useScenarioCatalogStates = (scenarioIds: string[]) => {
  const catalog = isFeatureEnabled('CATALOG');
  const [states, setStates] = useState<Record<string, ScenarioCatalogStateOutput>>({});
  const listed = useRef<Set<string>>(new Set());

  useEffect(() => {
    const newlyListed = scenarioIds.filter(scenarioId => !listed.current.has(scenarioId));
    listed.current = new Set(scenarioIds);
    if (!catalog || newlyListed.length === 0) return;
    fetchScenarioCatalogStates(newlyListed)
      .then(data => setStates(previous => ({
        ...previous,
        ...data,
      })))
      .catch(() => undefined);
  }, [catalog, scenarioIds.join()]);

  return states;
};

export default useScenarioCatalogStates;
