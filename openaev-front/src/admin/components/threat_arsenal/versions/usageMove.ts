import { moveThreatArsenalUsages } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsageMoveOutput, type PayloadUsageOutput, type PayloadVersionOutput } from '../../../../utils/api-types';

// Where the usages of a version can go, the latest picked first: a revoked version would refuse them all.
export const moveTargets = (versions: PayloadVersionOutput[], sourceId: string) => {
  const targets = versions.filter(v => v.payload_id !== sourceId && !v.payload_revoked);
  return {
    targets,
    defaultTarget: (targets.find(v => v.payload_is_latest) ?? targets[0])?.payload_id ?? '',
  };
};

export const moveUsages = (actionId: string, targetPayloadId: string, usages: PayloadUsageOutput[]): Promise<PayloadUsageMoveOutput> => moveThreatArsenalUsages(actionId, {
  target_payload_id: targetPayloadId,
  usage_ids: usages.map(usage => usage.usage_id),
}).then(response => response.data as PayloadUsageMoveOutput);
