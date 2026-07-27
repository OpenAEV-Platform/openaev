import type { ContractElement, ContractType } from '../../../../../utils/api-types-custom';
import type { ExpectationInput } from '../../../common/injects/expectations/Expectation';
import type { FieldLink } from './InjectDataFieldItem';

export const EXPECTATION_FIELD_TYPE = 'expectation';
export const EXPECTATIONS_CONTENT_KEY = 'expectations';

/**
 * Maps contract field types to their auto-link output primitive type (PrimitiveType label).
 * Fields whose type appears here are automatically linked when the action form opens.
 * Extend this map to add new auto-links.
 */
const AUTO_LINK_BY_FIELD_TYPE: Partial<Record<ContractType, string>> = { 'targeted-asset': 'targeted-asset' };

const resolveDefaultOutputType = (
  field: ContractElement,
  argumentWithDefaultValueTypes: Set<string>,
): string | undefined => {
  const strictAutoType = AUTO_LINK_BY_FIELD_TYPE[field.type];
  if (strictAutoType) {
    return strictAutoType;
  }
  if (argumentWithDefaultValueTypes.has(field.type)) {
    return field.type;
  }
  if (argumentWithDefaultValueTypes.has(field.key)) {
    return field.key;
  }
  return undefined;
};

/**
 * Returns an updated fieldLinks record with auto-links applied for fields whose type
 * has a known primitive type mapping. Existing links are never overwritten.
 */
export const applyAutoLinks = (
  contractFields: ContractElement[],
  existingLinks: Record<string, FieldLink>,
  argumentWithDefaultValueTypes: Set<string>,
): Record<string, FieldLink> => {
  const updates: Record<string, FieldLink> = {};
  for (const field of contractFields) {
    if (existingLinks[field.key]) continue;
    const outputType = resolveDefaultOutputType(field, argumentWithDefaultValueTypes);
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
