import { fetchThreatArsenalVersionChanges } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadVersionChangeOutput } from '../../../../utils/api-types';
import useLoadedOnKey from '../../../../utils/hooks/useLoadedOnKey';

const loadChanges = (actionId: string) => fetchThreatArsenalVersionChanges(actionId)
  .then(({ data }: { data: PayloadVersionChangeOutput[] }) => data);

/** What the version of this action changed from the one just below it; null while it loads. */
const useVersionChanges = (actionId: string) => useLoadedOnKey<PayloadVersionChangeOutput[] | null>(actionId, loadChanges, null).value;

export default useVersionChanges;
