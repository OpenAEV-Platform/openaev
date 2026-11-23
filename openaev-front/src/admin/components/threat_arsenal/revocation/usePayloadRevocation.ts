import { useState } from 'react';

import { type PayloadVersionOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import usePayloadVersions from '../versions/usePayloadVersions';

type RevocationKind = 'revoke' | 'lift';

/**
 * Revoking or lifting a version of the action's payload: the versions each one can take, loaded when
 * the menu opens, and what follows it (the card follows the version it shows).
 */
const usePayloadRevocation = (action: ThreatArsenalAction, menuOpen: boolean, onDone: (changed: ThreatArsenalAction) => void) => {
  const ability = useAbility();
  const payloadId = action.action_payload?.payload_id;
  const canRevoke = isFeatureEnabled('CATALOG') && !!payloadId && ability.can(ACTIONS.REVOKE, SUBJECTS.THREAT_ARSENALS);
  const versions = usePayloadVersions(action.injector_contract_id, menuOpen && canRevoke) ?? [];
  const [revocation, setRevocation] = useState<RevocationKind | null>(null);

  const revocable = versions.filter(v => !v.payload_revoked);
  // A revocation from Filigran cannot be lifted by the tenant.
  const liftable = versions.filter(v => v.payload_revoked && !v.payload_revoked_by_catalogue);

  const revocationDone = (version: PayloadVersionOutput) => {
    const changed = version.payload_id === payloadId
      ? {
          ...action,
          action_payload_revoked: revocation === 'revoke',
        }
      : action;
    setRevocation(null);
    onDone(changed);
  };

  return {
    canRevoke,
    revocable: canRevoke ? revocable : [],
    liftable: canRevoke ? liftable : [],
    revocation,
    openRevocation: (kind: RevocationKind) => setRevocation(kind),
    closeRevocation: () => setRevocation(null),
    revocationDone,
  };
};

export default usePayloadRevocation;
