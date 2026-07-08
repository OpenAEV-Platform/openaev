import type { ContractElement } from '../../../../../utils/api-types-custom';
import type { ExpectationInput } from '../../../common/injects/expectations/Expectation';

export const EXPECTATION_FIELD_TYPE = 'expectation';
export const EXPECTATIONS_CONTENT_KEY = 'expectations';

export const isExpectationInput = (value: unknown): value is ExpectationInput =>
  typeof value === 'object'
  && value !== null
  && 'expectation_type' in value
  && 'expectation_name' in value
  && 'expectation_score' in value;

export const getContractFieldDefaultValue = (field: ContractElement): unknown => {
  if (field.type === EXPECTATION_FIELD_TYPE) {
    if (Array.isArray(field.defaultValue) && field.defaultValue.length > 0) {
      return field.defaultValue;
    }
    if (field.predefinedExpectations && field.predefinedExpectations.length > 0) {
      return field.predefinedExpectations;
    }
  }

  if (field.defaultValue !== undefined && field.defaultValue !== null) {
    return field.defaultValue;
  }
  return undefined;
};

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

export const applyExpectationDefaults = (
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
