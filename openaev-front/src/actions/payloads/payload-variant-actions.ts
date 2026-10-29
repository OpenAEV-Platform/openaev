import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type ThreatArsenalAction } from '../../utils/api-types';

export type VariantBlock = 'EXECUTION' | 'PLATFORMS';
export type VariantChoice = 'KEEP_MINE' | 'TAKE_CATALOG' | 'EDIT';

export interface PayloadVariant {
  identity_id: string;
  variant_action_id?: string;
  variant_payload_id: string;
  variant_version: string;
  variant_overrides?: Record<string, unknown>;
  variant_customizable_fields: string[];
  base_payload_id: string;
  base_action_id?: string;
  base_external_id?: string;
  base_version: string;
  newer_base_payload_id?: string;
  newer_base_version?: string;
  conflict_target_version?: string;
  base_values: Record<string, unknown>;
  current_values: Record<string, unknown>;
}

export interface VariantSaveOutput {
  action?: ThreatArsenalAction;
  new_version: boolean;
  previous_action_id?: string;
  previous_version_usages: number;
}

export interface VariantBlockPreview {
  block: VariantBlock;
  catalog_changed: boolean;
  variant_changed: boolean;
  conflict: boolean;
  base: Record<string, unknown>;
  catalog: Record<string, unknown>;
  mine: Record<string, unknown>;
}

export interface VariantRebasePreview {
  current_version: string;
  base_version: string;
  target_payload_id: string;
  target_version: string;
  blocks: VariantBlockPreview[];
  missing_variables: string[];
  has_conflict: boolean;
}

export interface VariantBlockChoice {
  choice: VariantChoice;
  values?: Record<string, unknown>;
}

const IDENTITY_URI = '/api/payload-identities';

export const customizePayload = (payloadId: string) => simplePostCall(`/api/payloads/${payloadId}/customize`, {});

export const fetchPayloadVariant = (identityId: string) => simpleCall(`${IDENTITY_URI}/${identityId}`);

export const updatePayloadOverrides = (identityId: string, overrides: Record<string, unknown>, tagIds?: string[]) => simplePutCall(
  `${IDENTITY_URI}/${identityId}/overrides`,
  {
    variant_overrides: overrides,
    variant_tags: tagIds,
  },
);

export const fetchVariantRebasePreview = (identityId: string, targetPayloadId?: string) => simpleCall(
  `${IDENTITY_URI}/${identityId}/rebase-preview${targetPayloadId ? `?target_payload_id=${targetPayloadId}` : ''}`,
);

export const rebaseVariant = (
  identityId: string,
  targetPayloadId: string,
  choices: Partial<Record<VariantBlock, VariantBlockChoice>>,
  overrides?: Record<string, unknown>,
) => simplePostCall(
  `${IDENTITY_URI}/${identityId}/resolve-conflict`,
  {
    target_payload_id: targetPayloadId,
    choices,
    variant_overrides: overrides,
  },
);

export const liftInheritedRevocation = (payloadId: string, reason: string) => simplePostCall(
  `/api/payloads/${payloadId}/inherited-revocation/lift`,
  { content_revocation_reason: reason },
);
