import { useEffect, useState } from 'react';

import { fetchThreatArsenalUsages } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

/** The usages of every version, by payload id; null until all are loaded. */
const useVersionsUsages = (versions: PayloadVersionOutput[] | null) => {
  const [usages, setUsages] = useState<Record<string, PayloadUsagesOutput> | null>(null);

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
  }, [versions]);

  return usages;
};

export default useVersionsUsages;
