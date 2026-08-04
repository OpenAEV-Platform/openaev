import type { ConditionCreateInput } from '../../../../../utils/api-types';
import type { ContractElement, ContractType } from '../../../../../utils/api-types-custom';
import type { ExpectationInput } from '../../../common/injects/expectations/Expectation';
import type { ActionDetailData } from '../types';
import type { FieldLink } from './InjectDataFieldItem';

export const EXPECTATION_FIELD_TYPE = 'expectation';
export const EXPECTATIONS_CONTENT_KEY = 'expectations';
const FRONTEND_CONTENT_KEY_PREFIX = '__openaev_';

/**
 * Maps contract field types to their auto-link output primitive type (PrimitiveType label).
 * Fields whose type appears here are automatically linked when the action form opens.
 * Extend this map to add new auto-links.
 */
const AUTO_LINK_BY_FIELD_TYPE: Partial<Record<ContractType, string>> = { 'targeted-asset': 'targeted-asset' };

const resolveDefaultOutputType = (field: ContractElement): string | undefined => {
  const argumentType = typeof field.argumentType === 'string' ? field.argumentType.trim() : '';
  if (argumentType.length > 0) {
    return argumentType;
  }
  return AUTO_LINK_BY_FIELD_TYPE[field.type];
};

/**
 * Returns an updated fieldLinks record with auto-links applied for fields carrying an argumentType,
 * or whose field type is strictly auto-linked. Existing links are never overwritten.
 */
export const applyAutoLinks = (
  contractFields: ContractElement[],
  existingLinks: Record<string, FieldLink>,
): Record<string, FieldLink> => {
  const updates: Record<string, FieldLink> = {};
  for (const field of contractFields) {
    if (Object.prototype.hasOwnProperty.call(existingLinks, field.key)) continue;
    const outputType = resolveDefaultOutputType(field);
    if (outputType) {
      updates[field.key] = {
        outputTypes: [outputType],
        localScope: false,
      };
    }
  }
  return Object.keys(updates).length > 0
    ? {
        ...existingLinks,
        ...updates,
      }
    : existingLinks;
};

/** Converts linked fields to step mapper conditions. */
export const mapFieldLinksToStepConditions = (
  data: ActionDetailData,
): ConditionCreateInput[] => {
  const fieldLinks: Record<string, FieldLink> = data.inject_field_links;
  return Object.entries(fieldLinks).map(([fieldKey, link], index) => {
    const outputTypes = link.outputTypes ?? [];
    const keyTypes = outputTypes.length > 0 ? outputTypes : [];

    // Carry over the field's own typed value as the MAPPER condition's defined value,
    // so it keeps participating in the generated input combinations as an extra
    // candidate alongside the linked type's resolved pool, instead of being dropped
    // once a primitive type is linked.
    const rawValue = data.inject_content[fieldKey];
    const definedValue = rawValue != null && String(rawValue).trim() !== ''
      ? String(rawValue)
      : undefined;

    return {
      condition_temporary_id: String(index),
      condition_type: 'MAPPER',
      condition_key_types: keyTypes as ConditionCreateInput['condition_key_types'],
      condition_key: fieldKey,
      condition_value: definedValue,
      condition_mapping_type: (link.localScope ? 'LOCAL' : 'GLOBAL') as ConditionCreateInput['condition_mapping_type'],
    };
  });
};

/** Parses contract fields from injector contract content JSON string. */
export const parseContractFields = (injectorContractContent?: string): ContractElement[] => {
  if (!injectorContractContent) {
    return [];
  }
  try {
    const parsed = JSON.parse(injectorContractContent) as { fields?: ContractElement[] };
    return Array.isArray(parsed.fields) ? parsed.fields : [];
  } catch {
    return [];
  }
};

/** Removes frontend-only metadata keys before sending inject_content to backend. */
export const stripFrontendMetadataKeys = (
  content: Record<string, unknown>,
): Record<string, unknown> => {
  return Object.fromEntries(
    Object.entries(content).filter(
      ([key]) => !key.startsWith(FRONTEND_CONTENT_KEY_PREFIX),
    ),
  );
};

/** Returns the set of field keys that are auto-linked (and therefore read-only). */
export const getAutoLinkedFieldKeys = (contractFields: ContractElement[]): Set<string> => {
  return new Set(
    contractFields
      .filter(f => AUTO_LINK_BY_FIELD_TYPE[f.type] !== undefined)
      .map(f => f.key),
  );
};

/** Type guard returns true if value is a valid ExpectationInput object. */
export const isExpectationInput = (value: unknown): value is ExpectationInput =>
  typeof value === 'object'
  && value !== null
  && 'expectation_type' in value
  && 'expectation_name' in value
  && 'expectation_score' in value;

/**
 * Returns the default value for a contract field.
 * For expectation fields: returns predefined expectations (or defaultValue if set).
 * For other fields: unwraps cardinality-1 arrays to scalar
 */
export const getContractFieldDefaultValue = (field: ContractElement): unknown => {
  if (field.type === EXPECTATION_FIELD_TYPE) {
    if (Array.isArray(field.defaultValue) && field.defaultValue.length > 0) {
      return field.defaultValue;
    }
    const predefinedExpectations = (field.availableExpectations ?? []).filter(e => e.expectation_is_predefined);
    if (predefinedExpectations.length > 0) {
      return predefinedExpectations;
    }
  }

  if (field.defaultValue !== undefined && field.defaultValue !== null) {
    // For cardinality='1' fields, unwrap to scalar so the value matches the expected type.
    if (field.cardinality === '1' && Array.isArray(field.defaultValue)) {
      return field.defaultValue[0] ?? '';
    }
    return field.defaultValue;
  }
  return undefined;
};

/**
 * Builds a default content map from all contract fields, including expectations.
 */
export const buildContractDefaults = (fields: ContractElement[]): Record<string, unknown> => {
  const defaults: Record<string, unknown> = {};
  for (const field of fields) {
    const defaultValue = getContractFieldDefaultValue(field);
    if (defaultValue !== undefined && defaultValue !== null) {
      defaults[field.key] = defaultValue;
    }
  }
  return defaults;
};

/**
 * Populates the content with predefined expectations from the contract
 * if no expectations are currently set. Returns content unchanged if
 * expectations are already present or no expectation field exists.
 */
export const applyPredefinedExpectations = (
  content: Record<string, unknown>,
  fields: ContractElement[],
): Record<string, unknown> => {
  const expectationField = fields.find(field => field.type === EXPECTATION_FIELD_TYPE);
  if (!expectationField) {
    return content;
  }

  const defaultValue = getContractFieldDefaultValue(expectationField);
  const defaultExpectations = Array.isArray(defaultValue)
    ? defaultValue.filter(isExpectationInput)
    : [];
  if (defaultExpectations.length === 0) {
    return content;
  }

  const currentExpectations = content[EXPECTATIONS_CONTENT_KEY];
  if (Array.isArray(currentExpectations) && currentExpectations.length > 0) {
    return content;
  }

  return {
    ...content,
    [EXPECTATIONS_CONTENT_KEY]: defaultExpectations,
  };
};

export const normalizeFieldLinks = (
  links: Record<string, FieldLink> | undefined,
): Record<string, FieldLink> => {
  if (!links) {
    return {};
  }
  const normalized: Record<string, FieldLink> = {};
  for (const [fieldKey, link] of Object.entries(links)) {
    normalized[fieldKey] = {
      ...link,
      outputTypes: link.outputTypes ?? [],
    };
  }
  return normalized;
};
