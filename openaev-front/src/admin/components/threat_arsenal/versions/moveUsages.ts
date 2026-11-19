import { moveThreatArsenalUsages } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsageMoveOutput, type PayloadUsageOutput } from '../../../../utils/api-types';

// Moves the given usages of an action's version to another version: chained steps travel apart.
const moveUsages = (actionId: string, targetPayloadId: string, usages: PayloadUsageOutput[]): Promise<PayloadUsageMoveOutput> => moveThreatArsenalUsages(actionId, {
  target_payload_id: targetPayloadId,
  inject_ids: usages.filter(usage => usage.usage_type !== 'step').map(usage => usage.usage_id),
  step_ids: usages.filter(usage => usage.usage_type === 'step').map(usage => usage.usage_id),
}).then(response => response.data as PayloadUsageMoveOutput);

export default moveUsages;
