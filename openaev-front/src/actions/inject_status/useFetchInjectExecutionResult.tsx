import { useEffect, useState } from 'react';

import type { InjectExecutionPayloadOutput, InjectTarget } from '../../utils/api-types';
import { fetchInjectExecutionResult } from './inject-status-action';

const useFetchInjectExecutionResult = (injectId: string, target: InjectTarget) => {
  const [injectExecutionResult, setInjectExecutionResult] = useState<InjectExecutionPayloadOutput>();
  const [loading, setLoading] = useState(false);
  const fetch = async () => {
    if (!injectId || !target?.target_id || !target.target_type) return;
    setLoading(true);
    try {
      const result = await fetchInjectExecutionResult(injectId, target.target_id, target.target_type);
      setInjectExecutionResult(result.data || []);
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
