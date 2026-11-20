import { useEffect, useState } from 'react';

import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';

/** The full details of an action, loaded each time its id changes; nothing without id. */
const useActionDetails = (actionId: string | undefined) => {
  const [details, setDetails] = useState<ThreatArsenalActionFullOutput | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setDetails(null);
    setLoading(!!actionId);
    if (!actionId) return undefined;
    // A superseded call must not overwrite the details or the spinner of the newer one.
    let cancelled = false;
    fetchThreatArsenalAction(actionId)
      .then(({ data }: { data: ThreatArsenalActionFullOutput }) => {
        if (!cancelled) setDetails(data);
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [actionId]);

  return {
    details,
    loading,
  };
};

export default useActionDetails;
