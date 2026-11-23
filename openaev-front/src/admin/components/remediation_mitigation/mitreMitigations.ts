// ---------------------------------------------------------------------------------------------
// PROTOTYPE - SAMPLE DATA ONLY. Generic mitigations come from MITRE ATT&CK, not from XTM One: every
// technique lists the mitigations MITRE publishes for it, so nothing is generated and nothing is sent.
// In the product this would be the mitigations of the ATT&CK knowledge base the platform already stores.
// Names are proper nouns, never translated.
// ---------------------------------------------------------------------------------------------

export interface MitreMitigationInfo {
  name: string;
  // What the mitigation is, in general.
  description: string;
}

export const MITRE_MITIGATIONS: Record<string, MitreMitigationInfo> = {
  M1015: {
    name: 'Active Directory Configuration',
    description: 'Configure Active Directory to prevent the use of the abused features and to limit what a compromised account can do.',
  },
  M1016: {
    name: 'Vulnerability Scanning',
    description: 'Scan the exposed systems and applications regularly to find the weaknesses before an adversary does.',
  },
  M1017: {
    name: 'User Training',
    description: 'Train users to recognize and report the behavior this technique relies on.',
  },
  M1018: {
    name: 'User Account Management',
    description: 'Limit the permissions of accounts and remove the ones that are not needed.',
  },
  M1022: {
    name: 'Restrict File and Directory Permissions',
    description: 'Restrict access to files and directories to the accounts that need them.',
  },
  M1025: {
    name: 'Privileged Process Integrity',
    description: 'Protect the processes with high privileges so that untrusted code cannot read or tamper with them.',
  },
  M1026: {
    name: 'Privileged Account Management',
    description: 'Manage the creation, use and permissions of privileged accounts, and limit where they can log on.',
  },
  M1027: {
    name: 'Password Policies',
    description: 'Enforce long, unique passwords so that captured material is hard to crack and to reuse.',
  },
  M1028: {
    name: 'Operating System Configuration',
    description: 'Harden the operating system settings the technique depends on.',
  },
  M1030: {
    name: 'Network Segmentation',
    description: 'Isolate critical systems and limit which networks can reach them.',
  },
  M1031: {
    name: 'Network Intrusion Prevention',
    description: 'Use network signatures to identify and block the traffic of the technique.',
  },
  M1032: {
    name: 'Multi-factor Authentication',
    description: 'Require more than a password so that a captured or guessed credential is not enough.',
  },
  M1035: {
    name: 'Limit Access to Resource Over Network',
    description: 'Prevent access to the services and shares of a host from the networks that do not need them.',
  },
  M1036: {
    name: 'Account Use Policies',
    description: 'Lock accounts after failed attempts and limit the number of guesses an adversary can make.',
  },
  M1037: {
    name: 'Filter Network Traffic',
    description: 'Filter the network traffic with firewalls and proxies to block the protocols and destinations that are not needed.',
  },
  M1038: {
    name: 'Execution Prevention',
    description: 'Block the execution of code that is not expected, with application control or script restrictions.',
  },
  M1040: {
    name: 'Behavior Prevention on Endpoint',
    description: 'Use endpoint security to block the behaviors of the technique, such as suspicious access to another process.',
  },
  M1041: {
    name: 'Encrypt Sensitive Information',
    description: 'Use strong encryption so that captured material cannot be used or cracked.',
  },
  M1042: {
    name: 'Disable or Remove Feature or Program',
    description: 'Remove or disable the features and programs the technique abuses when they are not needed.',
  },
  M1043: {
    name: 'Credential Access Protection',
    description: 'Use the protections of the operating system that keep credentials out of reach of the attacker.',
  },
  M1047: {
    name: 'Audit',
    description: 'Audit the systems regularly to find the weaknesses the technique abuses.',
  },
  M1048: {
    name: 'Application Isolation and Sandboxing',
    description: 'Run applications in a sandbox so that the compromise of one does not give access to the rest of the system.',
  },
  M1050: {
    name: 'Exploit Protection',
    description: 'Use exploit protection features to detect and block the conditions of an exploit.',
  },
  M1051: {
    name: 'Update Software',
    description: 'Patch the software so that the weakness the technique exploits is closed.',
  },
};

// A mitigation of a technique: its id and, when MITRE says something specific for the technique, how it applies.
export type TechniqueMitigation = [id: string, how?: string];

export interface MitreTechnique {
  name: string;
  mitigations: TechniqueMitigation[];
}

