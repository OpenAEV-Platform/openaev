import { useEffect, useState } from 'react';

import { revokePayload } from '../../../../actions/payloads/payload-revocation-actions';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import { moveTargets, moveUsages } from '../versions/usages/usageMove';
import useNotifyMoves from '../versions/usages/useNotifyMoves';
import useVersionAction from './useVersionAction';

interface Options {
  open: boolean;
  // The other versions are where the injects of the revoked one can go.
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  onDone: (version: PayloadVersionOutput) => void;
}

/** The revoke form: the version chosen, what it stops, where its injects go, the reason and the submit. */
const useRevokeForm = (options: Options) => {
  const notifyMoves = useNotifyMoves();
  const { selected, setSelected, version, usages, submitting, submit } = useVersionAction(options);
  const [reason, setReason] = useState('');
  const [replace, setReplace] = useState(true);
  const [replacement, setReplacement] = useState('');

  useEffect(() => {
    if (options.open) setReason('');
  }, [options.open]);

  const { targets: replacements, defaultTarget } = moveTargets(options.versions, selected);
  useEffect(() => setReplacement(defaultTarget), [defaultTarget]);
  const shown = usages?.payload_usages ?? [];
  const canReplace = shown.length > 0 && replacements.length > 0;
  const target = replacements.find(v => v.payload_id === replacement);

  // The injects move first, so none of them fails between the revocation and the move.
  const revoke = () => submit(async (chosen) => {
    if (canReplace && replace && target) {
      notifyMoves([await moveUsages(chosen.payload_action_id, target.payload_id, shown)], target.payload_version);
    }
    await revokePayload(chosen.payload_id, { payload_revocation_reason: reason.trim() });
  });

  return {
    selected,
    setSelected,
    version,
    usages,
    reason,
    setReason,
    canReplace,
    replace,
    setReplace,
    replacement,
    setReplacement,
    replacements,
    submitting,
    // Until the injects have loaded, the move would be skipped and the revocation would leave them failing.
    canSubmit: !!version && !submitting && usages !== null && reason.trim() !== '',
    moving: canReplace && replace,
    moveCount: shown.length,
    revoke,
  };
};

export default useRevokeForm;
