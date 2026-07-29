enum ContractOutputElementType {
  account_with_password_not_required = 'AccountWithPasswordNotRequired',
  admin_username = 'AdminUsername',
  asreproastable_account = 'AsreproastableAccount',
  computer = 'Computer',
  credentials = 'Credentials',
  cve = 'CVE',
  delegation = 'Delegation',
  file = 'File',
  group = 'Group',
  ipv4 = 'IPv4',
  ipv6 = 'IPv6',
  kerberoastable_account = 'KerberoastableAccount',
  number = 'Number',
  password_policy = 'PasswordPolicy',
  port = 'Port',
  portscan = 'PortsScan',
  share = 'Share',
  sid = 'Sid',
  text = 'Text',
  action_output = 'action_output',
  username = 'Username',
  vulnerability = 'Vulnerability',
  expectation_signature = 'expectation_signature',
}

export const CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS = Object.keys(
  ContractOutputElementType,
) as [keyof typeof ContractOutputElementType, ...Array<keyof typeof ContractOutputElementType>];

export default ContractOutputElementType;
