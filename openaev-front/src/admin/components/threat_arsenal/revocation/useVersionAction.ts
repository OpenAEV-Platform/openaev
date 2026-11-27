import { useEffect, useState } from 'react';

import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useUsagesByVersion from '../versions/usages/useUsagesByVersion';

interface Options {
  open: boolean;
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  onDone: (version: PayloadVersionOutput) => void;
}

/** The version a revoke or lift dialog acts on, the current one first, what uses it, and the submit. */
const useVersionAction = ({ open, versions, currentPayloadId, onDone }: Options) => {
  const [selected, setSelected] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setSelected(versions.some(v => v.payload_id === currentPayloadId) ? currentPayloadId : versions[0]?.payload_id ?? '');
    }
  }, [open, versions, currentPayloadId]);

  const version = versions.find(v => v.payload_id === selected);
  const usages = useUsagesByVersion(open && version ? [version] : [])[selected] ?? null;

  const submit = async (act: (chosen: PayloadVersionOutput) => Promise<unknown>) => {
    if (!version) return;
    setSubmitting(true);
    try {
      await act(version);
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
    submitting,
    submit,
  };
};

export default useVersionAction;
