import { fetchThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadCustomizationOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import useLoadedOnKey from '../../../../utils/hooks/useLoadedOnKey';
import { isCustomizedCopy } from '../utils/threatArsenalStatusUtils';

const loadCustomization = (actionId: string) => fetchThreatArsenalCustomization(actionId)
  .then(({ data }: { data: PayloadCustomizationOutput }) => data);

/** The Filigran version a customized copy is built from, and the update it can take; null for any other action. */
const useCustomization = (action: ThreatArsenalAction | null) => {
  return useLoadedOnKey<PayloadCustomizationOutput | null>(isCustomizedCopy(action) ? action?.injector_contract_id : undefined, loadCustomization, null).value;
};

export default useCustomization;
