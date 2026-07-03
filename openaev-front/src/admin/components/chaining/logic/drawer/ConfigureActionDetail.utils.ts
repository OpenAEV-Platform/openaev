import type { ContractElement } from '../../../../../utils/api-types-custom';
import type { ExpectationInput } from '../../../common/injects/expectations/Expectation';

export const EXPECTATION_FIELD_TYPE = 'expectation';

export const isExpectationInput = (value: unknown): value is ExpectationInput =>
  typeof value === 'object'
  && value !== null
  && 'expectation_type' in value
  && 'expectation_name' in value
  && 'expectation_score' in value;

export const getContractFieldDefaultValue = (field: ContractElement): unknown => {
  if (field.defaultValue !== undefined && field.defaultValue !== null) {
    return field.defaultValue;
  }
  if (field.type === EXPECTATION_FIELD_TYPE) {
    if (field.predefinedExpectations && field.predefinedExpectations.length > 0) {
      return field.predefinedExpectations;
    }
    if (field.availableExpectations && field.availableExpectations.length > 0) {
      return field.availableExpectations;
    }
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
