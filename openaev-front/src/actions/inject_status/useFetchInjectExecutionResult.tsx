import { useEffect, useState } from 'react';

import type { InjectResultPayloadExecutionOutput, InjectTarget } from '../../utils/api-types';
import { fetchInjectExecutionResult } from './inject-status-action';

// `target` may be null while the caller is still resolving which target to show
// (e.g. the attack-path panel resolves it from a search): the fetch simply waits
// until a target with an id and type is provided.
//
// `undefined` means the fetch has not settled yet; a settled fetch with no result (empty payload or
// a failed request) lands on `null`, so a consumer can tell "still in flight" from "resolved empty"
// (the attack-path execution-status badge keys its fallback rendering on that distinction).
const useFetchInjectExecutionResult = (injectId: string, target: InjectTarget | null) => {
  const [injectExecutionResult, setInjectExecutionResult] = useState<InjectResultPayloadExecutionOutput | null>();
  const [loading, setLoading] = useState(false);
  const fetch = async () => {
    if (!injectId || !target?.target_id || !target.target_type) return;
    setLoading(true);
    try {
      const result = await fetchInjectExecutionResult(injectId, target.target_id, target.target_type);
      setInjectExecutionResult(result.data ?? null);
    } catch {
      setInjectExecutionResult(null);
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
