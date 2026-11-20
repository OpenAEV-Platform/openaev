import { useEffect, useState } from 'react';

import { fetchThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadCustomizationOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

/** The Filigran version a customized copy is built from, and the update it can take. */
const useCustomization = (action: ThreatArsenalAction) => {
  const [customization, setCustomization] = useState<PayloadCustomizationOutput | null>(null);
  const customized = isFeatureEnabled('CATALOG') && !!action.action_payload_customized;

  useEffect(() => {
    setCustomization(null);
    if (customized) {
      fetchThreatArsenalCustomization(action.injector_contract_id)
        .then(({ data }: { data: PayloadCustomizationOutput }) => setCustomization(data));
    }
  }, [action.injector_contract_id, customized]);

  return customization;
};

export default useCustomization;
