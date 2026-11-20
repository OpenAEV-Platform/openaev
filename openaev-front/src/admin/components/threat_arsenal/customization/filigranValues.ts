import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { fetchThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-customization-actions';
import { type PayloadCustomizationOutput, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { buildInitialValues } from '../utils/actionFormValues';

/** The form values of the Filigran version a customized copy is built from. */
const fetchFiligranValues = async (actionId: string) => {
  const { data: customization }: { data: PayloadCustomizationOutput } = await fetchThreatArsenalCustomization(actionId);
  const { data: base }: { data: ThreatArsenalActionFullOutput } = await fetchThreatArsenalAction(customization.customization_base_action_id);
  return buildInitialValues(base, '');
};

export default fetchFiligranValues;
