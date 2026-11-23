import { useEffect, useState } from 'react';

import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';

// A version never changes once saved: its details are loaded once, then shared by every row that shows it.
const loaded = new Map<string, Promise<ThreatArsenalActionFullOutput>>();

const loadVersionDetails = (actionId: string) => {
  let details = loaded.get(actionId);
  if (!details) {
    details = fetchThreatArsenalAction(actionId).then(({ data }: { data: ThreatArsenalActionFullOutput }) => data);
    // A failed load is tried again next time.
    details.catch(() => loaded.delete(actionId));
    loaded.set(actionId, details);
  }
  return details;
};

/** The full details of one version of a payload, by its action; null while they load. */
const useVersionDetails = (actionId: string | undefined) => {
  const [details, setDetails] = useState<ThreatArsenalActionFullOutput | null>(null);
  useEffect(() => {
    let active = true;
    setDetails(null);
    if (actionId) {
      loadVersionDetails(actionId)
        .then((value) => {
          if (active) setDetails(value);
        })
        .catch(() => undefined);
    }
    return () => {
      active = false;
    };
  }, [actionId]);
  return details;
};

export default useVersionDetails;
