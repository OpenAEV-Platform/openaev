import { useEffect, useState } from 'react';

import { type PayloadVersionOutput, type ThreatArsenalAction } from '../../../../utils/api-types';

/**
 * The version an action's drawer shows: the opened one first, then the one picked, installed (by payload
 * id) or only in the catalogue (by version).
 */
const useShownVersion = (action: ThreatArsenalAction | null, versions: PayloadVersionOutput[] | null) => {
  const openedId = action?.action_payload?.payload_id ?? '';
  const [shownId, setShownId] = useState(openedId);
  useEffect(() => setShownId(openedId), [action]);

  const installed = versions?.find(version => version.payload_id === shownId);
  const opened = shownId === openedId;
  // Another installed version is another action: its status comes with its details.
  const shownAction: ThreatArsenalAction | null = action?.action_payload && installed && !opened
    ? {
        ...action,
        injector_contract_id: installed.payload_action_id,
        action_payload: {
          ...action.action_payload,
          payload_id: installed.payload_id,
          payload_status: undefined,
        },
        action_payload_version: installed.payload_version,
        action_payload_revoked: installed.payload_revoked,
      }
    : action;

  return {
    shownId,
    show: setShownId,
    installed,
    shownAction: installed || opened ? shownAction : null,
    notInstalledVersion: installed || opened ? undefined : shownId,
  };
};

export default useShownVersion;
