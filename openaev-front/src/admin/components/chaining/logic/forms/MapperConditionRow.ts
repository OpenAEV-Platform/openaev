export interface MapperConditionRow {
  condition_key_type: string;
  condition_key: string;
  condition_mapping_type: string;
}

export const CONDITION_KEY_TYPES = [
  'text', 'number', 'status', 'port', 'portscan',
  'ipv4', 'ipv6', 'credentials', 'cve', 'username',
  'share', 'admin_username', 'group', 'computer',
  'password_policy', 'delegation', 'sid', 'vulnerability', 'asset',
] as const;

export const MAPPING_TYPES = ['DEFAULT', 'LOCAL', 'GLOBAL'] as const;
