import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadCustomizationOutput, type ThreatArsenalAction, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import useLoadedOnKey from '../../../../utils/hooks/useLoadedOnKey';
import fetchFiligranValues from '../customization/filigranValues';
import { type ActionFormValues } from '../utils/actionFormValues';
import { isCustomizedCopy } from '../utils/threatArsenalStatusUtils';

interface ActionEdit {
  details: ThreatArsenalActionFullOutput;
  // Set on a customized copy only.
  customization: PayloadCustomizationOutput | null;
  filigranValues?: ActionFormValues;
}

/** What the edit drawer of an action needs, its details and its Filigran values loaded at once; null while loading. */
const useActionEdit = (action: ThreatArsenalAction | null) => {
  const customized = isCustomizedCopy(action);
  const load = async (actionId: string): Promise<ActionEdit> => {
    const [{ data: details }, filigran] = await Promise.all([
      fetchThreatArsenalAction(actionId) as Promise<{ data: ThreatArsenalActionFullOutput }>,
      customized ? fetchFiligranValues(actionId) : Promise.resolve(undefined),
    ]);
    return {
      details,
      customization: filigran?.customization ?? null,
      filigranValues: filigran?.values,
    };
  };
  return useLoadedOnKey<ActionEdit | null>(action?.injector_contract_id, load, null).value;
};

export default useActionEdit;
