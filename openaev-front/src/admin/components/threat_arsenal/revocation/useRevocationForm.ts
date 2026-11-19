import { useEffect, useState } from 'react';

import { liftPayloadRevocation, revokePayload } from '../../../../actions/payloads/payload-revocation-actions';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useVersionUsages from '../versions/useVersionUsages';

interface Options {
  open: boolean;
  lift: boolean;
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  onDone: (version: PayloadVersionOutput) => void;
}

/** The revoke or lift form: the version chosen, what it stops, the reason, and the submit. */
const useRevocationForm = ({ open, lift, versions, currentPayloadId, onDone }: Options) => {
  const [selected, setSelected] = useState('');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setReason('');
      setSelected(versions.some(v => v.payload_id === currentPayloadId) ? currentPayloadId : versions[0]?.payload_id ?? '');
    }
  }, [open, versions, currentPayloadId]);

  const version = versions.find(v => v.payload_id === selected);
  const usages = useVersionUsages(open ? version?.payload_action_id : undefined);

  const submit = async () => {
    if (!version) return;
    setSubmitting(true);
    try {
      if (lift) {
        await liftPayloadRevocation(version.payload_id);
      } else {
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
    submitting,
    canSubmit: !!version && !submitting && (lift || reason.trim() !== ''),
    submit,
  };
};

export default useRevocationForm;
