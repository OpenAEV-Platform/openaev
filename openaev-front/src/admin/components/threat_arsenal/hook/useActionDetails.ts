import { fetchThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import useLoadedOnKey from '../../../../utils/hooks/useLoadedOnKey';

const loadDetails = (actionId: string) => fetchThreatArsenalAction(actionId)
  .then(({ data }: { data: ThreatArsenalActionFullOutput }) => data);

/** The full details of an action, loaded each time its id changes; nothing without id. */
const useActionDetails = (actionId: string | undefined) => {
  const { value, loading } = useLoadedOnKey<ThreatArsenalActionFullOutput | null>(actionId, loadDetails, null);
  return {
    details: value,
    loading,
  };
};

export default useActionDetails;
