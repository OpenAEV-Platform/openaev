import { olderVersionsInUse, totalUsageCount } from '../versions/usages/usageCounts';
import useUsagesByVersion from '../versions/usages/useUsagesByVersion';
import usePayloadVersions from '../versions/usePayloadVersions';

/**
 * How many usages still run an older version of a card's payload, counted for a card known to have some
 * only; null until counted. The list call only says whether a card has some: the number is read here.
 */
const useOlderUsageCount = (actionId: string, inUse: boolean) => {
  const versions = usePayloadVersions(actionId, inUse);
  const usages = useUsagesByVersion(inUse ? versions : null);
  if (!inUse || !versions || !usages) return null;
  return totalUsageCount(olderVersionsInUse(versions, usages, actionId));
};

export default useOlderUsageCount;
