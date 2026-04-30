import ipaddr from 'ipaddr.js';

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

const DOMAIN_REGEX = /^(?=.{1,253}$)(?!-)(?:[a-zA-Z0-9-]{1,63}\.)+[a-zA-Z]{2,63}$/;

const TYPE_MAP: Record<string, ScopeCsvType> = {
  'domain': 'DOMAIN',
  'ip': 'IP',
  'ipsubnet': 'IP_SUBNET',
  'ip_subnet': 'IP_SUBNET',
  'ip-subnet': 'IP_SUBNET',
};

const normalizeType = (raw: string): ScopeCsvType | null => {
  return TYPE_MAP[raw.trim().toLowerCase()] ?? null;
};

const isIp = (value: string): boolean => {
  if (!value.includes('.') && !value.includes(':')) return false;
  return ipaddr.isValid(value);
};

const isIpSubnet = (value: string): boolean => {
  try {
    ipaddr.parseCIDR(value);
    return true;
  } catch {
    return false;
  }
};

const isDomain = (value: string) => DOMAIN_REGEX.test(value);

// Strip non-printable characters, BOM markers, and Unicode replacement chars that
// appear when a CSV is saved in a non-UTF-8 encoding (e.g. UTF-16, Windows-1252).
// eslint-disable-next-line no-control-regex
const sanitizeValue = (value: string) => value.replaceAll(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F\uFEFF\uFFFD]/g, '').trim();

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
    const [rawType = '', rawValue = ''] = cells.map(sanitizeValue);

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
