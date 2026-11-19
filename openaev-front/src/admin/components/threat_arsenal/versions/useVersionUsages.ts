import { useEffect, useState } from 'react';

import { fetchThreatArsenalUsages } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsagesOutput } from '../../../../utils/api-types';

/** The usages of one version's action, and what they add up to: what a revocation would stop. */
const useVersionUsages = (actionId: string | undefined) => {
  const { t } = useFormatter();
  const [usages, setUsages] = useState<PayloadUsagesOutput | null>(null);

  useEffect(() => {
    setUsages(null);
    if (actionId) {
      fetchThreatArsenalUsages(actionId).then(response => setUsages(response.data as PayloadUsagesOutput));
    }
  }, [actionId]);

  const shown = usages?.payload_usages ?? [];
  const countOf = (type: string) => shown.filter(usage => usage.usage_type === type).length;
  const scenarioCount = new Set(shown.map(usage => usage.usage_scenario_id).filter(Boolean)).size;
  const counted = (count: number, one: string, many: string) => (count === 1 ? t(one) : t(many, { count: String(count) }));
  const impact = [
    scenarioCount > 0 ? counted(scenarioCount, '1 scenario', '{count} scenarios') : null,
    countOf('inject') > 0 ? counted(countOf('inject'), '1 scenario inject', '{count} scenario injects') : null,
    countOf('atomic_testing') > 0 ? counted(countOf('atomic_testing'), '1 atomic test', '{count} atomic tests') : null,
    countOf('step') > 0 ? counted(countOf('step'), '1 chained step', '{count} chained steps') : null,
  ].filter(Boolean).join(' · ');

  return {
    loaded: usages !== null,
    shown,
    usageCount: shown.length + (usages?.payload_usages_hidden_count ?? 0),
    impact,
  };
};

export default useVersionUsages;
