import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import PayloadVersionSelect from './PayloadVersionSelect';

interface Props {
  actionId: string;
  value: string;
  onChange: (actionId: string) => void;
}

const PayloadContractVersionSelect: FunctionComponent<Props> = ({ actionId, value, onChange }) => {
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);

  useEffect(() => {
    // Silent: a user without access to the action keeps the version the inject has.
    fetchThreatArsenalVersions(actionId, false)
      .then(response => setVersions(response.data as PayloadVersionOutput[]))
      .catch(() => setVersions([]));
  }, [actionId]);

  const choices = versions.filter(version => version.payload_action_id === value || !version.payload_revoked);
  const current = choices.find(version => version.payload_action_id === value);
  if (!current || choices.length < 2) {
    return null;
  }
  return (
    <PayloadVersionSelect
      value={current.payload_id}
      versions={choices}
      onChange={(payloadId) => {
        const chosen = choices.find(version => version.payload_id === payloadId);
        if (chosen) {
          onChange(chosen.payload_action_id);
        }
      }}
    />
  );
};

export default PayloadContractVersionSelect;
