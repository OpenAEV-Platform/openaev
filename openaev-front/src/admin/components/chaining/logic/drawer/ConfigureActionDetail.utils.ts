import type { ContractElement } from '../../../../../utils/api-types-custom';
import type { ExpectationInput } from '../../../common/injects/expectations/Expectation';

export const EXPECTATION_FIELD_TYPE = 'expectation';
export const EXPECTATIONS_CONTENT_KEY = 'expectations';

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
