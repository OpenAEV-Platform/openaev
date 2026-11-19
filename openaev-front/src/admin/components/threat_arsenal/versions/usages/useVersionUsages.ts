import { useEffect, useState } from 'react';

import { fetchThreatArsenalUsages } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsagesOutput } from '../../../../../utils/api-types';

/** Where one version runs: the usages of its action, null while loading, fetched again when `reloadKey` changes. */
const useVersionUsages = (actionId: string | undefined, reloadKey = 0) => {
  const [usages, setUsages] = useState<PayloadUsagesOutput | null>(null);

  useEffect(() => {
    setUsages(null);
    if (!actionId) return undefined;
    let cancelled = false;
    fetchThreatArsenalUsages(actionId).then(({ data }: { data: PayloadUsagesOutput }) => {
      if (!cancelled) setUsages(data);
    });
    return () => {
      cancelled = true;
    };
  }, [actionId, reloadKey]);

  return usages;
};

export default useVersionUsages;
