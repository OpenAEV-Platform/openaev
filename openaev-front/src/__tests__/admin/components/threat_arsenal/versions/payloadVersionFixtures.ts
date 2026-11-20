import { type OlderVersionInUse } from '../../../../../admin/components/threat_arsenal/versions/usages/usageCounts';
import { type PayloadUsageOutput, type PayloadUsagesOutput, type PayloadVersionOutput, type ThreatArsenalAction } from '../../../../../utils/api-types';

export const actionId = (version: string) => `action-${version}`;

export const payloadId = (version: string) => `payload-${version}`;

/** The Threat Arsenal action of a version of the payload. */
export const versionAction = (version: string) => ({
  injector_contract_id: actionId(version),
  action_payload: { payload_id: payloadId(version) },
  action_payload_version: version,
}) as ThreatArsenalAction;

export const payloadVersion = (version: string): PayloadVersionOutput => ({
  payload_id: payloadId(version),
  payload_action_id: actionId(version),
  payload_version: version,
  payload_is_latest: false,
  payload_revoked: false,
  payload_revoked_by_catalogue: false,
});

export const usage = (id: string, scenarioId?: string, type: PayloadUsageOutput['usage_type'] = 'inject'): PayloadUsageOutput => ({
  usage_id: id,
  usage_type: type,
  usage_scenario_id: scenarioId,
});

export const usagesOf = (...usages: PayloadUsageOutput[]): PayloadUsagesOutput => ({ payload_usages: usages });

export const olderVersion = (version: string, ...usages: PayloadUsageOutput[]): OlderVersionInUse => ({
  version: payloadVersion(version),
  usages: usagesOf(...usages),
});
