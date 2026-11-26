import { useEffect, useState } from 'react';

import { fetchThreatArsenalUsages } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

/** The usages of each version, by payload id: null until all are loaded, loaded again when `reloadKey` changes. */
const useUsagesByVersion = (versions: PayloadVersionOutput[] | null, reloadKey = 0) => {
  const [usages, setUsages] = useState<Record<string, PayloadUsagesOutput> | null>(null);
  const loadedIds = versions?.map(version => version.payload_id).join();

  useEffect(() => {
    setUsages(null);
    if (!versions) return undefined;
    let cancelled = false;
    Promise.all(versions.map(version => fetchThreatArsenalUsages(version.payload_action_id)
      .then(({ data }: { data: PayloadUsagesOutput }) => data)
      // The call already tells the user it failed: the version shows no usage instead of loading forever.
      .catch((): PayloadUsagesOutput => ({ payload_usages: [] }))
      .then(versionUsages => [version.payload_id, versionUsages] as const)))
      .then((loaded) => {
        if (!cancelled) setUsages(Object.fromEntries(loaded));
      });
    return () => {
      cancelled = true;
    };
  }, [loadedIds, reloadKey]);

  return usages;
};

export default useUsagesByVersion;
