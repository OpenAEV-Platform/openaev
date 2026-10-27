import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalVersions } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { type PayloadVersionOutput } from '../../../utils/api-types';
import PayloadVersionSelect from './PayloadVersionSelect';

interface Props {
  contractId: string;
  onChange: (version: PayloadVersionOutput) => void;
  disabled?: boolean;
}

// The versions of the contract's payload an inject can run: installed and not revoked.
const PayloadContractVersionSelect: FunctionComponent<Props> = ({ contractId, onChange, disabled = false }) => {
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetchThreatArsenalVersions(contractId)
      .then((response: { data: PayloadVersionOutput[] }) => setVersions(response.data))
      .catch(() => setVersions([]))
      .finally(() => setLoading(false));
  }, [contractId]);

  const current = versions.find(v => v.payload_current);
  const usable = versions.filter(v => v.payload_version && v.payload_installed && !v.payload_revoked && v.payload_action_id);
  if (!loading && !current) {
    return null;
  }
  return (
    <PayloadVersionSelect
      value={current?.payload_version ?? ''}
      options={usable.map(v => ({
        version: v.payload_version as string,
        latest: v.payload_is_latest,
        installed: true,
      }))}
      onChange={(version) => {
        const target = usable.find(v => v.payload_version === version);
        if (target && !target.payload_current) {
          onChange(target);
        }
      }}
      loading={loading || disabled}
    />
  );
};

export default PayloadContractVersionSelect;
