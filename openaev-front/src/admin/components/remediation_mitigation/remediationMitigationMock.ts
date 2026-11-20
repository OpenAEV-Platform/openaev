// ---------------------------------------------------------------------------------------------
// PROTOTYPE - SAMPLE DATA ONLY. Nothing here comes from the backend or from XTM One.
// Single typed mock layer behind the "Remediation" and "Mitigation" tabs. The tabs only call
// `fetchRemediationMitigationItems`, so replacing this module by an API call (and the type by the
// generated one in `api-types.d.ts`) does not touch the UI.
//
// Definitions the tabs are built on:
// - Remediation removes the ROOT CAUSE of a weakness (patch, upgrade, configuration/hardening
//   change, credential hygiene).
// - Mitigation is a compensating control: the weakness stays, but the attack is detected or blocked.
// Classification question: "Does it remove the root cause?" Yes -> remediation, no -> mitigation.
//
// The commands, rules, settings and CVE identifiers below are real and were checked against the vendor
// documentation (Microsoft, OpenSSH, Apple, CrowdStrike). Host names, domains, indexes and accounts are
// placeholders to adapt to the environment. Only the way they are stored is a prototype.
// ---------------------------------------------------------------------------------------------

export type RemediationMitigationCategory = 'remediation' | 'mitigation';

export type ControlType = 'hardening' | 'patch' | 'detection' | 'prevention';

export type Scope = 'vendor_specific' | 'generic';

export type CollectorType = 'splunk' | 'crowdstrike';

export type Platform = 'windows' | 'linux' | 'macos';

export type AuthorRule = 'HUMAN' | 'AI' | 'AI_OUTDATED';

// The layer where the change is made.
export type ApplyLayer = 'host' | 'directory_domain' | 'application' | 'network';

export interface RemediationAction {
  title: string;
  steps: string[];
  prerequisites: string[];
  change_risk: string;
}

export interface RemediationMitigationItem {
  id: string;
  category: RemediationMitigationCategory;
  control_type: ControlType;
  // A remediation is always 'generic' (it depends on the target OS/technology, never on a vendor).
  scope: Scope;
  // Only set for a vendor-specific mitigation.
  collector_type?: CollectorType;
  title: string;
  summary: string;
  platform: Platform;
  author_rule: AuthorRule;
  // The guidance must be checked against the environment before it is applied.
  verify?: boolean;
  // Remediation only.
  actions?: RemediationAction[];
  // Mitigation only: the rule text.
  content?: string;
  coverage_notes?: string;
  false_positive_notes?: string;
  assumptions?: string[];
  // Plain text, never a link.
  references?: string[];
  // Which system the suggestion is valid for (operating system, product, version...).
  applies_to?: string;
  // Where the change is made: host, directory / domain, application or network.
  apply_layer?: ApplyLayer;
  // AI items start as 'needs_review' until a person marks them as reviewed.
  review_state?: 'needs_review' | 'reviewed';
}

export const REMEDIATION_CONTROL_TYPES: ControlType[] = ['patch', 'hardening'];

export const MITIGATION_CONTROL_TYPES: ControlType[] = ['detection', 'prevention'];

