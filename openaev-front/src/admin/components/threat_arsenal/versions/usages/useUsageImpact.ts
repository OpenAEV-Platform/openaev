import { type PayloadUsagesOutput } from '../../../../../utils/api-types';
import useCountLabel from '../../../../../utils/hooks/useCountLabel';
import { scenarioCount } from './usageCounts';
import { USAGE_TYPES } from './usageTypes';

/** What the usages add up to, e.g. "2 scenarios · 3 scenario injects · 1 atomic test". */
const useUsageImpact = (usages: PayloadUsagesOutput | null) => {
  const counted = useCountLabel();
  if (!usages) return '';
  const scenarios = scenarioCount(usages);
  const byType = Object.entries(USAGE_TYPES).map(([type, { one, many }]) => {
    const count = usages.payload_usages.filter(usage => usage.usage_type === type).length;
    return count > 0 ? counted(count, one, many) : null;
  });
  return [scenarios > 0 ? counted(scenarios, '1 scenario', '{count} scenarios') : null, ...byType].filter(Boolean).join(' · ');
};

export default useUsageImpact;
