import { useEffect, useState } from 'react';

import type { InjectResultPayloadExecutionOutput, InjectTarget } from '../../utils/api-types';
import { fetchInjectExecutionResult } from './inject-status-action';

// `target` may be null while the caller is still resolving which target to show
// (e.g. the attack-path panel resolves it from a search): the fetch simply waits
// until a target with an id and type is provided.
const useFetchInjectExecutionResult = (injectId: string, target: InjectTarget | null) => {
  const [injectExecutionResult, setInjectExecutionResult] = useState<InjectResultPayloadExecutionOutput>();
  const [loading, setLoading] = useState(false);
  const fetch = async () => {
    if (!injectId || !target?.target_id || !target.target_type) return;
    setLoading(true);
    try {
      const result = await fetchInjectExecutionResult(injectId, target.target_id, target.target_type);
      setInjectExecutionResult(result.data || undefined);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    fetch();
  }, [injectId, target?.target_id, target?.target_type]);
  return ({
    injectExecutionResult,
    loading,
  });
};

export default useFetchInjectExecutionResult;
