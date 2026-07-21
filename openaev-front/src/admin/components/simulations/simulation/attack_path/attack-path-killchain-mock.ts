// TEMPORARY MOCK — TODO(#6647): remove when the backend exposes these fields.
//
// This file fakes the kill-chain execution metadata that the backend will eventually carry on
// AttackPathNodeDTO (fields `dependsOn: string[]` and
// `consumedFindingKeys: { keyType, operator, value }[]`). It is intentionally shaped 1:1 with those
// future DTO fields so that, once the backend is ready, the whole file can be deleted and the two
// consumers switched to read the real DTO fields with no other change.
//
// IMPORTANT: the stepTemplateId keys below are PLACEHOLDERS. They MUST be adapted to the real seed
// step-template ids of the running environment for the causal edges to match anything — otherwise the
// lookup returns undefined and the graph renders exactly as it does today (additive by design).
//
// Encoded scenario (a realistic nmap -> smb -> null-session kill chain):
//   1. an nmap-style scan step produces a `port` finding with value '445';
//   2. an smb/enum step CONSUMES { keyType:'port', operator:'EQ', value:'445' } and dependsOn (1);
//   3. a NULL-session/shares step consumes the smb step's output and dependsOn (2).

export interface ConsumedFindingKey {
  keyType: string;
  operator: string;
  value: string;
}

export interface KillChainExecMeta {
  dependsOn: string[];
  consumedFindingKeys: ConsumedFindingKey[];
}

// Placeholder step-template ids — adapt to real seed ids (see file header).
const STEP_NMAP_SCAN = 'step-template-nmap-portscan';
const STEP_SMB_ENUM = 'step-template-smb-enum';
const STEP_NULL_SESSION = 'step-template-smb-null-session';

// Keyed by stepTemplateId. Mirrors the future AttackPathNodeDTO fields exactly.
export const KILLCHAIN_MOCK: Record<string, KillChainExecMeta> = {
  // 1. nmap-style port scan: the origin of the chain, it depends on nothing and consumes nothing.
  //    It produces the `port` finding (value '445') that the smb step below consumes.
  [STEP_NMAP_SCAN]: {
    dependsOn: [],
    consumedFindingKeys: [],
  },
  // 2. smb/enum step: depends on the nmap scan and consumes its produced port finding (445 open).
  [STEP_SMB_ENUM]: {
    dependsOn: [STEP_NMAP_SCAN],
    consumedFindingKeys: [
      {
        keyType: 'port',
        operator: 'EQ',
        value: '445',
      },
    ],
  },
  // 3. NULL-session / shares enumeration: depends on the smb step and consumes its output (the
  //    reachable smb service / accessible shares surfaced by the enum step).
  [STEP_NULL_SESSION]: {
    dependsOn: [STEP_SMB_ENUM],
    consumedFindingKeys: [
      {
        keyType: 'share',
        operator: 'EQ',
        value: 'ADMIN$',
      },
    ],
  },
};

// Accessor mirroring how a consumer would read the DTO field: returns the kill-chain meta for a given
// stepTemplateId, or undefined when unknown (so callers degrade to today's graph — additive by design).
export const getKillChainMeta = (stepTemplateId?: string): KillChainExecMeta | undefined =>
  (stepTemplateId ? KILLCHAIN_MOCK[stepTemplateId] : undefined);
