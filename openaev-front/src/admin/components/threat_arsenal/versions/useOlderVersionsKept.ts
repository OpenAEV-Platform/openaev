import { useEffect, useState } from 'react';

import { keepThreatArsenalOlderVersions, showThreatArsenalOlderVersions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadVersionOutput } from '../../../../utils/api-types';

/** "Keep them on their version" for the latest version: its card shows no banner until a newer version. */
const useOlderVersionsKept = (latest: PayloadVersionOutput | undefined) => {
  const [kept, setKept] = useState(!!latest?.payload_older_versions_kept);

  useEffect(() => setKept(!!latest?.payload_older_versions_kept), [latest]);

  const toggle = () => {
    if (!latest) return;
    const call = kept ? showThreatArsenalOlderVersions : keepThreatArsenalOlderVersions;
    call(latest.payload_action_id).then(() => setKept(!kept));
  };

  return {
    kept,
    toggle,
  };
};

export default useOlderVersionsKept;