export const MITRE_TECHNIQUES: Record<string, MitreTechnique> = {
  'T1003.001': {
    name: 'OS Credential Dumping: LSASS Memory',
    mitigations: [
      ['M1043', 'Enable Credential Guard (and LSA protection) so that the secrets in LSASS are not readable by other processes.'],
      ['M1040', 'Enable the attack surface reduction rule that blocks credential stealing from LSASS.'],
      ['M1025', 'Run LSASS as a protected process light (PPL) so that untrusted code cannot open it.'],
      ['M1028', 'Disable WDigest so that clear text passwords are not kept in memory.'],
      ['M1027'],
      ['M1026', 'Limit the accounts that have debug privileges.'],
    ],
  },
  'T1003.006': {
    name: 'OS Credential Dumping: DCSync',
    mitigations: [
      ['M1015', 'Limit the replication rights (Replicating Directory Changes) to the domain controllers.'],
      ['M1026'],
    ],
  },
  'T1558': {
    name: 'Steal or Forge Kerberos Tickets',
    mitigations: [['M1015'], ['M1026'], ['M1027'], ['M1041']],
  },
  'T1558.003': {
    name: 'Steal or Forge Kerberos Tickets: Kerberoasting',
    mitigations: [
      ['M1041', 'Enable AES encryption for the service accounts and disable RC4.'],
      ['M1027', 'Use long random passwords (or group managed service accounts) for the accounts with a service principal name.'],
      ['M1026'],
    ],
  },
  'T1558.004': {
    name: 'Steal or Forge Kerberos Tickets: AS-REP Roasting',
    mitigations: [
      ['M1041'],
      ['M1027', 'Use long passwords for the accounts that cannot require Kerberos pre-authentication.'],
    ],
  },
  'T1134': {
    name: 'Access Token Manipulation',
    mitigations: [['M1026'], ['M1018']],
  },
  'T1135': {
    name: 'Network Share Discovery',
    mitigations: [
      ['M1035', 'Block anonymous access to the shares and list only the shares the account can open.'],
      ['M1030'],
      ['M1028'],
    ],
  },
  'T1021.002': {
    name: 'Remote Services: SMB/Windows Admin Shares',
    mitigations: [
      ['M1037', 'Block SMB (445) between the workstations.'],
      ['M1035'],
      ['M1032'],
      ['M1027'],
      ['M1026'],
      ['M1018'],
    ],
  },
  'T1021.004': {
    name: 'Remote Services: SSH',
    mitigations: [['M1042'], ['M1035'], ['M1032'], ['M1027'], ['M1026'], ['M1018']],
  },
  'T1046': {
    name: 'Network Service Discovery',
    mitigations: [
      ['M1031', 'Detect and block the scanning patterns at the network boundary.'],
      ['M1030', 'Segment the network so that a scan only reaches the services of its own segment.'],
      ['M1042', 'Disable the services that are not needed so that there is nothing to discover.'],
    ],
  },
  'T1007': {
    name: 'System Service Discovery',
    // MITRE: this technique abuses normal system functionality and cannot be easily mitigated.
    mitigations: [],
  },
  'T1068': {
    name: 'Exploitation for Privilege Escalation',
    mitigations: [['M1051'], ['M1050'], ['M1048'], ['M1038'], ['M1026']],
  },
  'T1136.002': {
    name: 'Create Account: Domain Account',
    mitigations: [['M1032'], ['M1026'], ['M1028']],
  },
  'T1190': {
    name: 'Exploit Public-Facing Application',
    mitigations: [
      ['M1051', 'Patch the vulnerable component (CVE) as soon as the fix is available.'],
      ['M1050', 'Use a web application firewall or exploit protection in front of the application.'],
      ['M1030', 'Place the application in a DMZ with limited access to the internal network.'],
      ['M1048'],
      ['M1026'],
      ['M1016'],
    ],
  },
  'T1210': {
    name: 'Exploitation of Remote Services',
    mitigations: [
      ['M1051', 'Install the patch of the vulnerability and enforce the secure channel on the domain controllers.'],
      ['M1050'],
      ['M1048'],
      ['M1042'],
      ['M1030', 'Limit which hosts can reach the domain controllers.'],
      ['M1026'],
    ],
  },
  'T1557.001': {
    name: 'Adversary-in-the-Middle: LLMNR/NBT-NS Poisoning and SMB Relay',
    mitigations: [
      ['M1042', 'Disable LLMNR and NetBIOS name resolution when they are not needed.'],
      ['M1037', 'Block the ports used by the name resolution and by SMB between the workstations.'],
      ['M1031'],
      ['M1017'],
    ],
  },
  'T1110.001': {
    name: 'Brute Force: Password Guessing',
    mitigations: [['M1036'], ['M1032'], ['M1027'], ['M1018']],
  },
  'T1059.004': {
    name: 'Command and Scripting Interpreter: Unix Shell',
    mitigations: [['M1038'], ['M1026'], ['M1042']],
  },
  'T1552.001': {
    name: 'Unsecured Credentials: Credentials In Files',
    mitigations: [['M1047'], ['M1022'], ['M1027'], ['M1017']],
  },
  'T1553.001': {
    name: 'Subvert Trust Controls: Gatekeeper Bypass',
    mitigations: [['M1038'], ['M1026']],
  },
};

export interface TechniqueMitigations {
  technique: string;
  name?: string;
  mitigations: {
    id: string;
    name: string;
    text: string;
    // True when the text says how the mitigation applies to this technique, false for the general one.
    specific: boolean;
    url: string;
  }[];
}

// The mitigations MITRE publishes for each technique of the action, in the order of its techniques.
export const mitigationsOf = (techniques: string[]): TechniqueMitigations[] => techniques.map((technique) => {
  const entry = MITRE_TECHNIQUES[technique];
  return {
    technique,
    name: entry?.name,
    mitigations: (entry?.mitigations ?? []).flatMap(([id, how]) => {
      const info = MITRE_MITIGATIONS[id];
      return info
        ? [{
            id,
            name: info.name,
            text: how ?? info.description,
            specific: !!how,
            url: `https://attack.mitre.org/mitigations/${id}/`,
          }]
        : [];
    }),
  };
});
