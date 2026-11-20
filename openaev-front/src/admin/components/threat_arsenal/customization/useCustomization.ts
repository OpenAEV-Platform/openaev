import { useEffect, useState } from 'react';

import { fetchThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadCustomizationOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

/** The Filigran version a customized copy is built from, and the update it can take. */
const useCustomization = (action: ThreatArsenalAction | null) => {
  const [customization, setCustomization] = useState<PayloadCustomizationOutput | null>(null);
  const actionId = action?.injector_contract_id;
  const customized = isFeatureEnabled('CATALOG') && !!action?.action_payload_customized;

  useEffect(() => {
    setCustomization(null);
    if (customized && actionId) {
      fetchThreatArsenalCustomization(actionId)
        .then(({ data }: { data: PayloadCustomizationOutput }) => setCustomization(data));
    }
  }, [actionId, customized]);

  return customization;
};

export default useCustomization;
