import { useCallback, useEffect, useRef, useState } from 'react';

import { callAgentStream } from './agentApi';

interface UseAgentStreamReturn {
  content: string;
  setContent: (content: string) => void;
  loading: boolean;
  error: string | undefined;
  execute: (agentSlug: string, prompt: string) => void;
  abort: () => void;
}

/**
 * React hook to stream an agent call via SSE.
 * Manages content accumulation, loading state, errors, and abort.
 */
const useAgentStream = (): UseAgentStreamReturn => {
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>();
  const abortRef = useRef<AbortController | null>(null);

  const abort = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const execute = useCallback((agentSlug: string, prompt: string) => {
    // Abort any existing stream
    abort();

    const controller = new AbortController();
    abortRef.current = controller;

    setContent('');
    setLoading(true);
    setError(undefined);

    callAgentStream(
      agentSlug,
      prompt,
      partialContent => setContent(partialContent),
      controller.signal,
    )
      .then((result) => {
        if (result.status === 'error') {
          setError(result.error ?? 'Unknown error');
        }
      })
      .catch((err) => {
        if (err.name !== 'AbortError') {
          setError(err.message ?? 'Stream failed');
        }
      })
      .finally(() => {
        setLoading(false);
        abortRef.current = null;
      });
  }, [abort]);

  // Abort on unmount
  useEffect(() => () => abort(), [abort]);

  return {
    content,
    setContent,
    loading,
    error,
    execute,
    abort,
  };
};

export default useAgentStream;
