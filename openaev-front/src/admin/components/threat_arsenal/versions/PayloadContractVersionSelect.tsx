import { type FunctionComponent, useEffect, useState } from 'react';

import { directFetchInjectorContract } from '../../../../actions/InjectorContracts';
import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type InjectorContract, type PayloadVersionOutput } from '../../../../utils/api-types';
import { isFeatureEnabled, isNotEmptyField } from '../../../../utils/utils';
import PayloadVersionSelect from './PayloadVersionSelect';

interface Props {
  action: Pick<InjectorContract, 'injector_contract_id' | 'injector_contract_payload'>;
  value: string;
  onChange: (action: InjectorContract) => void;
}

// The installed, unrevoked versions of the action's payload; the one in use stays listed even when revoked.
const PayloadContractVersionSelect: FunctionComponent<Props> = ({ action, value, onChange }) => {
  const enabled = isFeatureEnabled('CATALOG') && isNotEmptyField(action.injector_contract_payload);
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);

  useEffect(() => {
    if (enabled && !versions.some(version => version.payload_action_id === action.injector_contract_id)) {
      // Silent: a user without access to the action keeps the version the inject has.
      fetchThreatArsenalVersions(action.injector_contract_id, false)
        .then(response => setVersions(response.data as PayloadVersionOutput[]))
        .catch(() => setVersions([]));
    }
  }, [enabled, action.injector_contract_id]);

  const choices = versions.filter(version => version.payload_action_id === value || !version.payload_revoked);
  const current = choices.find(version => version.payload_action_id === value);
  if (!enabled || !current || choices.length < 2) {
    return null;
  }
  const choose = (payloadId: string) => {
    const chosen = choices.find(version => version.payload_id === payloadId);
    if (chosen) {
      directFetchInjectorContract(chosen.payload_action_id).then((result: { data: InjectorContract }) => onChange(result.data));
    }
  };
  return <PayloadVersionSelect value={current.payload_id} versions={choices} onChange={choose} />;
};

export default PayloadContractVersionSelect;
