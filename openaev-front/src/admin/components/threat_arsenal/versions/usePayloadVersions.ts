import { useEffect, useState } from 'react';

import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadVersionOutput } from '../../../../utils/api-types';

/** The versions of an action's payload, newest first, fetched again each time `load` turns on or `reloadKey` changes; null while loading. */
const usePayloadVersions = (actionId: string, load: boolean, reloadKey = 0) => {
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);

  useEffect(() => {
    if (!load) return undefined;
    setVersions(null);
    let cancelled = false;
    fetchThreatArsenalVersions(actionId)
      .then(({ data }: { data: PayloadVersionOutput[] }) => {
        if (!cancelled) setVersions(data);
      })
      // The call already tells the user it failed: no version instead of loading forever.
      .catch(() => {
        if (!cancelled) setVersions([]);
      });
    return () => {
      cancelled = true;
    };
  }, [actionId, load, reloadKey]);

  return versions;
};

export default usePayloadVersions;
