import { useEffect, useState } from 'react';

import { fetchCapabilities } from '../../actions/capabilities/capability-action';
import type { CapabilityOutput } from '../api-types';
import { type CapabilityScope } from '../permissions/types';

// The capability tree is static reference data, already ordered by the backend. It is deliberately
// kept out of the normalized store, whose Immutable entity maps do not preserve the API order.
const useCapabilities = (scope: CapabilityScope) => {
  const [capabilities, setCapabilities] = useState<CapabilityOutput[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let stale = false;
    setLoading(true);
    fetchCapabilities(scope)
      .then((response) => {
        if (!stale) {
          setCapabilities(response.data as CapabilityOutput[]);
        }
      })
      .catch(() => {
        if (!stale) {
          setCapabilities([]);
        }
      })
      .finally(() => {
        if (!stale) {
          setLoading(false);
        }
      });
    return () => {
      stale = true;
    };
  }, [scope]);

  return {
    capabilities,
    loading,
  };
};

export default useCapabilities;
