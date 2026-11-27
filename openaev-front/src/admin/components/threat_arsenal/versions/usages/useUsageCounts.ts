import { fetchThreatArsenalUsageCounts } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsageCountOutput } from '../../../../../utils/api-types';
import useLoadedOnKey from '../../../../../utils/hooks/useLoadedOnKey';
import { type UsageCounts } from './usageCounts';

const loadCounts = (actionId: string) => fetchThreatArsenalUsageCounts(actionId)
  .then(({ data }: { data: PayloadUsageCountOutput[] }): UsageCounts => Object.fromEntries(data.map(count => [count.payload_id, count.payload_usage_count])))
  // The call already tells the user it failed: no usage instead of loading forever.
  .catch((): UsageCounts => ({}));

/** How many usages each version of the action's payload has, in one call: null while loading, loaded again when `reloadKey` changes. */
const useUsageCounts = (actionId: string | undefined, reloadKey = 0) => useLoadedOnKey<UsageCounts | null>(
  actionId ? `${reloadKey}:${actionId}` : undefined,
  () => loadCounts(actionId ?? ''),
  null,
).value;

export default useUsageCounts;
