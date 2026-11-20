import { useEffect, useState } from 'react';

import { fetchThreatArsenalUsages } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsagesOutput } from '../../../../../utils/api-types';

/** Where one version runs: the usages of its action, null while loading. */
const useVersionUsages = (actionId: string | undefined) => {
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
  }, [actionId]);

  return usages;
};

export default useVersionUsages;
