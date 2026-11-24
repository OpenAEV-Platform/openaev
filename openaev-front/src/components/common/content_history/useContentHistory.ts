import { useEffect, useState } from 'react';

import { type ContentChangeOutput } from '../../../utils/api-types';

/** The history of one catalogue content, newest first; null while it loads. A new `reload` value reads it again. */
const useContentHistory = (fetchHistory: (id: string) => Promise<{ data: unknown }>, id: string, reload = 0) => {
  const [history, setHistory] = useState<ContentChangeOutput[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    setHistory(null);
    fetchHistory(id).then((response) => {
      if (!cancelled) {
        setHistory(response.data as ContentChangeOutput[]);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [id, reload]);

  return history;
};

export default useContentHistory;
