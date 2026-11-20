import { fetchThreatArsenalAction, fetchThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadCustomizationOutput, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { buildInitialValues } from '../utils/actionFormValues';

/** What a customized copy is built from: its customization, and the form values of its Filigran version. */
const fetchFiligranValues = async (actionId: string) => {
  const { data: customization }: { data: PayloadCustomizationOutput } = await fetchThreatArsenalCustomization(actionId);
  const { data: base }: { data: ThreatArsenalActionFullOutput } = await fetchThreatArsenalAction(customization.customization_base_action_id);
  return {
    customization,
    values: buildInitialValues(base, ''),
  };
};

export default fetchFiligranValues;
