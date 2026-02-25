enum ContractOutputElementType {
  text = 'Text',
  number = 'Number',
  port = 'Port',
  portscan = 'PortsScan',
  ipv4 = 'IPv4',
  ipv6 = 'IPv6',
  credentials = 'Credentials',
  cve = 'CVE',
  username = 'Username',
  share = 'Share',
  admin_username = 'AdminUsername',
  group = 'Group',
  computer = 'Computer',
  password_policy = 'PasswordPolicy',
  delegation = 'Delegation',
  sid = 'Sid',
  account_with_password_not_required = 'AccountWithPasswordNotRequired',
  asreproastable_account = 'AsreproastableAccount',
  kerberoastable_account = 'KerberoastableAccount',
  vulnerability = 'Vulnerability',
}

export const CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS = [
  'text', 'number', 'port', 'portscan', 'ipv4', 'ipv6', 'credentials', 'cve',
  'vulnerability', 'username', 'share', 'admin_username', 'group', 'computer',
  'password_policy', 'delegation', 'sid', 'account_with_password_not_required',
  'asreproastable_account', 'kerberoastable_account',
] as const;

export type ContractOutputElementTypeKey = typeof CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS[number];

export default ContractOutputElementType;
