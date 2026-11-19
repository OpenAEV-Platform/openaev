import { useEffect, useState } from 'react';

import { fetchThreatArsenalUsages, fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';

/** The versions of an action's payload, newest first, and the usages of each one. */
const usePayloadVersions = (actionId: string) => {
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [usages, setUsages] = useState<Record<string, PayloadUsagesOutput>>({});

  const load = () => fetchThreatArsenalVersions(actionId).then((response) => {
    const loaded = response.data as PayloadVersionOutput[];
    setVersions(loaded);
    loaded.forEach(version => fetchThreatArsenalUsages(version.payload_action_id).then(result => setUsages(previous => ({
      ...previous,
      [version.payload_id]: result.data as PayloadUsagesOutput,
    }))));
  });

  useEffect(() => {
    setVersions(null);
    setUsages({});
    load();
  }, [actionId]);

  return {
    versions,
    usages,
  };
};

export default usePayloadVersions;
