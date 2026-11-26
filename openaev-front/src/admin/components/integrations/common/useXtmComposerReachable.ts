import { useEffect, useState } from 'react';

import { isXtmComposerIsReachable } from '../../../../actions/catalog/catalog-actions';

// Null while the probe runs, so a warning about it does not flash.
const useXtmComposerReachable = (): boolean | null => {
  const [reachable, setReachable] = useState<boolean | null>(null);
  useEffect(() => {
    isXtmComposerIsReachable()
      .then(({ data }) => setReachable(data))
      .catch(() => setReachable(false));
  }, []);
  return reachable;
};

export default useXtmComposerReachable;
