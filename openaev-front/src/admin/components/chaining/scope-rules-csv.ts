export type ScopeCsvType = 'DOMAIN' | 'IP' | 'IP_SUBNET';

export interface ScopeCsvRule {
  type: ScopeCsvType;
  value: string;
}

export interface ScopeCsvInvalidRow {
  row: number;
  reason: string;
}

export interface ScopeCsvParseResult {
  valid: ScopeCsvRule[];
  invalid: ScopeCsvInvalidRow[];
}

const IPV4_PART = String.raw`(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)`;
const IPV4_REGEX = new RegExp(String.raw`^${IPV4_PART}(\.${IPV4_PART}){3}$`);
const DOMAIN_REGEX = /^(?=.{1,253}$)(?!-)(?:[a-zA-Z0-9-]{1,63}\.)+[a-zA-Z]{2,63}$/;

const normalizeType = (raw: string): ScopeCsvType | null => {
  const value = raw.trim().toLowerCase();
  if (value === 'domain') {
    return 'DOMAIN';
  }
  if (value === 'ip') {
    return 'IP';
  }
  if (value === 'ipsubnet' || value === 'ip_subnet' || value === 'ip-subnet') {
    return 'IP_SUBNET';
  }
  return null;
};

const isIpv6 = (value: string) => {
  if (!value.includes(':')) {
    return false;
  }

  const parts = value.split('::');
  if (parts.length > 2) {
    return false;
  }

  const left = parts[0] ? parts[0].split(':') : [];
  const right = parts.length === 2 && parts[1] ? parts[1].split(':') : [];
  const segments = [...left, ...right];
  if (segments.length > 8 || (parts.length === 1 && segments.length !== 8)) {
    return false;
  }

  return segments.every(segment => /^[0-9a-fA-F]{1,4}$/.test(segment));
};

const isIp = (value: string) => IPV4_REGEX.test(value) || isIpv6(value);

const isIpSubnet = (value: string) => {
  const [ip, rawMask] = value.split('/');
  if (!ip || !rawMask || !isIp(ip.trim())) {
    return false;
  }

  const mask = Number(rawMask);
  if (!Number.isInteger(mask)) {
    return false;
  }

  const isIpv6 = ip.includes(':');
  return isIpv6 ? mask >= 0 && mask <= 128 : mask >= 0 && mask <= 32;
};

const isDomain = (value: string) => DOMAIN_REGEX.test(value);

const isHeader = (first: string, second: string) => {
  return first.trim().toLowerCase() === 'type' && second.trim().toLowerCase() === 'value';
};

const splitCsvLine = (line: string): string[] => {
  const values: string[] = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < line.length; i += 1) {
    const char = line[i];

    if (char === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"';
        i += 1;
      } else {
        inQuotes = !inQuotes;
      }
      continue;
    }

    if (char === ',' && !inQuotes) {
      values.push(current.trim());
      current = '';
      continue;
    }

    current += char;
  }

  values.push(current.trim());
  return values;
};

export const parseScopeRulesCsv = (content: string): ScopeCsvParseResult => {
  const sanitizedContent = content.replace(/^\uFEFF/, '');
  const rows = sanitizedContent.split(/\r?\n/);
  const result: ScopeCsvParseResult = {
    valid: [],
    invalid: [],
  };
  const seen = new Set<string>();

  rows.forEach((line, index) => {
    const rowNumber = index + 1;
    if (!line.trim()) {
      return;
    }

    const cells = splitCsvLine(line);
    const [rawType = '', rawValue = ''] = cells;

    if (cells.length !== 2 || !rawType || !rawValue) {
      result.invalid.push({
        row: rowNumber,
        reason: 'Expected 2 columns: type,value',
      });
      return;
    }

    if (rowNumber === 1 && isHeader(rawType, rawValue)) {
      return;
    }

    const type = normalizeType(rawType);
    if (!type) {
      result.invalid.push({
        row: rowNumber,
        reason: `Unknown type: ${rawType}`,
      });
      return;
    }

    const value = rawValue.trim();
    let isValid: boolean;
    switch (type) {
      case 'DOMAIN':
        isValid = isDomain(value);
        break;
      case 'IP':
        isValid = isIp(value);
        break;
      case 'IP_SUBNET':
        isValid = isIpSubnet(value);
        break;
      default:
        isValid = false;
    }

    if (!isValid) {
      result.invalid.push({
        row: rowNumber,
        reason: `Invalid ${type.toLowerCase()} value: ${value}`,
      });
      return;
    }

    const key = `${type}:${value.toLowerCase()}`;
    if (!seen.has(key)) {
      seen.add(key);
      result.valid.push({
        type,
        value,
      });
    }
  });

  return result;
};

export const buildScopeRulesCsvTemplate = () => {
  return [
    'type,value',
    'domain,example.com',
    'ip,10.10.10.10',
    'ip_subnet,10.10.10.0/24',
  ].join('\n');
};
