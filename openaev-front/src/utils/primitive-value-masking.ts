import type { ScopeVariableOutput } from './api-types';

type PrimitiveValueType = ScopeVariableOutput['scope_variable_type'];

type MaskRule = {
  prefixLength: number;
  suffixLength: number;
};

const MASK_RULES: Record<string, MaskRule> = {
  password: { prefixLength: 1, suffixLength: 1 },
  hash: { prefixLength: 3, suffixLength: 3 },
  key: { prefixLength: 2, suffixLength: 2 },
  username: { prefixLength: 1, suffixLength: 1 },
  admin_username: { prefixLength: 1, suffixLength: 1 },
  account_with_password_not_required: { prefixLength: 1, suffixLength: 1 },
  asreproastable_account: { prefixLength: 1, suffixLength: 1 },
  kerberoastable_account: { prefixLength: 1, suffixLength: 1 },
  delegation_account: { prefixLength: 1, suffixLength: 1 },
  sid: { prefixLength: 2, suffixLength: 2 },
  host: { prefixLength: 2, suffixLength: 2 },
  domain: { prefixLength: 2, suffixLength: 2 },
  ipv4: { prefixLength: 2, suffixLength: 2 },
  ipv6: { prefixLength: 2, suffixLength: 2 },
  ip_subnet: { prefixLength: 2, suffixLength: 2 },
  file_path: { prefixLength: 2, suffixLength: 2 },
  'targeted-asset': { prefixLength: 2, suffixLength: 2 },
};

const maskWithVisibleEdges = (value: string, { prefixLength, suffixLength }: MaskRule): string => {
  if (value.length <= prefixLength + suffixLength) {
    return '*'.repeat(value.length);
  }

  const maskedLength = value.length - prefixLength - suffixLength;
  return `${value.slice(0, prefixLength)}${'*'.repeat(maskedLength)}${value.slice(-suffixLength)}`;
};

const maskPrimitiveValue = (type: PrimitiveValueType, value?: string): string | undefined => {
  if (!value) {
    return value;
  }

  if (!type) {
    return value;
  }

  const rule = MASK_RULES[type];
  if (!rule) {
    return value;
  }

  return maskWithVisibleEdges(value, rule);
};

export default maskPrimitiveValue;
