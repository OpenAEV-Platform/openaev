import { useEffect, useState } from 'react';

import { searchTargets } from '../../../../../actions/injects/inject-action';
import type { InjectTarget } from '../../../../../utils/api-types';

// The attack-path DTOs expose `injectId` but not the executed target, so resolve the inject's asset
// targets and pick the one matching this execution's endpoint (falling back to the first asset).
// Shared by the live terminal view and the payload execution-status badge — each mounts
// independently, so each resolution runs when its consumer renders.
const useResolvedAssetTarget = (injectId: string, endpointName?: string) => {
  const [target, setTarget] = useState<InjectTarget | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    searchTargets(injectId, 'ASSETS', {
      filterGroup: {
        mode: 'and',
        filters: [],
      },
      size: 50,
      page: 0,
    })
      .then((response) => {
        if (!active) {
          return;
        }
        const targets: InjectTarget[] = response.data?.content ?? [];
        const match = targets.find(tg => tg.target_name && tg.target_name === endpointName);
        setTarget(match ?? targets[0] ?? null);
      })
      .catch(() => active && setTarget(null))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [injectId, endpointName]);
  return {
    target,
    loading,
  };
};

export default useResolvedAssetTarget;
