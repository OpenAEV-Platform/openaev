import { useEffect, useState } from 'react';

import { type CatalogHistoryOutput } from '../../../utils/api-types';

/** The history of one catalogue content, newest first; null while it loads. */
const useCatalogHistory = (fetchHistory: (id: string) => Promise<{ data: unknown }>, id: string) => {
  const [history, setHistory] = useState<CatalogHistoryOutput[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    setHistory(null);
    fetchHistory(id).then((response) => {
      if (!cancelled) {
        setHistory(response.data as CatalogHistoryOutput[]);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [id]);

  return history;
};

export default useCatalogHistory;
