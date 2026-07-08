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
    if (field.availableExpectations && field.availableExpectations.length > 0) {
      return field.availableExpectations;
    }
  }

  if (field.defaultValue !== undefined && field.defaultValue !== null) {
    if (field.cardinality === '1' && Array.isArray(field.defaultValue)) {
      return field.defaultValue[0] ?? '';
    }
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

export const normalizeSingleCardinalityContent = (
  content: Record<string, unknown>,
  fields: ContractElement[],
): Record<string, unknown> => {
  const normalizedContent = { ...content };

  fields.forEach((field) => {
    if (field.type === EXPECTATION_FIELD_TYPE || field.cardinality !== '1') {
      return;
    }

    const value = normalizedContent[field.key];
    if (Array.isArray(value)) {
      normalizedContent[field.key] = value[0] ?? '';
    }
  });

  return normalizedContent;
};

export const applyExpectationDefaults = (
  content: Record<string, unknown>,
  fields: ContractElement[],
): Record<string, unknown> => {
  const normalizedContent = normalizeSingleCardinalityContent(content, fields);
  const expectationField = fields.find(field => field.type === EXPECTATION_FIELD_TYPE);
  if (!expectationField) {
    return normalizedContent;
  }

  const defaultValue = getContractFieldDefaultValue(expectationField);
  const defaultExpectations = Array.isArray(defaultValue)
    ? defaultValue.filter(isExpectationInput)
    : [];
  if (defaultExpectations.length === 0) {
    return normalizedContent;
  }

  const currentExpectations = normalizedContent[EXPECTATIONS_CONTENT_KEY];
  if (Array.isArray(currentExpectations) && currentExpectations.length > 0) {
    return normalizedContent;
  }

  return {
    ...normalizedContent,
    [EXPECTATIONS_CONTENT_KEY]: defaultExpectations,
  };
};