// Remediation = removes the root cause (patch, upgrade, configuration or credential hardening).
// Mitigation = compensating control: the weakness stays, the attack is detected or blocked.
export const SAMPLE_ITEMS: RemediationMitigationItem[] = [
  // ------------------------------------------------------------------------------------------
  // REMEDIATIONS
  // ------------------------------------------------------------------------------------------
  {
    id: 'sample-rem-kerberoasting-service-accounts',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Harden service accounts against Kerberoasting',
    summary: 'Any domain user can request a service ticket for an account with a service principal name (SPN) and crack it offline. Remove the weakness: managed passwords that cannot be cracked, no unused SPNs, and AES-only Kerberos encryption.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Move service accounts to group Managed Service Accounts (gMSA)',
        steps: [
          'List the user accounts that own an SPN: Get-ADUser -Filter \'ServicePrincipalName -like "*"\' -Properties ServicePrincipalName',
          'Create the KDS root key once per forest (Add-KdsRootKey -EffectiveImmediately in a lab; in production the key is usable 10 hours after creation).',
          'Create a gMSA per service: New-ADServiceAccount -Name svc-app -DNSHostName svc-app.corp.example.com -PrincipalsAllowedToRetrieveManagedPassword AppServers',
          'On the host run Install-ADServiceAccount svc-app, then set the service to log on as CORP\\svc-app$ with an empty password.',
          'For a service that cannot run as a gMSA, set a random password of at least 25 characters and store it in a vault.',
        ],
        prerequisites: [
          'Domain controllers running Windows Server 2012 or later.',
          'The service supports running as a gMSA.',
        ],
        change_risk: 'Medium: a service can fail to start after the account change. Test before production.',
      },
      {
        title: 'Remove unneeded SPNs',
        steps: [
          'Review the list from the first action and keep only SPNs of services that still exist.',
          'Remove the others: setspn -D MSSQLSvc/oldsql.corp.example.com:1433 CORP\\svc-old',
        ],
        prerequisites: ['Delegated right to write the servicePrincipalName attribute.'],
        change_risk: 'Low: only SPNs of decommissioned services are removed.',
      },
      {
        title: 'Allow AES only for service tickets',
        steps: [
          'Set AES on each service account: Set-ADUser svc-app -KerberosEncryptionType AES128,AES256 (msDS-SupportedEncryptionTypes = 24).',
          'In the Default Domain Controllers Policy, set "Network security: Configure encryption types allowed for Kerberos" to AES128_HMAC_SHA1, AES256_HMAC_SHA1 and Future encryption types.',
          'Watch events 4769 with Ticket_Encryption_Type 0x17 (RC4) for clients that still need it.',
        ],
        prerequisites: ['No legacy system still requires RC4.'],
        change_risk: 'High: a legacy application that only supports RC4 stops authenticating. Test before production.',
      },
    ],
    assumptions: [
      'Active Directory domain with Windows Server domain controllers.',
      'The attacker can request service tickets as any authenticated domain user.',
    ],
    references: [
      'MITRE ATT&CK T1558.003: Steal or Forge Kerberos Tickets, Kerberoasting',
      'Microsoft Learn: Group Managed Service Accounts overview',
    ],
  },
  {
    id: 'sample-rem-asrep-preauth',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Require Kerberos pre-authentication on every account',
    summary: 'With the "Do not require Kerberos preauthentication" flag (DONT_REQ_PREAUTH), anyone can ask the domain controller for material encrypted with the account password and crack it offline. Clear the flag.',
    platform: 'windows',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Clear the flag and reset the exposed passwords',
        steps: [
          'Find the accounts: Get-ADUser -Filter \'DoesNotRequirePreAuth -eq $true\' -Properties DoesNotRequirePreAuth',
          'Clear the flag: Set-ADAccountControl -Identity <account> -DoesNotRequirePreAuth $false',
          'Reset the password of each of these accounts, because the old hash may already have been collected.',
        ],
        prerequisites: ['Right to modify the userAccountControl attribute.'],
        change_risk: 'Low: only a legacy Kerberos client that relies on the flag is affected.',
      },
    ],
    assumptions: ['The flag was not set on purpose for a legacy application.'],
    references: ['MITRE ATT&CK T1558.004: Steal or Forge Kerberos Tickets, AS-REP Roasting'],
  },
  {
    id: 'sample-rem-restrict-delegation',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Remove unconstrained Kerberos delegation',
    summary: 'A host trusted for unconstrained delegation stores the TGT of every user who connects to it. Whoever compromises it can impersonate those users, domain admins included. Replace it with resource-based constrained delegation and protect sensitive accounts.',
    platform: 'windows',
    author_rule: 'AI_OUTDATED',
    verify: true,
    actions: [
      {
        title: 'Replace unconstrained delegation',
        steps: [
          'List the objects: Get-ADComputer -Filter \'TrustedForDelegation -eq $true\' -Properties TrustedForDelegation (domain controllers are expected, ignore them) and Get-ADUser -Filter \'TrustedForDelegation -eq $true\'.',
          'Turn it off: Set-ADAccountControl -Identity <object> -TrustedForDelegation $false',
          'Where delegation is still needed, use RBCD: Set-ADComputer <back-end$> -PrincipalsAllowedToDelegateToAccount <front-end$>',
        ],
        prerequisites: ['Knowledge of which services legitimately need delegation.'],
        change_risk: 'Medium: a multi-tier application can lose access to its back end. Test before production.',
      },
      {
        title: 'Protect privileged accounts from delegation',
        steps: [
          'Set "Account is sensitive and cannot be delegated": Set-ADUser <admin> -AccountNotDelegated $true',
          'Add privileged users to the Protected Users group: Add-ADGroupMember "Protected Users" -Members <admin>',
        ],
        prerequisites: ['Domain functional level Windows Server 2012 R2 or later for Protected Users.'],
        change_risk: 'Medium: Protected Users blocks NTLM, DES and RC4 and cached credentials for its members.',
      },
    ],
    assumptions: ['The payload this guidance was written for changed since it was generated.'],
    references: ['MITRE ATT&CK T1558: Steal or Forge Kerberos Tickets'],
  },
  {
    id: 'sample-rem-kerberos-security-update',
    category: 'remediation',
    control_type: 'patch',
    scope: 'generic',
    title: 'Install the November 2021 updates for CVE-2021-42278 and CVE-2021-42287 (noPac)',
    summary: 'CVE-2021-42278 (sAMAccountName spoofing) and CVE-2021-42287 (Kerberos PAC confusion) chain into a domain admin takeover from a standard domain user. The security updates released on 9 November 2021 fix both.',
    platform: 'windows',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Update every domain controller',
        steps: [
          'List the domain controllers and their installed updates: Get-ADDomainController -Filter * | ForEach-Object { Get-HotFix -ComputerName $_.HostName }',
          'Install the cumulative update of 9 November 2021 (or any later one) on each domain controller. One unpatched domain controller keeps the domain exposed.',
          'Restart each domain controller and confirm the build number.',
        ],
        prerequisites: ['A maintenance window.', 'A tested rollback plan for the domain controllers.'],
        change_risk: 'Medium: a restart of each domain controller is required. Update one at a time.',
      },
    ],
    assumptions: ['Domain controllers run a supported Windows Server version.'],
    references: [
      'CVE-2021-42278: Active Directory Domain Services Elevation of Privilege',
      'CVE-2021-42287: Active Directory Domain Services Elevation of Privilege',
    ],
  },
  {
    id: 'sample-rem-machine-account-quota',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Set ms-DS-MachineAccountQuota to 0',
    summary: 'By default every authenticated user can join 10 computers to the domain. That right is the first step of noPac, resource-based delegation abuse and relay attacks. Remove it and delegate joining to a dedicated group.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Remove the default right to create computer accounts',
        steps: [
          'Check the current value: Get-ADDomain | Select-Object -ExpandProperty DistinguishedName | ForEach-Object { Get-ADObject $_ -Properties ms-DS-MachineAccountQuota }',
          'Set it to 0: Set-ADDomain -Identity corp.example.com -Replace @{"ms-DS-MachineAccountQuota"="0"}',
          'Give the help desk group the right "Create Computer objects" on the OU where computers are joined.',
        ],
        prerequisites: ['Domain admin rights.', 'A process that joins computers with a delegated account.'],
        change_risk: 'Medium: users and scripts that join machines with their own account fail until delegated.',
      },
    ],
    references: ['MITRE ATT&CK T1136.002: Create Account, Domain Account'],
  },
  {
    id: 'sample-rem-krbtgt-rotation',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Rotate the KRBTGT account password twice',
    summary: 'The KRBTGT secret signs every Kerberos ticket. A stolen copy (for example through DCSync) lets an attacker forge tickets that stay valid until the secret is changed. Two resets invalidate every forged ticket.',
    platform: 'windows',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Reset the KRBTGT password twice, with a wait between the resets',
        steps: [
          'Check that replication is healthy on every domain controller: repadmin /replsummary',
          'Reset the password once, for example with Microsoft\'s New-KrbtgtKeys.ps1 script, and wait until the change has replicated to every domain controller.',
          'Wait longer than the maximum lifetime of a user ticket (10 hours by default), then reset it a second time.',
        ],
        prerequisites: ['Domain admin rights.', 'Healthy Active Directory replication.'],
        change_risk: 'High: a second reset before replication completes breaks authentication across the domain.',
      },
    ],
    assumptions: ['Single Active Directory forest. In a multi-domain forest each domain has its own KRBTGT.'],
    references: ['MITRE ATT&CK T1558.001: Steal or Forge Kerberos Tickets, Golden Ticket'],
  },
  {
    id: 'sample-rem-dcsync-permissions',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Remove directory replication rights from non-domain-controller accounts',
    summary: 'DCSync works with the rights "Replicating Directory Changes" and "Replicating Directory Changes All" on the domain object. Only domain controllers and the directory synchronization account should hold them.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Find and remove the extra replication rights',
        steps: [
          'List the holders: (Get-Acl "AD:DC=corp,DC=example,DC=com").Access | Where-Object { $_.ObjectType -in "1131f6aa-9c07-11d1-f79f-00c04fc2dcd2","1131f6ad-9c07-11d1-f79f-00c04fc2dcd2" } | Select-Object IdentityReference, ObjectType',
          'Keep Domain Controllers, Enterprise Domain Controllers and the Entra Connect (MSOL_) account if you use it.',
          'Remove the right from every other account in Active Directory Users and Computers, Security, Advanced.',
        ],
        prerequisites: ['Domain admin rights.', 'An inventory of the products that read the directory (sync, backup, migration).'],
        change_risk: 'Medium: a synchronization or backup product that used the right stops working.',
      },
    ],
    references: ['MITRE ATT&CK T1003.006: OS Credential Dumping, DCSync'],
  },
  {
    id: 'sample-rem-lsa-protection',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Run LSASS as a protected process and enable Credential Guard',
    summary: 'LSASS holds credentials in memory. As a protected process (RunAsPPL) only signed code can open it, and with Credential Guard the secrets are moved to an isolated virtualized environment. Both remove the way credential dumpers read them.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Turn on LSA protection',
        steps: [
          'Set the value: reg add HKLM\\SYSTEM\\CurrentControlSet\\Control\\Lsa /v RunAsPPL /t REG_DWORD /d 1 /f (1 = with UEFI lock, 2 = without lock on Windows 11 22H2 and later).',
          'Restart the host, then check for event 12 in the System log with source Wininit: "LSASS.exe was started as a protected process".',
        ],
        prerequisites: ['Secure Boot recommended.', 'Third-party LSA plug-ins are signed.'],
        change_risk: 'Medium: an unsigned authentication or password filter plug-in fails to load.',
      },
      {
        title: 'Enable Credential Guard',
        steps: [
          'Group policy: Computer Configuration, Administrative Templates, System, Device Guard, "Turn On Virtualization Based Security" set to Enabled with "Credential Guard Configuration" set to "Enabled with UEFI lock".',
          'Restart and confirm in msinfo32 that "Credential Guard" is listed under Virtualization-based security services running.',
        ],
        prerequisites: ['64-bit CPU with virtualization extensions, UEFI with Secure Boot, TPM recommended.'],
        change_risk: 'Medium: software that needs NTLMv1, unconstrained delegation or MS-CHAPv2 single sign-on stops working.',
      },
    ],
    assumptions: ['Windows 10 or 11 Enterprise or Education, or Windows Server 2016 or later.'],
    references: ['MITRE ATT&CK T1003.001: OS Credential Dumping, LSASS Memory'],
  },
  {
    id: 'sample-rem-laps',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Give every computer a unique, rotating local administrator password (Windows LAPS)',
    summary: 'When the local administrator password is the same everywhere, one dumped hash gives access to every machine (pass-the-hash). Windows LAPS generates a different password per computer and rotates it automatically.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Deploy Windows LAPS with Active Directory as the backup directory',
        steps: [
          'Extend the schema once per forest: Update-LapsADSchema',
          'Let computers write their own password: Set-LapsADComputerSelfPermission -Identity "OU=Workstations,DC=corp,DC=example,DC=com"',
          'Group policy "Configure password backup directory" set to Active Directory (BackupDirectory = 2), with a password length of 20 characters and an age of 30 days.',
          'Read a password when needed with Get-LapsADPassword -Identity <computer>, restricted to a defined group.',
        ],
        prerequisites: [
          'Windows 11, or Windows Server 2019 or later, with the April 2023 or later update (Windows LAPS is built in).',
          'Schema admin rights.',
        ],
        change_risk: 'Low: only the managed local administrator password changes. Test on a pilot OU first.',
      },
      {
        title: 'Remove the old shared password from scripts and images',
        steps: [
          'Search deployment scripts, task sequences and golden images for the shared password.',
          'Remove it and rebuild the images.',
        ],
        prerequisites: ['Access to the deployment tooling.'],
        change_risk: 'Low: only unattended installs that still use the old password are affected.',
      },
    ],
    assumptions: ['Computers are joined to the domain.'],
    references: ['MITRE ATT&CK T1078.003: Valid Accounts, Local Accounts'],
  },
  {
    id: 'sample-rem-disable-llmnr',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Disable LLMNR and NetBIOS name resolution',
    summary: 'When DNS fails, Windows falls back to LLMNR and NBT-NS and asks the whole subnet. Anyone on the subnet can answer and capture the NTLM authentication that follows. Turn both fallbacks off.',
    platform: 'windows',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Turn off LLMNR with group policy',
        steps: [
          'Computer Configuration, Administrative Templates, Network, DNS Client, "Turn off multicast name resolution" set to Enabled (registry: HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows NT\\DNSClient, EnableMulticast = 0).',
          'Link the policy to the workstation and server organizational units.',
        ],
        prerequisites: ['Reliable DNS for every internal name.'],
        change_risk: 'Low: only names that DNS cannot resolve are affected.',
      },
      {
        title: 'Turn off NetBIOS over TCP/IP',
        steps: [
          'Set DHCP option 001 "Microsoft Disable NetBIOS Option" to 0x2 on the scopes, or per adapter: (Get-WmiObject Win32_NetworkAdapterConfiguration -Filter "IPEnabled=true").SetTcpipNetbios(2)',
          'Check for applications that still rely on short NetBIOS names.',
        ],
        prerequisites: ['No legacy application depends on NetBIOS.'],
        change_risk: 'Medium: legacy file shares and printers can become unreachable by short name.',
      },
    ],
    references: ['MITRE ATT&CK T1557.001: Adversary-in-the-Middle, LLMNR/NBT-NS Poisoning and SMB Relay'],
  },
  {
    id: 'sample-rem-smb-signing',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Require SMB signing on servers and clients',
    summary: 'Without required signing, an NTLM authentication captured on the network can be relayed to another SMB server. Signed sessions cannot be tampered with or relayed.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Require signing through group policy',
        steps: [
          'Computer Configuration, Windows Settings, Security Settings, Local Policies, Security Options: "Microsoft network server: Digitally sign communications (always)" set to Enabled.',
          'Same place: "Microsoft network client: Digitally sign communications (always)" set to Enabled.',
          'Per host: Set-SmbServerConfiguration -RequireSecuritySignature $true -Force and Set-SmbClientConfiguration -RequireSecuritySignature $true -Force',
          'Roll out to a pilot group first and watch for SMB connection failures.',
        ],
        prerequisites: ['Every SMB peer supports signing.'],
        change_risk: 'Medium: old devices, NAS and appliances that cannot sign lose access. Test before production.',
      },
    ],
    assumptions: ['No third-party SMB device without signing support is in use.'],
    references: ['MITRE ATT&CK T1557.001: Adversary-in-the-Middle, LLMNR/NBT-NS Poisoning and SMB Relay'],
  },
  {
    id: 'sample-rem-smb-anonymous-access',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Remove anonymous access to shares and to account enumeration',
    summary: 'Shares that grant Everyone access, and null sessions that may list shares and accounts, let an unauthenticated attacker read data and map the domain. Grant access to named groups only.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Fix the share permissions',
        steps: [
          'List the access of every share: Get-SmbShare | Get-SmbShareAccess',
          'Remove the broad entries: Revoke-SmbShareAccess -Name <share> -AccountName Everyone -Force',
          'Grant access to the groups that need it: Grant-SmbShareAccess -Name <share> -AccountName "CORP\\Finance" -AccessRight Read -Force',
        ],
        prerequisites: ['Rights to administer the file server.'],
        change_risk: 'Medium: users and scripts that relied on open access lose it. Test before production.',
      },
      {
        title: 'Disable null sessions',
        steps: [
          'Security Options: "Network access: Restrict anonymous access to Named Pipes and Shares" set to Enabled.',
          'Security Options: "Network access: Let Everyone permissions apply to anonymous users" set to Disabled.',
          'Security Options: "Network access: Do not allow anonymous enumeration of SAM accounts and shares" set to Enabled.',
        ],
        prerequisites: ['No legacy application reads shares anonymously.'],
        change_risk: 'Medium: legacy tools that enumerate anonymously stop working.',
      },
    ],
    references: ['MITRE ATT&CK T1135: Network Share Discovery'],
  },
  {
    id: 'sample-rem-linux-hardcoded-secrets',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Remove hard-coded credentials and protect SSH keys',
    summary: 'Passwords, tokens and private keys left in scripts, configuration files or unprotected key files are reused by attackers after a first foothold. Remove them, rotate them, and keep key files private.',
    platform: 'linux',
    author_rule: 'AI_OUTDATED',
    verify: true,
    actions: [
      {
        title: 'Find and revoke exposed secrets',
        steps: [
          'Search the hosts: grep -rIlE \'(password|passwd|secret|api[_-]?key)[[:space:]]*[=:]\' /home /opt /etc 2>/dev/null',
          'Scan repositories with a secret scanner such as gitleaks: gitleaks detect --source .',
          'Revoke every secret found, issue a new one and move it to a secret manager.',
        ],
        prerequisites: ['A secret manager available to the affected services.'],
        change_risk: 'Medium: a service that still uses the old secret stops working. Test before production.',
      },
      {
        title: 'Protect private keys',
        steps: [
          'Restrict the permissions: chmod 700 ~/.ssh && chmod 600 ~/.ssh/id_*',
          'Add a passphrase to interactive keys: ssh-keygen -p -f ~/.ssh/id_ed25519',
        ],
        prerequisites: ['Shell access to the hosts.'],
        change_risk: 'Low: automated jobs that rely on a passphrase-less key must be updated.',
      },
    ],
    assumptions: ['The payload this guidance was written for changed since it was generated.'],
    references: ['MITRE ATT&CK T1552.001: Unsecured Credentials, Credentials In Files'],
  },
  {
    id: 'sample-rem-linux-ssh-hardening',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Harden the OpenSSH server configuration',
    summary: 'Password logins and direct root access make password guessing and credential stuffing succeed. Allow key-based access for named users only.',
    platform: 'linux',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Disable password authentication and root login',
        steps: [
          'Confirm that every administrator can log in with a key.',
          'Create /etc/ssh/sshd_config.d/10-hardening.conf with: PasswordAuthentication no, KbdInteractiveAuthentication no, PermitRootLogin no, MaxAuthTries 3',
          'Validate the configuration: sudo sshd -t',
          'Reload without closing the current session: sudo systemctl reload sshd (the unit is called ssh on Debian and Ubuntu).',
        ],
        prerequisites: ['Key-based access tested for every administrator.', 'Console access in case of lockout.', 'OpenSSH 8.2 or later (reads the sshd_config.d directory).'],
        change_risk: 'Medium: a wrong setting can lock administrators out. Keep a session open while testing.',
      },
    ],
    assumptions: ['OpenSSH is the SSH server in use.'],
    references: ['MITRE ATT&CK T1110.001: Brute Force, Password Guessing'],
  },
  {
    id: 'sample-rem-linux-package-update',
    category: 'remediation',
    control_type: 'patch',
    scope: 'generic',
    title: 'Upgrade OpenSSH to 9.8p1 or the distribution fix for CVE-2024-6387 (regreSSHion)',
    summary: 'A race condition in the sshd signal handler allows unauthenticated remote code execution as root on glibc-based Linux. Portable OpenSSH 8.5p1 to 9.7p1 is affected; 9.8p1 (1 July 2024) fixes it. Distributions ship the fix as a backport.',
    platform: 'linux',
    author_rule: 'AI',
    actions: [
      {
        title: 'Upgrade the package and restart the service',
        steps: [
          'Check the version: ssh -V, then compare the package changelog with the distribution advisory, because a backported fix keeps an older version string.',
          'Debian and Ubuntu: sudo apt update && sudo apt install --only-upgrade openssh-server',
          'RHEL, Rocky and Alma: sudo dnf upgrade openssh-server',
          'Restart the daemon: sudo systemctl restart sshd, then confirm the new package version.',
        ],
        prerequisites: ['Package repository access.', 'A maintenance window for the restart.'],
        change_risk: 'Low: existing sessions stay open, new connections use the restarted daemon.',
      },
    ],
    references: ['CVE-2024-6387: OpenSSH sshd signal handler race condition (regreSSHion)', 'OpenSSH 9.8 release notes'],
  },
  {
    id: 'sample-rem-macos-os-update',
    category: 'remediation',
    control_type: 'patch',
    scope: 'generic',
    title: 'Install macOS 13.1, 12.6.2 or 11.7.2 or later for CVE-2022-42821 (Gatekeeper bypass)',
    summary: 'CVE-2022-42821 (Achilles) lets a downloaded app avoid the quarantine attribute and run without Gatekeeper checks. Apple fixed it on 13 December 2022 in macOS Ventura 13.1, Monterey 12.6.2 and Big Sur 11.7.2.',
    platform: 'macos',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Push the update and enforce a deadline',
        steps: [
          'Check the versions in use: sw_vers -productVersion',
          'List what is available: softwareupdate --list',
          'Install: sudo softwareupdate --install --all --restart (or through managed software updates in your device management solution).',
          'Set an installation deadline and report the devices that missed it.',
        ],
        prerequisites: ['Managed devices, or users able to install updates.'],
        change_risk: 'Low: a restart is required.',
      },
    ],
    references: ['CVE-2022-42821: macOS Gatekeeper bypass (Achilles)', 'Apple security releases'],
  },
  {
    id: 'sample-rem-macos-gatekeeper',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Enforce Gatekeeper and block the user override',
    summary: 'Unsigned or unnotarized apps are the usual delivery path of commodity macOS malware. Enforce Gatekeeper with a configuration profile, so apps from identified developers and the App Store run and users cannot turn the check off.',
    platform: 'macos',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Deploy a configuration profile',
        steps: [
          'Payload com.apple.systempolicy.control with EnableAssessment = true and AllowIdentifiedDevelopers = true.',
          'Payload com.apple.systempolicy.managed with DisableOverride = true, so the "Open Anyway" bypass is removed.',
          'Deploy the profile with your device management solution and check: spctl --status (expected: "assessments enabled").',
        ],
        prerequisites: ['Managed devices.', 'A process to approve exceptions.'],
        change_risk: 'Medium: internal tools that are not notarized stop launching. Allow-list them first.',
      },
    ],
    assumptions: ['Devices are enrolled in a device management solution.'],
    references: ['MITRE ATT&CK T1553.001: Subvert Trust Controls, Gatekeeper Bypass'],
  },

  // ------------------------------------------------------------------------------------------
  // MITIGATIONS
  // ------------------------------------------------------------------------------------------
  {
    id: 'sample-mit-splunk-kerberoasting-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect many RC4 service ticket requests by one account',
    summary: 'Kerberoasting tools ask for service tickets of many SPNs and usually request RC4 (0x17) because it is the fastest to crack. One account asking for more than five distinct services in five minutes is a strong signal.',
    platform: 'windows',
    author_rule: 'AI',
    content: [
      'index=wineventlog sourcetype="WinEventLog:Security" EventCode=4769',
      '  Ticket_Encryption_Type=0x17 Service_Name!="krbtgt" Service_Name!="*$"',
      '| bin _time span=5m',
      '| stats dc(Service_Name) AS distinct_services values(Service_Name) AS services BY _time, Account_Name, Client_Address',
      '| where distinct_services > 5',
    ].join('\n'),
    coverage_notes: 'Covers tooling that requests RC4 tickets. Requests that already use AES are not covered, which is another reason to enforce AES on service accounts.',
    false_positive_notes: 'Legacy applications that still require RC4 can trigger it. Tune the threshold per environment.',
    assumptions: ['Event 4769 (Kerberos Service Ticket Operations) is audited on the domain controllers and forwarded to Splunk with the Windows add-on field names.'],
  },
  {
    id: 'sample-mit-crowdstrike-kerberoasting-ioa',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'crowdstrike',
    title: 'Detect Kerberoasting tooling with a custom IOA',
    summary: 'Custom indicator of attack on process creation that matches the command lines of the common Kerberoasting tools.',
    platform: 'windows',
    author_rule: 'HUMAN',
    content: [
      'Rule name: Kerberoasting tool execution',
      'Severity: High',
      'Tactic / Technique: Credential Access / Steal or Forge Kerberos Tickets: Kerberoasting (T1558.003)',
      'Rule type: Process Creation',
      'Image Filename: .*',
      'Command Line: .*(Invoke-Kerberoast|Request-SPNTicket|Get-DomainSPNTicket|GetUserSPNs|Rubeus(\\.exe)?\\s+kerberoast).*',
      'Action: Detect',
    ].join('\n'),
    coverage_notes: 'Matches the known tool names and cmdlets. Renamed or in-memory tooling that hides its command line is not covered.',
    false_positive_notes: 'Security testing teams and audit scripts can match. Add exclusions for approved hosts.',
    assumptions: ['The prevention policy of the host group allows custom IOA rules.'],
  },
  {
    id: 'sample-mit-crowdstrike-kerberoasting-prevention',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'vendor_specific',
    collector_type: 'crowdstrike',
    title: 'Block Kerberoasting tooling with a custom IOA',
    summary: 'The same indicator as the detection rule, with the action set to block, so the process is stopped before it requests any ticket.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    content: [
      'Rule name: Block Kerberoasting tool execution',
      'Severity: Critical',
      'Tactic / Technique: Credential Access / Steal or Forge Kerberos Tickets: Kerberoasting (T1558.003)',
      'Rule type: Process Creation',
      'Image Filename: .*',
      'Command Line: .*(Invoke-Kerberoast|Request-SPNTicket|Get-DomainSPNTicket|GetUserSPNs|Rubeus(\\.exe)?\\s+kerberoast).*',
      'Action: Block Execution',
      'Note: start with Monitor and review the matches before switching the action to Block Execution.',
    ].join('\n'),
    coverage_notes: 'Stops the matching process before it requests tickets. Attacks that use renamed tooling are not covered.',
    false_positive_notes: 'A blocked approved audit tool can disrupt an authorized assessment. Add exclusions for those hosts.',
    assumptions: ['The prevention policy of the target host group allows custom IOA rules.'],
  },
  {
    id: 'sample-mit-splunk-spn-change-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect a service principal name added to a user account',
    summary: 'A new SPN on a user account makes that account a Kerberoasting target, and attackers with write access add one on purpose (targeted Kerberoasting). Alert when a servicePrincipalName value is added.',
    platform: 'windows',
    author_rule: 'AI_OUTDATED',
    verify: true,
    content: [
      'index=wineventlog sourcetype="WinEventLog:Security" EventCode=5136',
      '  Attribute_LDAP_Display_Name=servicePrincipalName Operation_Type="%%14674"',
      '| table _time, Subject_Account_Name, Object_DN, Attribute_Value',
    ].join('\n'),
    coverage_notes: 'Covers SPN additions made through any directory tool, because event 5136 is written by the directory service itself.',
    false_positive_notes: 'Planned service deployments add SPNs legitimately. Exclude the change window and the service installer accounts.',
    assumptions: [
      '"Audit Directory Service Changes" is enabled and the domain object has an auditing entry for servicePrincipalName writes.',
      'The payload this rule was written for changed since it was generated.',
    ],
  },
  {
    id: 'sample-mit-splunk-asrep-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect AS-REP requests made without pre-authentication',
    summary: 'A successful TGT request with Pre_Authentication_Type 0 means the account does not require pre-authentication and someone asked for its AS-REP, which is the AS-REP roasting pattern.',
    platform: 'windows',
    author_rule: 'HUMAN',
    content: [
      'index=wineventlog sourcetype="WinEventLog:Security" EventCode=4768',
      '  Pre_Authentication_Type=0 Result_Code=0x0',
      '| stats count values(Client_Address) AS sources BY Account_Name',
    ].join('\n'),
    coverage_notes: 'Covers requests for accounts that do not require pre-authentication. Each hit also identifies a misconfigured account.',
    false_positive_notes: 'A legacy client configured without pre-authentication will appear. Review the account list first.',
    assumptions: ['Event 4768 (Kerberos Authentication Service) is audited on every domain controller and forwarded to Splunk.'],
  },
  {
    id: 'sample-mit-splunk-anonymous-share-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect share access by the anonymous logon',
    summary: 'Event 5140 is written each time a network share is accessed. The built-in ANONYMOUS LOGON account should never open a share on a hardened file server.',
    platform: 'windows',
    author_rule: 'AI',
    content: [
      'index=wineventlog sourcetype="WinEventLog:Security" EventCode=5140 Account_Name="ANONYMOUS LOGON"',
      '| stats count values(Share_Name) AS shares BY Source_Address, ComputerName',
    ].join('\n'),
    coverage_notes: 'Covers anonymous access to any share. It does not tell which files were read: add event 5145 (detailed file share) for that.',
    false_positive_notes: 'Some legacy printers and scanners use anonymous access. Allow-list their addresses.',
    assumptions: ['Success auditing of the "File Share" subcategory is enabled on the file servers and the events are forwarded to Splunk.'],
  },
  {
    id: 'sample-mit-splunk-delegation-change-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect "Trusted For Delegation" set on a user or computer',
    summary: 'Turning on unconstrained delegation on a new object is how attackers prepare a host that collects the TGT of every user who connects to it. Alert on the change of the flag.',
    platform: 'windows',
    author_rule: 'AI',
    content: [
      'index=wineventlog sourcetype="WinEventLog:Security" (EventCode=4738 OR EventCode=4742)',
      '  "Trusted For Delegation" "Enabled"',
      '| stats count BY host, EventCode, Subject_Account_Name',
    ].join('\n'),
    coverage_notes: 'Covers the change of the userAccountControl flag on user accounts (4738) and computer accounts (4742).',
    false_positive_notes: 'Promoting a new domain controller sets the flag. Exclude those computers.',
    assumptions: ['"Audit User Account Management" and "Audit Computer Account Management" are enabled and forwarded to Splunk.'],
  },
  {
    id: 'sample-mit-splunk-dcsync-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect directory replication requested by a non-domain-controller account',
    summary: 'DCSync is a directory replication request (DS-Replication-Get-Changes and Get-Changes-All). When it comes from a user account instead of a domain controller, an attacker is dumping the password hashes of the domain.',
    platform: 'windows',
    author_rule: 'AI',
    content: [
      'index=wineventlog sourcetype="WinEventLog:Security" EventCode=4662',
      '  (Properties="*1131f6aa-9c07-11d1-f79f-00c04fc2dcd2*" OR Properties="*1131f6ad-9c07-11d1-f79f-00c04fc2dcd2*" OR Properties="*89e95b76-444d-4c62-991a-0facbeda640c*")',
      '  Account_Name!="*$"',
      '| stats count BY Account_Name, Account_Domain, host',
    ].join('\n'),
    coverage_notes: 'Covers DCSync run with any tool, because the replication request is the same. Machine accounts (domain controllers) are excluded by the trailing $.',
    false_positive_notes: 'The directory synchronization account of Entra Connect (MSOL_) replicates legitimately. Exclude it.',
    assumptions: ['"Audit Directory Service Access" is enabled and the domain object has an auditing entry for Everyone on the replication rights.'],
  },
  {
    id: 'sample-mit-splunk-samaccountname-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect a computer account renamed without the trailing $',
    summary: 'noPac renames a machine account so that its sAMAccountName no longer ends with $ and looks like a domain controller. Event 4781 records the rename.',
    platform: 'windows',
    author_rule: 'HUMAN',
    content: [
      'index=wineventlog sourcetype="WinEventLog:Security" EventCode=4781',
      '  Old_Account_Name="*$" NOT New_Account_Name="*$"',
      '| table _time, host, Subject_Account_Name, Old_Account_Name, New_Account_Name',
    ].join('\n'),
    coverage_notes: 'Covers the first step of CVE-2021-42278 abuse even on unpatched domain controllers.',
    false_positive_notes: 'Rare: renaming a computer account to a name without $ is almost never legitimate.',
    assumptions: ['"Audit User Account Management" is enabled on the domain controllers and forwarded to Splunk.'],
  },
  {
    id: 'sample-mit-splunk-ssh-bruteforce-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect repeated failed SSH logins from one source',
    summary: 'Flags a source address that fails more than ten SSH authentications in five minutes.',
    platform: 'linux',
    author_rule: 'AI',
    content: [
      'index=linux sourcetype=linux_secure ("Failed password" OR "Invalid user")',
      '| rex "from (?<src_ip>\\d{1,3}(?:\\.\\d{1,3}){3})"',
      '| bin _time span=5m',
      '| stats count BY _time, src_ip',
      '| where count > 10',
    ].join('\n'),
    coverage_notes: 'Covers password guessing against hosts that log to the authentication log (/var/log/secure or /var/log/auth.log).',
    false_positive_notes: 'Vulnerability scanners and misconfigured automation can trigger it. Allow-list their addresses.',
    assumptions: ['The SSH authentication log is forwarded to Splunk.'],
  },
  {
    id: 'sample-mit-crowdstrike-lsass-ioa',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'crowdstrike',
    title: 'Detect credential dumping from LSASS memory',
    summary: 'Custom indicator of attack on process creation for the command lines used to read or dump LSASS memory.',
    platform: 'windows',
    author_rule: 'AI',
    content: [
      'Rule name: LSASS credential dumping tool',
      'Severity: High',
      'Tactic / Technique: Credential Access / OS Credential Dumping: LSASS Memory (T1003.001)',
      'Rule type: Process Creation',
      'Image Filename: .*',
      'Command Line: .*(sekurlsa::|procdump(64)?(\\.exe)?\\s.*lsass|comsvcs\\.dll.*(#24|MiniDump)).*',
      'Action: Detect',
    ].join('\n'),
    coverage_notes: 'Matches the command lines of Mimikatz, ProcDump and the comsvcs.dll MiniDump technique. Custom tooling and direct system calls are not covered.',
    false_positive_notes: 'Administrators collecting crash dumps with approved tools can match. Add exclusions.',
    assumptions: ['The prevention policy of the host group allows custom IOA rules.'],
  },
  {
    id: 'sample-mit-crowdstrike-linux-reverse-shell',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'crowdstrike',
    title: 'Detect a reverse shell started by a web server process',
    summary: 'Custom indicator of attack on process creation: a shell started by the web server with the classic redirection to a TCP socket.',
    platform: 'linux',
    author_rule: 'AI',
    verify: true,
    content: [
      'Rule name: Reverse shell from web service',
      'Severity: High',
      'Tactic / Technique: Execution / Command and Scripting Interpreter: Unix Shell (T1059.004)',
      'Rule type: Process Creation',
      'Parent Image Filename: .*/(nginx|apache2|httpd|php-fpm.*)',
      'Command Line: .*(bash -i|/dev/tcp/|nc(at)? .* -e |mkfifo .* nc ).*',
      'Action: Detect',
    ].join('\n'),
    coverage_notes: 'Covers the most common reverse shell one-liners started by the web server.',
    false_positive_notes: 'Health-check scripts that open a TCP socket can match. Review before enabling.',
  },
  {
    id: 'sample-mit-crowdstrike-temp-exec-prevention',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'vendor_specific',
    collector_type: 'crowdstrike',
    title: 'Block execution of binaries from user temp and download folders',
    summary: 'Custom indicator of attack that blocks executables launched from the temporary and download folders, where droppers and dumping tools are usually written first.',
    platform: 'windows',
    author_rule: 'HUMAN',
    content: [
      'Rule name: Block execution from user temp folders',
      'Severity: Medium',
      'Rule type: Process Creation',
      'Image Filename: .*\\\\(AppData\\\\Local\\\\Temp|Downloads)\\\\.*\\.exe',
      'Command Line: .*',
      'Action: Block Execution',
      'Note: start with Monitor and review the matches before enabling Block Execution.',
    ].join('\n'),
    coverage_notes: 'Stops the common dropper behaviour. Binaries launched from other folders are not covered.',
    false_positive_notes: 'Some installers and portable tools run from these folders. Add exclusions for approved ones.',
    assumptions: ['The prevention policy of the target host group allows custom IOA rules.'],
  },
  {
    id: 'sample-mit-generic-sysmon-lsass',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'generic',
    title: 'Log process access to LSASS with Sysmon (event 10)',
    summary: 'Host telemetry with no vendor: Sysmon writes event 10 each time a process opens LSASS with a memory-read access mask, so any log pipeline can alert on it.',
    platform: 'windows',
    author_rule: 'HUMAN',
    content: [
      '<Sysmon schemaversion="4.90">',
      '  <EventFiltering>',
      '    <RuleGroup name="lsass-access" groupRelation="or">',
      '      <ProcessAccess onmatch="include">',
      '        <Rule name="LSASS memory access" groupRelation="and">',
      '          <TargetImage condition="is">C:\\Windows\\System32\\lsass.exe</TargetImage>',
      '          <GrantedAccess condition="is any">0x1010;0x1410;0x1438;0x143a;0x1fffff</GrantedAccess>',
      '        </Rule>',
      '      </ProcessAccess>',
      '    </RuleGroup>',
      '  </EventFiltering>',
      '</Sysmon>',
    ].join('\n'),
    coverage_notes: 'Produces the events needed to detect memory reads of LSASS. It does not alert or block by itself.',
    false_positive_notes: 'Security agents, monitoring tools and Windows itself (wininit, csrss, MsMpEng) access LSASS legitimately. Exclude their images.',
    assumptions: ['Sysmon is installed on the host and its events are collected.'],
  },
  {
    id: 'sample-mit-generic-asr-lsass',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Block credential stealing from LSASS with an attack surface reduction rule',
    summary: 'Built-in Microsoft Defender Antivirus protection with no extra vendor. The rule "Block credential stealing from the Windows local security authority subsystem (lsass.exe)" stops untrusted processes from reading LSASS. Start in audit mode, then enforce.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    content: [
      '# 1. Audit mode first (events 1122 in Microsoft-Windows-Windows Defender/Operational)',
      'Add-MpPreference -AttackSurfaceReductionRules_Ids 9e6c4e1f-7d60-472f-ba1a-a39ef669e4b2 -AttackSurfaceReductionRules_Actions AuditMode',
      '# 2. Enforce after reviewing the audit events (blocks are events 1121)',
      'Add-MpPreference -AttackSurfaceReductionRules_Ids 9e6c4e1f-7d60-472f-ba1a-a39ef669e4b2 -AttackSurfaceReductionRules_Actions Enabled',
    ].join('\n'),
    coverage_notes: 'Prevents direct reads of LSASS memory by untrusted processes. Microsoft notes the rule is redundant when LSA protection and Credential Guard are on.',
    false_positive_notes: 'Some security and backup tools read LSASS. Review the audit events before enforcing.',
    assumptions: ['Microsoft Defender Antivirus is the active antimalware solution.'],
  },
  {
    id: 'sample-mit-generic-smb-scope',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Only accept inbound SMB from the management and client networks',
    summary: 'Host firewall control: scope the built-in "File and Printer Sharing (SMB-In)" rules to the address ranges that need the file service. SMB stays enabled, but it is no longer reachable from anywhere else.',
    platform: 'windows',
    author_rule: 'AI',
    content: [
      '# Replace the ranges with the networks of your file service clients',
      'Set-NetFirewallRule -DisplayName "File and Printer Sharing (SMB-In)" -RemoteAddress 10.10.0.0/24,10.20.0.0/16',
      '# Check the result',
      'Get-NetFirewallRule -DisplayName "File and Printer Sharing (SMB-In)" | Get-NetFirewallAddressFilter',
    ].join('\n'),
    coverage_notes: 'Reduces the reach of anonymous or relayed SMB access to the allowed ranges. The share and signing weaknesses are not fixed.',
    false_positive_notes: 'Clients outside the allowed ranges lose access to the shares. Include every subnet that uses the server.',
  },
  {
    id: 'sample-mit-generic-disable-spooler',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Disable the Print Spooler service where printing is not needed',
    summary: 'Compensating control recommended by Microsoft for PrintNightmare (CVE-2021-34527) and for the printer bug, which coerces a server to authenticate to an attacker-controlled host (for example one trusted for unconstrained delegation). While the service is stopped, its code is not reachable.',
    platform: 'windows',
    author_rule: 'AI_OUTDATED',
    content: [
      'Stop-Service -Name Spooler -Force',
      'Set-Service -Name Spooler -StartupType Disabled',
      '# Check',
      'Get-Service -Name Spooler | Select-Object Status, StartType',
    ].join('\n'),
    coverage_notes: 'Removes the attack surface of the service on hosts that do not print, domain controllers included. The delegation and patch weaknesses are not fixed.',
    false_positive_notes: 'Printing from or to the host stops. Apply only to hosts that are not print servers or print clients.',
    assumptions: ['The payload this rule was written for changed since it was generated.'],
  },
  {
    id: 'sample-mit-generic-sshd-logingracetime',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Set LoginGraceTime to 0 while the OpenSSH update is pending',
    summary: 'Workaround documented by the OpenSSH project for CVE-2024-6387: the race condition is triggered by the login grace timeout, and with LoginGraceTime 0 the signal handler never runs. The flaw is still in the binary.',
    platform: 'linux',
    author_rule: 'AI',
    verify: true,
    content: [
      '# /etc/ssh/sshd_config.d/99-regresshion-workaround.conf',
      'LoginGraceTime 0',
      '',
      '# Validate and apply',
      'sudo sshd -t && sudo systemctl reload sshd',
    ].join('\n'),
    coverage_notes: 'Removes the race window. The exposure is a denial of service instead of remote code execution, so apply the OpenSSH update as soon as possible.',
    false_positive_notes: 'With no grace time, unauthenticated connections are never dropped by timeout and can exhaust MaxStartups. Keep MaxStartups low and limit who can reach port 22.',
    assumptions: ['The OpenSSH version in use is between 8.5p1 and 9.7p1 and has no distribution backport yet.'],
  },
  {
    id: 'sample-mit-generic-linux-egress',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Restrict outbound traffic of the web service account',
    summary: 'Host firewall rules that stop a compromised web service from opening a reverse shell or exfiltrating data: it can answer its clients and reach the internal network, and nothing else.',
    platform: 'linux',
    author_rule: 'HUMAN',
    content: [
      '# Rules are evaluated in this order. Adapt the user name and the internal range.',
      'nft add rule inet filter output meta skuid "www-data" oifname "lo" accept',
      'nft add rule inet filter output meta skuid "www-data" ct state established,related accept',
      'nft add rule inet filter output meta skuid "www-data" ip daddr 10.0.0.0/8 accept',
      'nft add rule inet filter output meta skuid "www-data" counter drop',
    ].join('\n'),
    coverage_notes: 'Blocks new connections from the service account to the internet, which stops most reverse shells and data exfiltration over a direct connection.',
    false_positive_notes: 'Updates, webhooks and calls to external APIs made by the service are blocked. Allow-list their destinations before the final drop rule.',
    assumptions: ['The table "inet filter" and an "output" chain already exist.'],
  },
  {
    id: 'sample-mit-generic-linux-block-ssh-source',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Limit SSH access to the management network',
    summary: 'Host firewall rules that only accept SSH from the management range, so password guessing and exploitation attempts from other networks never reach sshd.',
    platform: 'linux',
    author_rule: 'AI_OUTDATED',
    content: [
      '# Replace the range with your management network',
      'nft add rule inet filter input tcp dport 22 ip saddr 10.10.0.0/24 accept',
      'nft add rule inet filter input tcp dport 22 drop',
    ].join('\n'),
    coverage_notes: 'Stops SSH attacks that come from outside the management range.',
    false_positive_notes: 'Administrators connecting from another network lose access. Keep console access available.',
    assumptions: [
      'The table "inet filter" and an "input" chain already exist.',
      'The payload this rule was written for changed since it was generated.',
    ],
  },
  {
    id: 'sample-mit-generic-auditd-shadow',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'generic',
    title: 'Audit reads and changes of the password hash files',
    summary: 'Host audit rules that record every read, write or attribute change of the files holding password hashes, so access by anything other than the system tools stands out.',
    platform: 'linux',
    author_rule: 'AI',
    content: [
      '# /etc/audit/rules.d/shadow.rules, then: sudo augenrules --load',
      '-w /etc/shadow -p rwa -k shadow_access',
      '-w /etc/gshadow -p rwa -k shadow_access',
      '-w /etc/passwd -p wa -k passwd_change',
    ].join('\n'),
    coverage_notes: 'Records access and changes of the files with the executable and user that made them (ausearch -k shadow_access). It does not block anything.',
    false_positive_notes: 'login, sshd, sudo and account management tools read these files all the time. Filter on the executable (exe=) and correlate writes with change tickets.',
    assumptions: ['The audit daemon (auditd) is running and its log is collected.'],
  },
  {
    id: 'sample-mit-generic-macos-remote-login',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Turn off Remote Login and block inbound connections',
    summary: 'Host control for a Mac that does not need to be reached remotely: stop the SSH service and have the application firewall refuse inbound connections.',
    platform: 'macos',
    author_rule: 'HUMAN',
    content: [
      'sudo systemsetup -setremotelogin off',
      'sudo /usr/libexec/ApplicationFirewall/socketfilterfw --setglobalstate on',
      'sudo /usr/libexec/ApplicationFirewall/socketfilterfw --setblockall on',
    ].join('\n'),
    coverage_notes: 'Removes the SSH attack surface of the Mac. Weak SSH passwords elsewhere are not fixed.',
    false_positive_notes: 'Remote administration and file sharing stop working. Apply only to Macs that do not need them.',
    assumptions: ['The terminal running the first command has Full Disk Access on recent macOS versions.'],
  },

  // Zerologon (CVE-2020-1472): a flaw in the Netlogon secure channel (MS-NRPC), fixed by Microsoft in two
  // phases (initial fix 11 August 2020, enforcement mode from 9 February 2021, mandatory and not reversible).
  {
    id: 'sample-rem-zerologon-patch',
    category: 'remediation',
    control_type: 'patch',
    scope: 'generic',
    title: 'Install the August 2020 security update for CVE-2020-1472 (Zerologon) on every domain controller',
    summary: 'CVE-2020-1472 is a critical flaw (CVSS 10) in the Netlogon secure channel that lets an unauthenticated attacker on the network take over a domain controller. Microsoft fixed it on 11 August 2020; enforcement mode became mandatory for every domain controller on 9 February 2021.',
    platform: 'windows',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Update every domain controller',
        steps: [
          'List the domain controllers and their installed updates: Get-ADDomainController -Filter * | ForEach-Object { Get-HotFix -ComputerName $_.HostName }',
          'Install the 11 August 2020 cumulative update (or any later one) on each domain controller. One unpatched domain controller keeps the domain exposed.',
          'Restart each domain controller and confirm the build number.',
          'Confirm enforcement mode is active: since 9 February 2021 Microsoft enables it on every patched domain controller and it cannot be turned off.',
        ],
        prerequisites: ['A maintenance window.', 'A tested rollback plan for the domain controllers.'],
        change_risk: 'Medium: a restart of each domain controller is required. Update one at a time.',
      },
    ],
    assumptions: ['Domain controllers run a supported Windows Server version. Windows Server 2008 R2 SP1 needs an Extended Security Update license to receive this fix.'],
    references: ['CVE-2020-1472: Netlogon Elevation of Privilege Vulnerability (Zerologon)', 'Microsoft KB4557222'],
  },
  {
    id: 'sample-rem-zerologon-enforcement',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Remove every exception to Netlogon secure RPC enforcement',
    summary: 'Before 9 February 2021, Microsoft let administrators allow specific non-Windows devices to keep using the vulnerable Netlogon connection while they were updated. Any such exception that is still configured keeps a bypass of the fix open.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Check and clear the group policy exception list',
        steps: [
          'Open the policy "Domain controller: Allow vulnerable Netlogon secure channel connections" in the Default Domain Controllers Policy.',
          'If it lists any account, confirm every one of those devices has since been updated or replaced to support secure RPC.',
          'Set the policy back to Not Defined (or an empty list) so no account is exempted from enforcement.',
          'Watch events 5827 and 5828 (denied vulnerable connections) for any device that stops working, and update it instead of re-adding the exception.',
        ],
        prerequisites: ['An inventory of the non-Windows devices that use Netlogon (network appliances, NAS, older Samba versions).'],
        change_risk: 'Medium: a device still on the exception list loses its domain connection until it is updated.',
      },
    ],
    assumptions: ['Domain controllers are already on the fix released 11 August 2020 or later.'],
    references: ['Microsoft KB4557222: How to manage the changes in Netlogon secure channel connections'],
  },
  {
    id: 'sample-mit-splunk-zerologon-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect vulnerable Netlogon secure channel connections',
    summary: 'The Netlogon events Microsoft added for CVE-2020-1472 record every connection that used the vulnerable protocol. Event 5829 (allowed, before enforcement) is the one to act on; 5827 and 5828 are connections already denied.',
    platform: 'windows',
    author_rule: 'HUMAN',
    content: [
      'index=wineventlog sourcetype="WinEventLog:System" source="Microsoft-Windows-Netlogon"',
      '  (EventCode=5827 OR EventCode=5828 OR EventCode=5829 OR EventCode=5830 OR EventCode=5831)',
      '| stats count values(EventCode) AS event_codes BY host, Computer_Account',
    ].join('\n'),
    coverage_notes: 'Covers every vulnerable Netlogon connection attempt recorded by a patched domain controller, whether denied or still allowed.',
    false_positive_notes: 'Events 5830 and 5831 are connections allowed only because of a configured exception: review and remove the exception instead of ignoring the alert.',
    assumptions: ['The domain controllers run the update that adds these event IDs (11 August 2020 or later) and the System log is forwarded to Splunk.'],
  },
  {
    id: 'sample-mit-generic-zerologon-network',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Restrict who can reach the domain controller RPC endpoint mapper',
    summary: 'Netlogon is reached through RPC (TCP port 135 plus a dynamic high port, or 445 over SMB). Limiting what can open a connection to the domain controllers on these ports removes the network path for an unpatched or unenforced domain controller, in addition to the real fix.',
    platform: 'windows',
    author_rule: 'AI',
    verify: true,
    content: [
      '# Illustrative scope, adapt the source range to your management and server subnets',
      'New-NetFirewallRule -DisplayName "Restrict RPC Endpoint Mapper to management range" -Direction Inbound -Protocol TCP -LocalPort 135 -RemoteAddress 10.10.0.0/24 -Action Allow',
      'New-NetFirewallRule -DisplayName "Block RPC Endpoint Mapper from other ranges" -Direction Inbound -Protocol TCP -LocalPort 135 -Action Block',
    ].join('\n'),
    coverage_notes: 'Reduces who can send a Netlogon RPC request to the domain controllers. The Netlogon weakness itself is only removed by the patch (see Remediation).',
    false_positive_notes: 'Servers and applications that need to reach the domain controller over RPC from outside the allowed range lose that access. Include every subnet with domain-joined hosts.',
  },

  // Log4Shell (CVE-2021-44228): unauthenticated remote code execution in Apache Log4j 2, triggered by a
  // single attacker-controlled string (e.g. a request header) that log4j evaluates as a JNDI lookup. This
  // one has a clean remediation/mitigation split: the vulnerable code can be removed (remediation), and the
  // exploitation pattern can be blocked or detected in flight while that is pending (mitigation).
  {
    id: 'sample-rem-log4shell-patch',
    category: 'remediation',
    control_type: 'patch',
    scope: 'generic',
    title: 'Upgrade Apache Log4j 2 to 2.17.1 (or 2.12.4 / 2.3.2 on Java 7 / 6)',
    summary: 'CVE-2021-44228 affects log4j-core 2.0-beta9 through 2.14.1. The fixes released between 10 and 28 December 2021 removed message-lookup substitution and JNDI by default. Upgrading past 2.17.1 also closes the follow-on issues found during that same month (CVE-2021-45046, CVE-2021-45105, CVE-2021-44832).',
    platform: 'linux',
    author_rule: 'HUMAN',
    actions: [
      {
        title: 'Find every copy of the vulnerable library and upgrade it',
        steps: [
          'Find every log4j-core jar in use, including ones bundled inside application archives: find / -iname "log4j-core-*.jar" 2>/dev/null',
          'On Java 8 or later, replace it with log4j-core 2.17.1 or a later 2.x release. On Java 7, use 2.12.4. On Java 6, use 2.3.2.',
          'Rebuild or redeploy the application with the updated dependency, then confirm the version actually loaded: curl -s http://localhost:8080/actuator/env | grep -i log4j (or the equivalent for your application), or check the jar\'s MANIFEST.MF.',
        ],
        prerequisites: ['A build or deployment pipeline that can push a dependency bump.', 'A maintenance window if the application cannot reload the library live.'],
        change_risk: 'Medium: a very old log4j 1.x-style configuration file, or code that depends on removed lookup behavior, can need adjustment after the jump to 2.17.x.',
      },
    ],
    assumptions: ['The affected application is on the JVM (log4j-core is a Java library) and its full set of bundled jars can be inventoried.'],
    references: ['CVE-2021-44228: Apache Log4j2 JNDI features do not protect against attacker controlled LDAP and other JNDI related endpoints', 'Apache Log4j Security Vulnerabilities page'],
  },
  {
    id: 'sample-rem-log4shell-jndilookup',
    category: 'remediation',
    control_type: 'hardening',
    scope: 'generic',
    title: 'Remove the JndiLookup class from log4j-core while the upgrade is pending',
    summary: 'Apache\'s own advisory lists removing the JndiLookup class from the classpath as a workaround for log4j-core 2.10 and later: with the class gone, the vulnerable lookup can no longer run, in that build, regardless of configuration. It stays effective against the JNDI-based RCE even on the unpatched 2.x line, but it is a stand-in for the upgrade above, not a replacement for it (it does not address the later log4j CVEs).',
    platform: 'linux',
    author_rule: 'AI',
    verify: true,
    actions: [
      {
        title: 'Strip the vulnerable class from every copy of the jar',
        steps: [
          'For each log4j-core jar found (log4j-core-2.x.jar, 2.10 <= x <= 2.14.1): zip -q -d log4j-core-*.jar org/apache/logging/log4j/core/lookup/JndiLookup.class',
          'Restart the affected service so the running JVM reloads the modified jar.',
          'Confirm the class is gone: unzip -l log4j-core-*.jar | grep JndiLookup (expect no match).',
        ],
        prerequisites: ['zip/unzip available on the host or build pipeline.', 'A way to restart the affected service.'],
        change_risk: 'Low: only the JNDI lookup feature is removed. A configuration that deliberately uses a JNDI lookup (uncommon) stops working.',
      },
    ],
    assumptions: ['log4j-core is 2.10 or later (the class can be removed cleanly on these versions per the Apache advisory).'],
    references: ['Apache Log4j Security Vulnerabilities page: CVE-2021-44228 workarounds for releases >= 2.10'],
  },
  {
    id: 'sample-mit-splunk-log4shell-spl',
    category: 'mitigation',
    control_type: 'detection',
    scope: 'vendor_specific',
    collector_type: 'splunk',
    title: 'Detect the Log4Shell JNDI injection pattern in web traffic',
    summary: `The exploit string (for example a JNDI lookup opened with ${'$'}{jndi:ldap://...}) appears in any field log4j later formats into a log line: URL, request body, a header such as User-Agent or X-Forwarded-For. Searching raw web events for the pattern, including its common obfuscated variants, catches both successful and scanning-only attempts.`,
    platform: 'linux',
    author_rule: 'HUMAN',
    content: [
      '| Covers the plain pattern and the nested-lookup obfuscation used to evade naive filters',
      'index=web sourcetype=access_combined',
      `  _raw="*jndi:ldap*" OR _raw="*jndi:ldaps*" OR _raw="*jndi:rmi*" OR _raw="*jndi:dns*" OR _raw="*${'$'}{${'$'}{::-j}ndi*"`,
      '| table _time, clientip, uri_path, http_user_agent, _raw',
    ].join('\n'),
    coverage_notes: 'Covers the LDAP, LDAPS, RMI and DNS JNDI lookup forms and the double-brace obfuscation commonly used to evade a plain-string WAF rule. A heavily encoded or layered obfuscation can still slip past a raw-text search.',
    false_positive_notes: 'Security scanners (including your own vulnerability scanner) probing for the same weakness will also match. Exclude their known source addresses from the alert, not from the search.',
    assumptions: ['The web server or reverse proxy access log (with headers) is forwarded to Splunk.'],
  },
  {
    id: 'sample-mit-generic-log4shell-waf',
    category: 'mitigation',
    control_type: 'prevention',
    scope: 'generic',
    title: 'Block the JNDI lookup pattern at the reverse proxy or WAF',
    summary: 'A reverse-proxy rule that rejects requests carrying the JNDI lookup syntax stops the exploit string from ever reaching the vulnerable application. It is a network-layer compensating control: an attacker who finds another input path (not covered by the proxy) or a new obfuscation can still get through, which is why it does not replace the patch.',
    platform: 'linux',
    author_rule: 'AI',
    verify: true,
    content: [
      '# nginx, as a reverse proxy in front of the application; adapt the location block to your routes',
      'if ($http_user_agent ~* "\\$\\{jndi:") { return 403; }',
      'if ($request_uri ~* "\\$\\{jndi:") { return 403; }',
      'if ($http_x_forwarded_for ~* "\\$\\{jndi:") { return 403; }',
    ].join('\n'),
    coverage_notes: 'Blocks the common unobfuscated ${jndi: pattern in the three fields most exploit attempts used in December 2021 (URI, User-Agent, X-Forwarded-For). An obfuscated or nested-lookup variant, or an injection point outside these three fields, is not covered.',
    false_positive_notes: 'A legitimate request that happens to carry this exact string in one of these headers would be blocked; in practice this string has no legitimate use.',
  },
];

