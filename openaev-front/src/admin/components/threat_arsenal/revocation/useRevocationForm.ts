import { useEffect, useState } from 'react';

import { liftPayloadRevocation, revokePayload } from '../../../../actions/payloads/payload-revocation-actions';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import { moveTargets, moveUsages } from '../versions/usages/usageMove';
import useNotifyMoves from '../versions/usages/useNotifyMoves';
import useVersionUsages from '../versions/usages/useVersionUsages';

interface Options {
  open: boolean;
  lift: boolean;
  // Revoking, the other versions are where its injects can go.
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  onDone: (version: PayloadVersionOutput) => void;
}

/** The revoke or lift form: the version chosen, what it stops, where its injects go, and the submit. */
const useRevocationForm = ({ open, lift, versions, currentPayloadId, onDone }: Options) => {
  const notifyMoves = useNotifyMoves();
  const [selected, setSelected] = useState('');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [replace, setReplace] = useState(true);
  const [replacement, setReplacement] = useState('');

  useEffect(() => {
    if (open) {
      setReason('');
      setSelected(versions.some(v => v.payload_id === currentPayloadId) ? currentPayloadId : versions[0]?.payload_id ?? '');
    }
  }, [open, versions, currentPayloadId]);

  const version = versions.find(v => v.payload_id === selected);
  const usages = useVersionUsages(open ? version?.payload_action_id : undefined);

  const { targets: replacements, defaultTarget } = moveTargets(versions, selected);
  useEffect(() => setReplacement(defaultTarget), [defaultTarget]);
  const shown = usages?.payload_usages ?? [];
  const canReplace = !lift && shown.length > 0 && replacements.length > 0;
  const target = replacements.find(v => v.payload_id === replacement);

  // The injects move first, so none of them fails between the revocation and the move.
  const moveFirst = async (source: PayloadVersionOutput) => {
    if (!canReplace || !replace || !target) return;
    notifyMoves([await moveUsages(source.payload_action_id, target.payload_id, shown)], target.payload_version);
  };

  const submit = async () => {
    if (!version) return;
    setSubmitting(true);
    try {
      if (lift) {
        await liftPayloadRevocation(version.payload_id);
      } else {
        await moveFirst(version);
        await revokePayload(version.payload_id, { payload_revocation_reason: reason.trim() });
      }
      onDone(version);
    } finally {
      setSubmitting(false);
    }
  };

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
    canSubmit: !!version && !submitting && (lift || reason.trim() !== ''),
    submit,
  };
};

export default useRevocationForm;
