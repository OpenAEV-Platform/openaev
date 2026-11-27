import { type Platform } from './remediationMitigationMock';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - SAMPLE DATA ONLY. Sigma rules come from the SigmaHQ rule set, synced by a Sigma collector (Community
// Edition: no XTM One, nothing is generated). The rules below are samples written for the prototype, NOT copies of
// SigmaHQ rules: the authors are placeholders and every link points to the rule set. In the product the collector
// stores the real rule (YAML as published) and its source URL.
//
// Licence: the SigmaHQ rules are under the Detection Rule License (DRL) 1.1. When the rules are shared (shown or
// copied here included), the licence asks to keep, if the rule supplies it:
//   1. the author(s) of the rule (the `author` field),
//   2. a link to the rule set or to the rule,
//   3. the statement that the rules are under the DRL, with its text or a link to it.
// The matches condition (author kept in messages based on matches) only applies when the rules are run on data, which
// OpenAEV does not do here: it only lists them. The card shows 1, 2 and 3 for every rule, and the YAML is kept whole.
// ---------------------------------------------------------------------------------------------

export const SIGMA_RULE_SET = {
  name: 'SigmaHQ',
  url: 'https://github.com/SigmaHQ/sigma',
  license: 'Detection Rule License (DRL) 1.1',
  licenseUrl: 'https://github.com/SigmaHQ/Detection-Rule-License',
  // The version of the rule set the collector synced (a pinned release, so the rules do not change under the user).
  version: 'sample data',
};

export type SigmaStatus = 'stable' | 'test' | 'experimental';

export type SigmaLevel = 'critical' | 'high' | 'medium' | 'low' | 'informational';

export interface SigmaRule {
  id: string;
  title: string;
  // Sigma's own maturity of the rule. `experimental` rules are not shown.
  status: SigmaStatus;
  // Sigma's own level of the alert. It is NOT the criticality of the weakness or of the asset.
  level: SigmaLevel;
  // `logsource.product`: the platform of the logs the rule reads.
  platform: Platform;
  // ATT&CK techniques of the rule (`attack.tXXXX` tags).
  techniques: string[];
  // CVEs of the rule (`cve.XXXX-XXXX` tags). A rule with a CVE is shown for the actions that check this CVE, whatever
  // their platform (the check runs from one machine, the rule reads the logs of another).
  cves?: string[];
  // The `author` field of the rule, kept as published.
  author: string;
  // Link to the rule. Without one, the card links to the rule set.
  url?: string;
  // The YAML of the rule, as published.
  content: string;
}

const SAMPLE_AUTHOR = 'Sample author (placeholder)';

// Writes the YAML of a sample rule (the same shape as a published Sigma rule).
const sampleYaml = (rule: {
  title: string;
  id: string;
  status: SigmaStatus;
  description: string;
  tags: string[];
  logsource: string[];
  detection: string[];
  falsepositives: string;
  level: SigmaLevel;
}) => [
  `title: ${rule.title}`,
  `id: ${rule.id}`,
  `status: ${rule.status}`,
  `description: ${rule.description}`,
  `author: ${SAMPLE_AUTHOR}`,
  'tags:',
  ...rule.tags.map(tag => `  - ${tag}`),
  'logsource:',
  ...rule.logsource.map(line => `  ${line}`),
  'detection:',
  ...rule.detection.map(line => `  ${line}`),
  'falsepositives:',
  `  - ${rule.falsepositives}`,
  `level: ${rule.level}`,
].join('\n');

const sample = (rule: Omit<SigmaRule, 'author' | 'content'> & {
  description: string;
  tags: string[];
  logsource: string[];
  detection: string[];
  falsepositives: string;
}): SigmaRule => ({
  id: rule.id,
  title: rule.title,
  status: rule.status,
  level: rule.level,
  platform: rule.platform,
  techniques: rule.techniques,
  cves: rule.cves,
  author: SAMPLE_AUTHOR,
  content: sampleYaml(rule),
});

export const SIGMA_LEVEL_ORDER: SigmaLevel[] = ['critical', 'high', 'medium', 'low', 'informational'];

export const SIGMA_RULES: SigmaRule[] = [
  {
    id: '2c8a1f0e-6d1b-4f7a-9a43-5d0b8f2a7c11',
    title: 'LSASS memory dump with a process dumping tool',
    status: 'test',
    level: 'high',
    platform: 'windows',
    techniques: ['T1003.001'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: LSASS memory dump with a process dumping tool',
      'id: 2c8a1f0e-6d1b-4f7a-9a43-5d0b8f2a7c11',
      'status: test',
      'description: Detects a process dumping tool started against lsass.exe.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.credential-access',
      '  - attack.t1003.001',
      'logsource:',
      '  category: process_creation',
      '  product: windows',
      'detection:',
      '  selection_tool:',
      '    Image|endswith:',
      '      - \\procdump.exe',
      '      - \\procdump64.exe',
      '  selection_target:',
      '    CommandLine|contains: lsass',
      '  condition: selection_tool and selection_target',
      'falsepositives:',
      '  - Administrators who collect a dump for troubleshooting',
      'level: high',
    ].join('\n'),
  },
  {
    id: '7b3e5d92-0c44-4a18-8f0d-1e6a9c3b2d05',
    title: 'Suspicious access to the LSASS process',
    status: 'stable',
    level: 'medium',
    platform: 'windows',
    techniques: ['T1003.001'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Suspicious access to the LSASS process',
      'id: 7b3e5d92-0c44-4a18-8f0d-1e6a9c3b2d05',
      'status: stable',
      'description: Detects a process that opens lsass.exe with the rights needed to read its memory.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.credential-access',
      '  - attack.t1003.001',
      'logsource:',
      '  category: process_access',
      '  product: windows',
      'detection:',
      '  selection:',
      '    TargetImage|endswith: \\lsass.exe',
      '    GrantedAccess|contains:',
      '      - \'0x1010\'',
      '      - \'0x1410\'',
      '      - \'0x1fffff\'',
      '  filter_system:',
      '    SourceImage|startswith: C:\\Windows\\System32\\',
      '  condition: selection and not filter_system',
      'falsepositives:',
      '  - Security and backup products that read process memory',
      'level: medium',
    ].join('\n'),
  },
  {
    id: '1d9f4a6b-3e72-4b05-a1c8-8f5d2e7b9a30',
    title: 'Kerberos ticket requested with the RC4 encryption',
    status: 'stable',
    level: 'medium',
    platform: 'windows',
    techniques: ['T1558.003'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Kerberos ticket requested with the RC4 encryption',
      'id: 1d9f4a6b-3e72-4b05-a1c8-8f5d2e7b9a30',
      'status: stable',
      'description: Detects a service ticket requested with RC4 for a service account, the way Kerberoasting tools do.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.credential-access',
      '  - attack.t1558.003',
      'logsource:',
      '  product: windows',
      '  service: security',
      'detection:',
      '  selection:',
      '    EventID: 4769',
      '    TicketEncryptionType: \'0x17\'',
      '  filter_computer:',
      '    ServiceName|endswith: $',
      '  condition: selection and not filter_computer',
      'falsepositives:',
      '  - Legacy applications that only support RC4',
      'level: medium',
    ].join('\n'),
  },
  {
    id: '5a0c7e13-9b2d-4e66-b3f4-2d8a1c6e0f47',
    title: 'Kerberos authentication without pre-authentication',
    status: 'test',
    level: 'high',
    platform: 'windows',
    techniques: ['T1558.004'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Kerberos authentication without pre-authentication',
      'id: 5a0c7e13-9b2d-4e66-b3f4-2d8a1c6e0f47',
      'status: test',
      'description: Detects a ticket granting ticket requested for an account that does not require pre-authentication.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.credential-access',
      '  - attack.t1558.004',
      'logsource:',
      '  product: windows',
      '  service: security',
      'detection:',
      '  selection:',
      '    EventID: 4768',
      '    PreAuthType: 0',
      '    TicketEncryptionType: \'0x17\'',
      '  condition: selection',
      'falsepositives:',
      '  - Accounts that were set to not require pre-authentication on purpose',
      'level: high',
    ].join('\n'),
  },
  {
    id: '9e4b2a58-7c10-4d93-8a6f-3b1d5e7c0a29',
    title: 'Directory replication requested by an account that is not a domain controller',
    status: 'stable',
    level: 'critical',
    platform: 'windows',
    techniques: ['T1003.006'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Directory replication requested by an account that is not a domain controller',
      'id: 9e4b2a58-7c10-4d93-8a6f-3b1d5e7c0a29',
      'status: stable',
      'description: Detects the replication rights used by a computer or a user that is not a domain controller (DCSync).',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.credential-access',
      '  - attack.t1003.006',
      'logsource:',
      '  product: windows',
      '  service: security',
      'detection:',
      '  selection:',
      '    EventID: 4662',
      '    AccessMask: \'0x100\'',
      '    Properties|contains:',
      '      - 1131f6aa-9c07-11d1-f79f-00c04fc2dcd2',
      '      - 1131f6ad-9c07-11d1-f79f-00c04fc2dcd2',
      '  filter_dc:',
      '    SubjectUserName|endswith: $',
      '  condition: selection and not filter_dc',
      'falsepositives:',
      '  - Directory synchronization products',
      'level: critical',
    ].join('\n'),
  },
  {
    id: '3f6d8b21-4a95-4c07-b2e1-6a9c0d4f8e53',
    title: 'Many failed SSH logins from the same source',
    status: 'test',
    level: 'medium',
    platform: 'linux',
    techniques: ['T1110.001'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Many failed SSH logins from the same source',
      'id: 3f6d8b21-4a95-4c07-b2e1-6a9c0d4f8e53',
      'status: test',
      'description: Detects failed password attempts against the SSH service.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.credential-access',
      '  - attack.t1110.001',
      'logsource:',
      '  product: linux',
      '  service: auth',
      'detection:',
      '  selection:',
      '    - \'sshd\'',
      '  keywords:',
      '    - \'Failed password\'',
      '  condition: selection and keywords',
      'falsepositives:',
      '  - Users who mistype their password',
      'level: medium',
    ].join('\n'),
  },
  {
    id: 'b2c7e904-1f38-4d6a-9e5b-0a8d3c6f1b72',
    title: 'Interactive bash shell redirected to a network socket',
    status: 'stable',
    level: 'high',
    platform: 'linux',
    techniques: ['T1059.004'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Interactive bash shell redirected to a network socket',
      'id: b2c7e904-1f38-4d6a-9e5b-0a8d3c6f1b72',
      'status: stable',
      'description: Detects a bash command line that redirects the shell to /dev/tcp, the usual reverse shell one-liner.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.execution',
      '  - attack.t1059.004',
      'logsource:',
      '  category: process_creation',
      '  product: linux',
      'detection:',
      '  selection:',
      '    Image|endswith: /bash',
      '    CommandLine|contains|all:',
      '      - \'-i\'',
      '      - \'/dev/tcp/\'',
      '  condition: selection',
      'falsepositives:',
      '  - Rare administration scripts that open a socket from bash',
      'level: high',
    ].join('\n'),
  },
  {
    id: '6d1a9c35-8e07-4b42-a7f0-5c3e2b8d9a14',
    title: 'Search for passwords in files',
    status: 'test',
    level: 'medium',
    platform: 'linux',
    techniques: ['T1552.001'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Search for passwords in files',
      'id: 6d1a9c35-8e07-4b42-a7f0-5c3e2b8d9a14',
      'status: test',
      'description: Detects a recursive search of the file system for password, key or token strings.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.credential-access',
      '  - attack.t1552.001',
      'logsource:',
      '  category: process_creation',
      '  product: linux',
      'detection:',
      '  selection:',
      '    Image|endswith: /grep',
      '    CommandLine|contains:',
      '      - password',
      '      - passwd',
      '      - api_key',
      '  condition: selection',
      'falsepositives:',
      '  - Configuration audits',
      'level: medium',
    ].join('\n'),
  },
  {
    id: '0e5b7a46-2d93-4f18-8c60-9a1f4d3e7b85',
    title: 'List of the running services',
    status: 'test',
    level: 'informational',
    platform: 'linux',
    techniques: ['T1007'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: List of the running services',
      'id: 0e5b7a46-2d93-4f18-8c60-9a1f4d3e7b85',
      'status: test',
      'description: Detects the listing of the running services with systemctl.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.discovery',
      '  - attack.t1007',
      'logsource:',
      '  category: process_creation',
      '  product: linux',
      'detection:',
      '  selection:',
      '    Image|endswith: /systemctl',
      '    CommandLine|contains: list-units',
      '  condition: selection',
      'falsepositives:',
      '  - Normal administration',
      'level: informational',
    ].join('\n'),
  },
  {
    id: '4c8f2e70-5b19-4a93-bd06-7e0a3d9c1f68',
    title: 'Quarantine attribute removed from an application',
    status: 'test',
    level: 'medium',
    platform: 'macos',
    techniques: ['T1553.001'],
    author: SAMPLE_AUTHOR,
    content: [
      'title: Quarantine attribute removed from an application',
      'id: 4c8f2e70-5b19-4a93-bd06-7e0a3d9c1f68',
      'status: test',
      'description: Detects xattr removing the quarantine attribute, so that Gatekeeper does not check the application.',
      `author: ${SAMPLE_AUTHOR}`,
      'tags:',
      '  - attack.defense-evasion',
      '  - attack.t1553.001',
      'logsource:',
      '  category: process_creation',
      '  product: macos',
      'detection:',
      '  selection:',
      '    Image|endswith: /xattr',
      '    CommandLine|contains|all:',
      '      - \'-d\'',
      '      - com.apple.quarantine',
      '  condition: selection',
      'falsepositives:',
      '  - Developers who test their own applications',
      'level: medium',
    ].join('\n'),
  },
  // Rules for the other actions of the sample data.
  sample({
    id: '8a2d6c41-5e90-4b37-9f1a-3c7e0b5d8a62',
    title: 'Network share enumeration or access with an anonymous logon',
    status: 'test',
    level: 'medium',
    platform: 'windows',
    techniques: ['T1135'],
    description: 'Detects a network share opened by the anonymous logon account.',
    tags: ['attack.discovery', 'attack.t1135'],
    logsource: ['product: windows', 'service: security'],
    detection: [
      'selection:',
      '  EventID: 5140',
      '  SubjectUserName: ANONYMOUS LOGON',
      'condition: selection',
    ],
    falsepositives: 'Legacy applications that still use null sessions',
  }),
  sample({
    id: 'c4e8a1d7-2b63-4f90-8d15-7a0e3f6b9c28',
    title: 'Delegation setting changed on an account',
    status: 'test',
    level: 'high',
    platform: 'windows',
    techniques: ['T1558'],
    description: 'Detects a change of the delegation attributes of a computer or a user account.',
    tags: ['attack.credential-access', 'attack.t1558'],
    logsource: ['product: windows', 'service: security'],
    detection: [
      'selection:',
      '  EventID: 5136',
      '  AttributeLDAPDisplayName:',
      '    - msDS-AllowedToDelegateTo',
      '    - msDS-AllowedToActOnBehalfOfOtherIdentity',
      'condition: selection',
    ],
    falsepositives: 'Planned delegation changes made by the administrators',
  }),
  sample({
    id: 'e1b5d903-7c42-4a68-b0f6-9d2a8c4e1f37',
    title: 'Computer account renamed without a trailing dollar sign',
    status: 'test',
    level: 'high',
    platform: 'windows',
    techniques: ['T1136.002'],
    description: 'Detects a computer account whose name loses the trailing dollar sign, the step of the noPac attack.',
    tags: ['attack.persistence', 'attack.privilege-escalation', 'attack.t1136.002', 'cve.2021-42278'],
    logsource: ['product: windows', 'service: security'],
    detection: [
      'selection:',
      '  EventID: 4781',
      '  OldTargetUserName|endswith: $',
      'filter:',
      '  NewTargetUserName|endswith: $',
      'condition: selection and not filter',
    ],
    falsepositives: 'Rare, a computer account renamed by hand',
  }),
  sample({
    id: '2f7a9c58-1d04-4e83-a6b1-5e8d0c3f7b49',
    title: 'LLMNR response sent by a workstation',
    status: 'test',
    level: 'medium',
    platform: 'windows',
    techniques: ['T1557.001'],
    description: 'Detects a host that answers LLMNR queries from its UDP port 5355, a sign of a poisoning tool.',
    tags: ['attack.credential-access', 'attack.t1557.001'],
    logsource: ['category: network_connection', 'product: windows'],
    detection: [
      'selection:',
      '  Initiated: false',
      '  Protocol: udp',
      '  SourcePort: 5355',
      'condition: selection',
    ],
    falsepositives: 'Hosts that run an LLMNR responder on purpose',
  }),
  sample({
    id: '6b0e4d27-8a31-4c95-9e72-1f5c8a3d0b64',
    title: 'OpenSSH server timeout before authentication',
    status: 'test',
    level: 'medium',
    platform: 'linux',
    techniques: ['T1190'],
    cves: ['CVE-2024-6387'],
    description: 'Detects the login grace time of sshd expiring repeatedly, the pattern of the regreSSHion race condition.',
    tags: ['attack.initial-access', 'attack.t1190', 'cve.2024-6387'],
    logsource: ['product: linux', 'service: sshd'],
    detection: [
      'selection:',
      '  - Timeout before authentication',
      'condition: selection | count() by src_ip > 20',
    ],
    falsepositives: 'Scanners and monitoring probes that open SSH sessions without logging in',
  }),
  sample({
    id: '9d3c7e15-4a82-4b06-8f29-0e6b1a5c3d70',
    title: 'Log4Shell JNDI lookup in a web request',
    status: 'stable',
    level: 'high',
    platform: 'linux',
    techniques: ['T1190'],
    cves: ['CVE-2021-44228'],
    description: 'Detects a JNDI lookup string in the URL or the headers of a web request.',
    tags: ['attack.initial-access', 'attack.t1190', 'cve.2021-44228'],
    logsource: ['category: webserver'],
    detection: [
      'selection:',
      '  cs-uri-query|contains: \'${jndi:\'',
      'selection_header:',
      '  cs-user-agent|contains: \'${jndi:\'',
      'condition: selection or selection_header',
    ],
    falsepositives: 'Security scanners run by the company',
  }),
  sample({
    id: '3a8f1c60-9e47-4d02-b5a3-6c2d7e0b4f81',
    title: 'Netlogon vulnerable secure channel connection allowed',
    status: 'test',
    level: 'critical',
    platform: 'windows',
    techniques: ['T1210'],
    cves: ['CVE-2020-1472'],
    description: 'Detects a domain controller that allowed a Netlogon secure channel connection that is vulnerable to Zerologon.',
    tags: ['attack.lateral-movement', 'attack.t1210', 'cve.2020-1472'],
    logsource: ['product: windows', 'service: system'],
    detection: [
      'selection:',
      '  EventID: 5829',
      '  Provider_Name: NETLOGON',
      'condition: selection',
    ],
    falsepositives: 'Old devices that are still on the exception list of the domain controller',
  }),
  sample({
    id: '5e2b9a74-0c18-4f63-a7d5-8b3e1d6c9a02',
    title: 'Remote login enabled over SSH on macOS',
    status: 'test',
    level: 'low',
    platform: 'macos',
    techniques: ['T1021.004'],
    description: 'Detects the Remote Login service being turned on with systemsetup.',
    tags: ['attack.lateral-movement', 'attack.t1021.004'],
    logsource: ['category: process_creation', 'product: macos'],
    detection: [
      'selection:',
      '  Image|endswith: /systemsetup',
      '  CommandLine|contains|all:',
      '    - -setremotelogin',
      '    - \'on\'',
      'condition: selection',
    ],
    falsepositives: 'Administrators who turn on remote access',
  }),
  // Not shown: Sigma marks it experimental.
  {
    id: 'a7d3b1f9-6e24-4c58-9b0a-2f8e5d1c4a96',
    title: 'Experimental rule on LSASS (not shown)',
    status: 'experimental',
    level: 'low',
    platform: 'windows',
    techniques: ['T1003.001'],
    author: SAMPLE_AUTHOR,
    content: 'title: Experimental rule on LSASS (not shown)',
  },
];

// The rules of an action: the ones that carry one of its ATT&CK techniques (the technique itself or its parent), for
// the platform of its logs, plus the ones of the CVE it checks, without the experimental ones, the most severe first.
export const sigmaRulesOf = (techniques: string[], platform: Platform, cves: string[] = []): SigmaRule[] => SIGMA_RULES
  .filter((rule) => {
    if (rule.status === 'experimental') return false;
    if (rule.cves) return rule.cves.some(cve => cves.includes(cve));
    return rule.platform === platform
      && rule.techniques.some(technique => techniques.some(own => own === technique || own.startsWith(`${technique}.`)));
  })
  .sort((a, b) => SIGMA_LEVEL_ORDER.indexOf(a.level) - SIGMA_LEVEL_ORDER.indexOf(b.level));

// PROTOTYPE: the Sigma collector, as the Integrations page will show it. It is a Community Edition collector, so it
// never generates anything: it downloads the SigmaHQ rule set and keeps the rules (YAML, author, source URL).
export const SIGMA_COLLECTOR = {
  name: 'Sigma collector',
  status: 'Running',
  source: 'SigmaHQ',
  schedule: 'Every 24 hours',
  lastSync: 'Today, 04:00 (sample data)',
  rulesSynced: 3112,
  rulesKept: 'Stable and test rules, with their author and source URL',
};