// "Applies to" and "Where to apply" of each sample item above.
export const ITEM_APPLICATION: Record<string, {
  applies_to: string;
  apply_layer: ApplyLayer;
}> = {
  'sample-rem-kerberoasting-service-accounts': {
    applies_to: 'Active Directory domain with Windows Server domain controllers',
    apply_layer: 'directory_domain',
  },
  'sample-rem-asrep-preauth': {
    applies_to: 'Active Directory user accounts',
    apply_layer: 'directory_domain',
  },
  'sample-rem-restrict-delegation': {
    applies_to: 'Active Directory computers and accounts trusted for delegation',
    apply_layer: 'directory_domain',
  },
  'sample-rem-kerberos-security-update': {
    applies_to: 'Windows Server domain controllers without the November 2021 update',
    apply_layer: 'host',
  },
  'sample-rem-machine-account-quota': {
    applies_to: 'Active Directory domain object',
    apply_layer: 'directory_domain',
  },
  'sample-rem-krbtgt-rotation': {
    applies_to: 'Active Directory domain (all domain controllers)',
    apply_layer: 'directory_domain',
  },
  'sample-rem-dcsync-permissions': {
    applies_to: 'Active Directory domain object (access control list)',
    apply_layer: 'directory_domain',
  },
  'sample-rem-lsa-protection': {
    applies_to: 'Windows 10 and 11 Enterprise or Education, Windows Server 2016 and later',
    apply_layer: 'host',
  },
  'sample-rem-laps': {
    applies_to: 'Windows 11 and Windows Server 2019 or later joined to the domain',
    apply_layer: 'directory_domain',
  },
  'sample-rem-disable-llmnr': {
    applies_to: 'Windows hosts of the domain (group policy and DHCP)',
    apply_layer: 'directory_domain',
  },
  'sample-rem-smb-signing': {
    applies_to: 'Windows SMB servers and clients (group policy)',
    apply_layer: 'directory_domain',
  },
  'sample-rem-smb-anonymous-access': {
    applies_to: 'Windows file servers and their shares',
    apply_layer: 'host',
  },
  'sample-rem-linux-hardcoded-secrets': {
    applies_to: 'Linux hosts and the services that run on them',
    apply_layer: 'host',
  },
  'sample-rem-linux-ssh-hardening': {
    applies_to: 'Linux hosts running OpenSSH 8.2 or later',
    apply_layer: 'host',
  },
  'sample-rem-linux-package-update': {
    applies_to: 'Linux hosts running OpenSSH 8.5p1 to 9.7p1 without a distribution fix',
    apply_layer: 'host',
  },
  'sample-rem-macos-os-update': {
    applies_to: 'macOS Ventura before 13.1, Monterey before 12.6.2, Big Sur before 11.7.2',
    apply_layer: 'host',
  },
  'sample-rem-macos-gatekeeper': {
    applies_to: 'macOS devices managed by a device management solution',
    apply_layer: 'host',
  },
  'sample-mit-splunk-kerberoasting-spl': {
    applies_to: 'Windows domain controller security logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-splunk-asrep-spl': {
    applies_to: 'Windows domain controller security logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-splunk-spn-change-spl': {
    applies_to: 'Windows domain controller security logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-splunk-anonymous-share-spl': {
    applies_to: 'Windows file server security logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-splunk-delegation-change-spl': {
    applies_to: 'Windows domain controller security logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-splunk-dcsync-spl': {
    applies_to: 'Windows domain controller security logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-splunk-samaccountname-spl': {
    applies_to: 'Windows domain controller security logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-splunk-ssh-bruteforce-spl': {
    applies_to: 'Linux authentication logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-crowdstrike-kerberoasting-ioa': {
    applies_to: 'Windows hosts with the CrowdStrike sensor',
    apply_layer: 'host',
  },
  'sample-mit-crowdstrike-kerberoasting-prevention': {
    applies_to: 'Windows hosts with the CrowdStrike sensor',
    apply_layer: 'host',
  },
  'sample-mit-crowdstrike-lsass-ioa': {
    applies_to: 'Windows hosts with the CrowdStrike sensor',
    apply_layer: 'host',
  },
  'sample-mit-crowdstrike-linux-reverse-shell': {
    applies_to: 'Linux hosts with the CrowdStrike sensor',
    apply_layer: 'host',
  },
  'sample-mit-crowdstrike-temp-exec-prevention': {
    applies_to: 'Windows hosts with the CrowdStrike sensor',
    apply_layer: 'host',
  },
  'sample-mit-generic-sysmon-lsass': {
    applies_to: 'Windows hosts with Sysmon installed',
    apply_layer: 'host',
  },
  'sample-mit-generic-asr-lsass': {
    applies_to: 'Windows hosts protected by Microsoft Defender Antivirus',
    apply_layer: 'host',
  },
  'sample-mit-generic-smb-scope': {
    applies_to: 'Windows file servers (Windows Defender Firewall)',
    apply_layer: 'network',
  },
  'sample-mit-generic-disable-spooler': {
    applies_to: 'Windows hosts and domain controllers that do not print',
    apply_layer: 'host',
  },
  'sample-mit-generic-sshd-logingracetime': {
    applies_to: 'Linux hosts running OpenSSH 8.5p1 to 9.7p1',
    apply_layer: 'host',
  },
  'sample-mit-generic-linux-egress': {
    applies_to: 'Linux hosts running the web service (nftables)',
    apply_layer: 'network',
  },
  'sample-mit-generic-linux-block-ssh-source': {
    applies_to: 'Linux hosts and the management network that reaches them (nftables)',
    apply_layer: 'network',
  },
  'sample-mit-generic-auditd-shadow': {
    applies_to: 'Linux hosts with the audit daemon running',
    apply_layer: 'host',
  },
  'sample-mit-generic-macos-remote-login': {
    applies_to: 'macOS hosts that do not need remote access',
    apply_layer: 'host',
  },
  'sample-rem-zerologon-patch': {
    applies_to: 'Windows Server domain controllers without the August 2020 update',
    apply_layer: 'host',
  },
  'sample-rem-zerologon-enforcement': {
    applies_to: 'Active Directory domain (Default Domain Controllers Policy)',
    apply_layer: 'directory_domain',
  },
  'sample-mit-splunk-zerologon-spl': {
    applies_to: 'Windows domain controller System logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-generic-zerologon-network': {
    applies_to: 'Windows Server domain controllers (Windows Defender Firewall)',
    apply_layer: 'network',
  },
  'sample-rem-log4shell-patch': {
    applies_to: 'Java applications bundling log4j-core 2.0-beta9 to 2.14.1',
    apply_layer: 'application',
  },
  'sample-rem-log4shell-jndilookup': {
    applies_to: 'Java applications bundling log4j-core 2.10 to 2.14.1',
    apply_layer: 'application',
  },
  'sample-mit-splunk-log4shell-spl': {
    applies_to: 'Web server or reverse-proxy access logs collected in Splunk',
    apply_layer: 'application',
  },
  'sample-mit-generic-log4shell-waf': {
    applies_to: 'The reverse proxy or load balancer in front of the vulnerable application',
    apply_layer: 'network',
  },
};
