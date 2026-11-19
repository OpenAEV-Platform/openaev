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
  const impact = [
    scenarioCount > 0 ? t('{count, plural, one {# scenario} other {# scenarios}}', { count: scenarioCount }) : null,
    countOf('inject') > 0 ? t('{count, plural, one {# scenario inject} other {# scenario injects}}', { count: countOf('inject') }) : null,
    countOf('atomic_testing') > 0 ? t('{count, plural, one {# atomic test} other {# atomic tests}}', { count: countOf('atomic_testing') }) : null,
    countOf('step') > 0 ? t('{count, plural, one {# chained step} other {# chained steps}}', { count: countOf('step') }) : null,
  ].filter(Boolean).join(' · ');

  return {
    loaded: usages !== null,
    shown,
    usageCount: shown.length + (usages?.payload_usages_hidden_count ?? 0),
    impact,
  };
};

export default useVersionUsages;
