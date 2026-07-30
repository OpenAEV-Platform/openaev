import type { ScopeVariableOutput } from '../../../utils/api-types';

const maskWithVisibleEdges = (value: string, prefixLength: number, suffixLength: number): string => {
  if (value.length <= prefixLength + suffixLength) {
    return '*'.repeat(value.length);
  }

  const maskedLength = value.length - prefixLength - suffixLength;
  return `${value.slice(0, prefixLength)}${'*'.repeat(maskedLength)}${value.slice(-suffixLength)}`;
};

const maskScopeVariableValue = (
  type: ScopeVariableOutput['scope_variable_type'],
  value?: string,
): string | undefined => {
  if (!value) {
    return value;
  }

  if (type === 'password') {
    return maskWithVisibleEdges(value, 1, 1);
  }

  if (type === 'hash') {
    return maskWithVisibleEdges(value, 3, 3);
  }

  return value;
};

export default maskScopeVariableValue;
