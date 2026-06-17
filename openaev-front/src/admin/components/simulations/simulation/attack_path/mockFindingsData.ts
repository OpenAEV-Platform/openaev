/**
 * Mock findings data for the attack path POC demo.
 */

export interface MockFinding {
  id: string;
  name: string;
  value?: string; // displayed in "Value" column — masked for credentials
  severity: 'critical' | 'high' | 'medium' | 'low' | 'info';
  type: 'hostname' | 'username' | 'credential' | 'domain_name' | 'file' | 'port' | 'cve';
  description: string;
  mitre_technique: string;
  affected_asset: string;
  status: 'open' | 'mitigated' | 'accepted';
  detail?: string; // extra detail shown on expand
}

// ── Finance Department Credential Theft (5EP) ────────────────────────────────
const FINANCE_FINDINGS: MockFinding[] = [
  {
    id: 'f1-5ep', name: 'Domain Admin Credentials Captured', severity: 'critical', type: 'credential',
    value: 'CORP\\DA-admin : S●●●●●●3!',
    description: 'Mimikatz sekurlsa::logonpasswords extracted the DA plaintext password from LSASS on FINANCE-WS-01. Full domain compromise achieved.',
    mitre_technique: 'T1003.001', affected_asset: 'FINANCE-WS-01', status: 'open',
    detail: 'Account: CORP\\svc_backup | Hash: aad3b435b51404eeaad3b435b51404ee | Method: LSASS dump via procdump.exe',
  },
  {
    id: 'f2-5ep', name: 'Service Account Hash Cracked (Kerberoast)', severity: 'critical', type: 'credential',
    value: 'CORP\\svc_sqlreport : S●●●●r2023!',
    description: 'Service account svc_sqlreport had RC4 TGS ticket requested and cracked offline in 47 seconds using hashcat with rockyou.txt.',
    mitre_technique: 'T1558.003', affected_asset: 'DC-01', status: 'open',
    detail: 'Account: CORP\\svc_sqlreport | Hash type: $krb5tgs$23 | Cracked password: Summer2023!',
  },
  {
    id: 'f3-5ep', name: 'FINANCE-WS-01 Fully Compromised', severity: 'critical', type: 'hostname',
    value: 'FINANCE-WS-01 (10.10.1.11)',
    description: 'Initial access via phishing email. Attacker gained SYSTEM privileges via GPP password abuse and pivoted to domain controller.',
    mitre_technique: 'T1552.006', affected_asset: 'FINANCE-WS-01', status: 'open',
    detail: 'User: jsmith | Privilege escalation: GPP cPassword → Local Admin → Token impersonation',
  },
  {
    id: 'f4-5ep', name: 'FILE-SRV-01 Accessed via Lateral Movement', severity: 'high', type: 'hostname',
    value: 'FILE-SRV-01 (10.10.1.20)',
    description: 'Lateral movement from FINANCE-WS-01 to FILE-SRV-01 using pass-the-hash with captured NTLM hash. SMB signing was not enforced.',
    mitre_technique: 'T1021.002', affected_asset: 'FILE-SRV-01', status: 'open',
    detail: 'Method: PsExec via pass-the-hash | Source: FINANCE-WS-01 | Target share: \\\\FILE-SRV-01\\ADMIN$',
  },
  {
    id: 'f5-5ep', name: 'Financial Reports Exfiltrated', severity: 'high', type: 'file',
    value: '\\\\FILE-SRV-01\\Finance_Shared\\Q4_2025_Finance.xlsx',
    description: 'Sensitive financial spreadsheet exfiltrated from FILE-SRV-01 share. Contains salary data and Q4 projections.',
    mitre_technique: 'T1041', affected_asset: 'FILE-SRV-01', status: 'open',
    detail: 'File: \\\\FILE-SRV-01\\Finance_Shared\\Q4_2025_Finance.xlsx | Size: 2.4 MB | Exfil method: HTTP POST to 185.220.101.45',
  },
  {
    id: 'f6-5ep', name: 'Password Policy Document Stolen', severity: 'medium', type: 'file',
    value: '\\\\FILE-SRV-01\\IT_Docs\\Password_Policy_v3.docx',
    description: 'Internal IT password policy document copied from shared drive. Contains password complexity requirements and reset procedures.',
    mitre_technique: 'T1213', affected_asset: 'FILE-SRV-01', status: 'open',
    detail: 'File: \\\\FILE-SRV-01\\IT_Docs\\Password_Policy_v3.docx | Accessed by: CORP\\svc_sqlreport',
  },
  {
    id: 'f7-5ep', name: 'Local Admin Credentials Reused Across Workstations', severity: 'high', type: 'credential',
    value: '.\\administrator : W●●●●●●●●●r1',
    description: 'LAPS not deployed. The same local administrator password is used on all Finance workstations, enabling rapid horizontal spread.',
    mitre_technique: 'T1078.003', affected_asset: 'FINANCE-WS-01', status: 'open',
    detail: 'Local account: .\\administrator | Same password confirmed on: FINANCE-WS-01, FINANCE-WS-02, FINANCE-WS-03',
  },
  {
    id: 'f8-5ep', name: 'SMB Signing Disabled — NTLM Relay Risk', severity: 'high', type: 'hostname',
    value: 'FINANCE-WS-01, FINANCE-WS-02, FINANCE-WS-03',
    description: 'SMB signing is not enforced. Combined with LLMNR poisoning this enables NTLM relay to any SMB target in the Finance LAN.',
    mitre_technique: 'T1557.001', affected_asset: 'FINANCE-WS-01', status: 'open',
    detail: 'Affected hosts: FINANCE-WS-01, FINANCE-WS-02, FINANCE-WS-03 | Tested with: Responder + ntlmrelayx',
  },
];

// ── APT Mid-Enterprise Campaign (15EP) ───────────────────────────────────────
const APT_FINDINGS: MockFinding[] = [
  {
    id: 'f1-15ep', name: 'Web Application RCE — CVE-2023-44487', severity: 'critical', type: 'hostname',
    value: 'WEB-DMZ-01 (10.0.0.10)',
    description: 'HTTP/2 Rapid Reset Attack on public web server delivered a webshell. Used as primary initial access vector.',
    mitre_technique: 'T1190', affected_asset: 'WEB-DMZ-01', status: 'open',
    detail: 'CVE: 2023-44487 | Webshell: /var/www/html/.config.php | C2: 95.216.144.17:443',
  },
  {
    id: 'f2-15ep', name: 'Domain Admin Token Impersonated on WS-CORP-01', severity: 'critical', type: 'hostname',
    value: 'WS-CORP-01 (10.20.1.15)',
    description: 'Active DA session found on WS-CORP-01. Token impersonation via Incognito enabled privilege escalation without password.',
    mitre_technique: 'T1134.001', affected_asset: 'WS-CORP-01', status: 'open',
    detail: 'DA token: CORP\\domain.admin | Method: Incognito impersonate_token | Source process: explorer.exe',
  },
  {
    id: 'f3-15ep', name: 'DC-PRIMARY Fully Compromised — DCSync Executed', severity: 'critical', type: 'hostname',
    value: 'DC-PRIMARY (10.20.0.5)',
    description: 'DCSync executed against DC-PRIMARY using captured DA credentials. All domain account hashes extracted.',
    mitre_technique: 'T1003.006', affected_asset: 'DC-PRIMARY', status: 'open',
    detail: 'Method: mimikatz lsadump::dcsync /domain:corp.local /all | 847 accounts dumped',
  },
  {
    id: 'f4-15ep', name: 'All Domain Password Hashes Extracted', severity: 'critical', type: 'credential',
    value: 'CORP\\krbtgt : $NT$4b9a6●●●●●●●●',
    description: 'NTDS.dit extracted via DCSync. 847 user hashes captured including krbtgt, enabling Golden Ticket attacks.',
    mitre_technique: 'T1003.003', affected_asset: 'DC-PRIMARY', status: 'open',
    detail: 'Accounts captured: 847 | krbtgt hash: $NT$4b9a6b6e... | Method: DCSync via Mimikatz',
  },
  {
    id: 'f5-15ep', name: 'LSASS Cleartext Credentials (WDigest)', severity: 'critical', type: 'username',
    value: 'CORP\\j.parker : C●●●●●●●@25',
    description: 'WDigest authentication enabled on WS-CORP-01. Multiple domain user plaintext passwords recovered from LSASS.',
    mitre_technique: 'T1003.001', affected_asset: 'WS-CORP-01', status: 'open',
    detail: 'Accounts: CORP\\j.parker (CFO), CORP\\domain.admin | Method: sekurlsa::logonpasswords',
  },
  {
    id: 'f6-15ep', name: 'ProxyShell RCE on Exchange Server', severity: 'high', type: 'hostname',
    value: 'MAIL-SRV-01 (10.20.0.12)',
    description: 'CVE-2021-34473 exploited on Exchange. Pre-auth RCE granted mailbox access and lateral movement vector to Server VLAN.',
    mitre_technique: 'T1190', affected_asset: 'FILE-SRV-01', status: 'open',
    detail: 'CVE: 2021-34473/34523/31207 | Payload: PowerShell reverse shell | Target: https://mail.corp.local/owa',
  },
  {
    id: 'f7-15ep', name: 'M&A Strategy Document Exfiltrated', severity: 'high', type: 'file',
    value: 'Acquisition_Target_Confidential_2025.pdf',
    description: 'Confidential M&A due diligence document accessed from CFO mailbox and exfiltrated.',
    mitre_technique: 'T1114.001', affected_asset: 'FILE-SRV-01', status: 'open',
    detail: 'File: Acquisition_Target_Confidential_2025.pdf | Size: 8.1 MB | Accessed via: Exchange EWS API',
  },
  {
    id: 'f8-15ep', name: 'HR Employee Database Copied', severity: 'high', type: 'file',
    value: '\\\\FILE-SRV-01\\HR\\employees_export_2025.csv',
    description: 'Full HR database export (CSV) containing all employee PII copied from file server.',
    mitre_technique: 'T1213', affected_asset: 'FILE-SRV-01', status: 'open',
    detail: 'File: employees_export_2025.csv | Records: 1,240 | Contains: name, SSN, salary, address',
  },
  {
    id: 'f9-15ep', name: 'VPN Service Account Credentials', severity: 'high', type: 'username',
    value: 'CORP\\svc_vpn : V●●●●●●ss2024',
    description: 'VPN service account credentials found in plaintext in a scheduled task on WS-CORP-01.',
    mitre_technique: 'T1552.004', affected_asset: 'WS-CORP-01', status: 'open',
    detail: 'Account: CORP\\svc_vpn | Password: VPNaccess2024 | Location: C:\\Windows\\System32\\Tasks\\VPNKeepAlive',
  },
  {
    id: 'f10-15ep', name: 'No Segmentation Corp → Server VLAN', severity: 'medium', type: 'hostname',
    value: '10.20.1.0/24 → 10.20.0.0/24',
    description: 'SMB (445/tcp) and WMI (135/tcp) unrestricted between Corp LAN and Server VLAN. Enables unchecked lateral movement.',
    mitre_technique: 'T1021.002', affected_asset: 'FILE-SRV-01', status: 'open',
  },
];

// ── Large Enterprise Full Breach (50EP) ──────────────────────────────────────
const ENTERPRISE_FINDINGS: MockFinding[] = [
  {
    id: 'f1-50ep', name: 'Citrix ADC RCE — CVE-2023-3519', severity: 'critical', type: 'hostname',
    description: 'Unauthenticated RCE on Citrix NetScaler Gateway. Webshell delivered; DMZ fully compromised. Primary initial access.',
    mitre_technique: 'T1190', affected_asset: 'WEB-DMZ-01', status: 'open',
    detail: 'CVE: 2023-3519 | Webshell: /var/netscaler/logon/.s.php | CVSS: 9.8',
  },
  {
    id: 'f2-50ep', name: 'AD CS ESC1 — Arbitrary Certificate Enrollment', severity: 'critical', type: 'hostname',
    description: 'Any domain user can enroll a certificate with arbitrary SAN, allowing impersonation of any principal including Domain Admin.',
    mitre_technique: 'T1649', affected_asset: 'DC-01', status: 'open',
    detail: 'Template: SubCA | Flag: ENROLLEE_SUPPLIES_SUBJECT | Exploited with: Certipy',
  },
  {
    id: 'f3-50ep', name: 'PrintNightmare SYSTEM Escalation', severity: 'critical', type: 'hostname',
    description: 'CVE-2021-34527 exploited on DC-01 for SYSTEM-level code execution. Malicious driver installed via remote Print Spooler.',
    mitre_technique: 'T1068', affected_asset: 'DC-01', status: 'open',
    detail: 'CVE: 2021-34527 | DLL: C:\\Windows\\system32\\spool\\drivers\\x64\\3\\evil.dll | Result: SYSTEM shell',
  },
  {
    id: 'f4-50ep', name: 'OT Historian Accessible from IT Network', severity: 'critical', type: 'hostname',
    description: 'OT historian (OSIsoft PI) reachable from enterprise IT with no firewall or jump server. IT/OT bridge uncontrolled.',
    mitre_technique: 'T0817', affected_asset: 'HIST-01', status: 'open',
    detail: 'Port: 5450/tcp (PI Server) | Accessible from: 10.10.0.0/16 | Authentication: Default credentials',
  },
  {
    id: 'f5-50ep', name: 'Cloud Service Principal — Azure Owner Rights', severity: 'critical', type: 'hostname',
    description: 'On-prem sync service account has Azure Subscription Owner role. Pivot to cloud tenant enables full cloud resource takeover.',
    mitre_technique: 'T1078.004', affected_asset: 'CLOUD-BRIDGE-01', status: 'open',
    detail: 'Account: corp\\svc_adsync | Azure role: Owner on sub-abc123 | Method: Az CLI with stolen token',
  },
  {
    id: 'f6-50ep', name: 'Golden Ticket — krbtgt Hash Captured', severity: 'critical', type: 'credential',
    value: 'CORP\\krbtgt : $NT$7b9c3●●●●●●●●',
    description: 'krbtgt account hash extracted via DCSync. Enables forging Kerberos tickets valid for 10 years with any group membership.',
    mitre_technique: 'T1558.001', affected_asset: 'DC-01', status: 'open',
    detail: 'krbtgt hash: $NT$7b9c3... | Domain SID: S-1-5-21-3847... | Ticket validity: 10 years',
  },
  {
    id: 'f7-50ep', name: 'All 847 Domain Hashes Extracted', severity: 'critical', type: 'credential',
    value: 'CORP\\* (847 accounts)',
    description: 'Full NTDS.dit dump via DCSync. Every domain account hash including service accounts, admins, and privileged users.',
    mitre_technique: 'T1003.003', affected_asset: 'DC-01', status: 'open',
    detail: 'Total accounts: 847 | Admin accounts: 23 | Service accounts: 48 | Method: lsadump::dcsync /all',
  },
  {
    id: 'f8-50ep', name: 'Azure AD Sync Account Credentials', severity: 'critical', type: 'credential',
    value: 'MSOL_abc123 : M●●●●●●●●●!',
    description: 'Azure AD Connect sync account password extracted from local SQL database on CLOUD-BRIDGE-01 in plaintext.',
    mitre_technique: 'T1078.004', affected_asset: 'CLOUD-BRIDGE-01', status: 'open',
    detail: 'Account: MSOL_abc123 | Method: ADSyncDecrypt.ps1 | Access: Azure Global Admin equivalent',
  },
  {
    id: 'f9-50ep', name: 'OT Historian Default Credentials', severity: 'high', type: 'credential',
    value: 'piadmin : piadmin (default)',
    description: 'OSIsoft PI server accessed with default credentials (piadmin/piadmin). Grants read/write to all process sensor data.',
    mitre_technique: 'T1078', affected_asset: 'HIST-01', status: 'open',
    detail: 'Account: piadmin | Password: piadmin (default) | Access level: PI Server Administrator',
  },
  {
    id: 'f10-50ep', name: 'Engineering Schematics Exfiltrated', severity: 'critical', type: 'file',
    description: 'OT process schematics and PLC ladder logic programs copied from the HIST-01 archive. Critical IP theft.',
    mitre_technique: 'T1041', affected_asset: 'HIST-01', status: 'open',
    detail: 'Files: ProductionLine_A_PLC_v2.L5X, PlantSchematic_2025.pdf (total 47 files, 890 MB) | Exfil: SFTP to 45.142.212.18',
  },
  {
    id: 'f11-50ep', name: 'Azure Key Vault Secrets Exfiltrated', severity: 'critical', type: 'file',
    description: 'After Azure pivot, all secrets from Key Vault kv-prod-secrets accessed and exported including API keys and TLS certificates.',
    mitre_technique: 'T1552.001', affected_asset: 'CLOUD-BRIDGE-01', status: 'open',
    detail: 'Vault: kv-prod-secrets | Secrets: 34 | Contains: DB connection strings, payment API keys, TLS private keys',
  },
  {
    id: 'f12-50ep', name: 'M&A Documents and Board Meeting Minutes', severity: 'high', type: 'file',
    description: 'Confidential board-level documents including M&A targets, financial projections, and strategic plans exfiltrated from SharePoint.',
    mitre_technique: 'T1213.002', affected_asset: 'DC-01', status: 'open',
    detail: 'SharePoint site: /sites/BoardDocuments | Files: 12 documents | Includes: Project Phoenix deal terms',
  },
  {
    id: 'f13-50ep', name: 'Customer PII Database Dump', severity: 'high', type: 'file',
    description: '2.3M customer records copied from the CRM database on the application server. GDPR/CCPA breach.',
    mitre_technique: 'T1005', affected_asset: 'WEB-DMZ-01', status: 'open',
    detail: 'Database: crm_prod | Table: customers | Records: 2,341,887 | Format: CSV dump 1.8 GB',
  },
  {
    id: 'f14-50ep', name: 'No EDR on OT Segment', severity: 'high', type: 'hostname',
    description: 'OT systems run legacy Windows XP/7 with no endpoint detection. Attacker activity only detected via network anomalies.',
    mitre_technique: 'T0800', affected_asset: 'HIST-01', status: 'open',
  },
  {
    id: 'f15-50ep', name: 'KRBTGT Password Not Rotated (>12 months)', severity: 'high', type: 'hostname',
    description: 'krbtgt password unchanged for 14 months. Golden Tickets remain valid after most remediation steps.',
    mitre_technique: 'T1558.001', affected_asset: 'DC-01', status: 'open',
    detail: 'Last change: 2024-03-10 | Days since rotation: 438 | Risk: Golden tickets survive standard cleanup',
  },
];

// ── APT29 Domain Takeover (8EP) ───────────────────────────────────────────────
const APT_DOMAIN_FINDINGS: MockFinding[] = [
  {
    id: 'f1-apt29', name: 'Domain Admin Credentials Captured via DCSync', severity: 'critical', type: 'credential',
    value: 'CORP\\da.svcadmin : D●●●●●●●●●●●●●2024!',
    description: 'Plaintext domain admin password extracted from LSASS on IT-ADMIN-WS-01. Used to perform full DCSync against CORP-DC-01, extracting all 284 account hashes.',
    mitre_technique: 'T1003.006', affected_asset: 'CORP-DC-01', status: 'open',
    detail: 'Account: CORP\\da.svcadmin | Method: comsvcs.dll MiniDump → Mimikatz sekurlsa | DCSync: 284 accounts extracted incl. krbtgt',
  },
  {
    id: 'f2-apt29', name: 'krbtgt Hash Extracted — Golden Ticket Possible', severity: 'critical', type: 'credential',
    value: 'CORP\\krbtgt : $NT$8f3a●●●●●●●●2c',
    description: 'krbtgt NTLM hash captured via DCSync. An attacker with this hash can forge Kerberos TGTs valid for any account with any group membership for up to 10 years.',
    mitre_technique: 'T1558.001', affected_asset: 'CORP-DC-01', status: 'open',
    detail: 'Hash: $NT$8f3a...2c (RC4-HMAC) | Domain SID: S-1-5-21-2847193020-... | Risk: Persistent AD access even after password resets',
  },
  {
    id: 'f3-apt29', name: 'Tomcat Service Account Credentials Exposed', severity: 'critical', type: 'credential',
    value: 'svc_tomcat : T●●●●●●●●●●2024',
    description: 'Tomcat Manager application had weak credentials exposed via CVE-2020-1938 (Ghostcat AJP). Used as pivot point for internal network access.',
    mitre_technique: 'T1078.003', affected_asset: 'WEB-APP-01', status: 'open',
    detail: 'Service: Tomcat 9.0.34 | CVE: CVE-2020-1938 | Exploit: AJP file inclusion → Manager console → war deployment',
  },
  {
    id: 'f4-apt29', name: 'IT Admin Workstation Fully Compromised', severity: 'critical', type: 'hostname',
    value: 'IT-ADMIN-WS-01 (10.10.1.60)',
    description: 'IT Administrator workstation breached via pass-the-hash using NTLM hash captured from DEV-WS-01 LSASS dump. Domain admin credentials found in memory.',
    mitre_technique: 'T1550.002', affected_asset: 'IT-ADMIN-WS-01', status: 'open',
    detail: 'Attack vector: Pass-the-hash from DEV-WS-01 | User found: CORP\\da.svcadmin | Privilege: Domain Admin',
  },
  {
    id: 'f5-apt29', name: 'WEB-APP-01 Remote Code Execution', severity: 'critical', type: 'hostname',
    value: 'WEB-APP-01 (172.16.0.10)',
    description: 'External web application server exploited via Ghostcat (CVE-2020-1938) followed by Tomcat Manager WAR deployment. Root access obtained.',
    mitre_technique: 'T1190', affected_asset: 'WEB-APP-01', status: 'open',
    detail: 'CVE: CVE-2020-1938 (CVSS 9.8) | Initial user: www-data | Privilege escalation: CVE-2021-3560 (polkit) → root',
  },
  {
    id: 'f6-apt29', name: 'LSASS Memory Dumped — NTLM Hashes Exposed', severity: 'high', type: 'credential',
    value: 'CORP\\dev.jthompson : $NT$a1b2●●●●●●3d',
    description: 'LSASS memory dump via comsvcs.dll triggered Defender ATP alert but partial hashes extracted before remediation. NTLM hash for dev.jthompson captured.',
    mitre_technique: 'T1003.001', affected_asset: 'DEV-WS-01', status: 'open',
    detail: 'Tool: comsvcs.dll MiniDump via WMI | Detection: Defender ATP fired after 8 seconds | Hashes captured before shutdown: 3',
  },
  {
    id: 'f7-apt29', name: 'PrintNightmare Exploited on Print Server', severity: 'high', type: 'hostname',
    value: 'PRINT-SRV-02 (10.10.2.20)',
    description: 'Windows Print Spooler vulnerability (CVE-2021-1675) exploited to install malicious printer driver and obtain SYSTEM-level code execution.',
    mitre_technique: 'T1068', affected_asset: 'PRINT-SRV-02', status: 'open',
    detail: 'CVE: CVE-2021-1675 / CVE-2021-34527 (PrintNightmare) | Method: DLL injection via AddPrinterDriverEx | Result: NT AUTHORITY\\SYSTEM',
  },
  {
    id: 'f8-apt29', name: 'DEV-WS-01 Compromised via Credential Spray', severity: 'high', type: 'hostname',
    value: 'DEV-WS-01 (10.10.1.50)',
    description: 'Developer workstation breached using Tomcat service account credentials sprayed over SMB. NTLM hash cache recovered for lateral movement.',
    mitre_technique: 'T1021.002', affected_asset: 'DEV-WS-01', status: 'open',
    detail: 'Credential: svc_tomcat:T0mcat@dmin2024 | SMB access: ADMIN$ share | Hashes: 3 active user sessions cached',
  },
  {
    id: 'f9-apt29', name: 'Web Application Configuration File Exposed', severity: 'high', type: 'file',
    value: '/opt/tomcat/webapps/ROOT/WEB-INF/web.xml',
    description: 'Tomcat application configuration file containing database credentials and service account passwords readable by www-data user via directory traversal.',
    mitre_technique: 'T1552.001', affected_asset: 'WEB-APP-01', status: 'open',
    detail: 'File: /opt/tomcat/conf/context.xml | Contains: DB password, LDAP bind credentials | Readable by: www-data (CVE-2020-1938)',
  },
  {
    id: 'f10-apt29', name: 'SALES-WS-01 Credential Spray Detected', severity: 'medium', type: 'hostname',
    value: 'SALES-WS-01 (10.10.1.55)',
    description: 'Credential spray attempt against SALES-WS-01 detected by SIEM (rule ALP-2025-0322-0047). Partial access gained before alert triggered endpoint isolation.',
    mitre_technique: 'T1110.003', affected_asset: 'SALES-WS-01', status: 'mitigated',
    detail: 'Alert: ALP-2025-0322-0047 | Detected by: SIEM + EDR correlation | Action taken: Endpoint isolated within 94 seconds',
  },
  {
    id: 'f11-apt29', name: 'MSSQL Port 1433 Exposed Internally', severity: 'medium', type: 'port',
    value: 'MSSQL-SRV-01:1433/tcp',
    description: 'SQL Server port 1433 open to all Corp VLAN hosts. Brute-force attack was prevented by firewall rule FW-RULE-104 but the exposure remains a risk.',
    mitre_technique: 'T1046', affected_asset: 'MSSQL-SRV-01', status: 'mitigated',
    detail: 'Port: 1433/tcp | Firewall rule: FW-RULE-104 (Corp LAN → Server VLAN MSSQL) | Recommendation: Add IP allowlist',
  },
  {
    id: 'f12-apt29', name: 'CVE-2020-1938 Ghostcat AJP Vulnerable', severity: 'critical', type: 'cve',
    value: 'CVE-2020-1938 (CVSS 9.8)',
    description: 'Apache Tomcat AJP connector enabled and exposed on port 8009. Allows arbitrary file read and remote code execution without authentication.',
    mitre_technique: 'T1190', affected_asset: 'WEB-APP-01', status: 'open',
    detail: 'CVE: CVE-2020-1938 | Affected: Tomcat 9.0.34 | Fix: Upgrade to 9.0.35+ or disable AJP connector | CVSS: 9.8 Critical',
  },
  {
    id: 'f13-apt29', name: 'Finance Report Data Exfiltrated from IT Admin', severity: 'high', type: 'file',
    value: 'C:\\Users\\da.svcadmin\\Documents\\network_topology_2025.vsd',
    description: 'Network topology diagram and access privilege documentation found on IT Admin workstation, copied to attacker-controlled file share.',
    mitre_technique: 'T1005', affected_asset: 'IT-ADMIN-WS-01', status: 'open',
    detail: 'Files copied: network_topology_2025.vsd, privileged_accounts_audit.xlsx, firewall_rules_export.csv | Total: 3 files, 14 MB',
  },
];

/** Keyed by exercise ID (simulation run) */
export const MOCK_FINDINGS: Record<string, MockFinding[]> = {
  'e65260ad-4685-4489-8f0d-8b316db695c9': FINANCE_FINDINGS,
  'a1ee5706-207c-48d0-a916-c2571c8f5180': FINANCE_FINDINGS.slice(0, 6),
  '01f18e12-022d-4f10-ae3f-9bbc566ebd9d': FINANCE_FINDINGS.slice(0, 4),
  'f4e195cd-920f-4882-89f3-9b56aa63329b': APT_FINDINGS,
  '2cfb41be-a08b-49cc-82f7-669c83aebaf4': APT_FINDINGS.slice(0, 7),
  '82f141cf-69b2-4af6-8d8a-4686afbe6451': APT_FINDINGS.slice(0, 5),
  '2a4648ff-14e7-422e-bf0f-d533368bdaf5': ENTERPRISE_FINDINGS,
  'a9b3c7d1-0000-0000-0000-000000000001': APT_DOMAIN_FINDINGS,
  'b8e4f2a6-0000-0000-0000-000000000002': APT_DOMAIN_FINDINGS.slice(0, 5),
};

/** Keyed by scenario ID — all findings aggregated across runs */
export const MOCK_SCENARIO_FINDINGS: Record<string, MockFinding[]> = {
  'f4bb8b8f-10ad-459b-b629-89dc282a7431': FINANCE_FINDINGS,
  '60101396-fb29-4eff-8d0c-1081986c8f5b': APT_FINDINGS,
  'b0e28e75-9e66-426a-8a32-80ee097dfdd1': ENTERPRISE_FINDINGS,
  'd7f3a2b1-8c4e-4f9a-b2d1-3a5f8e7c6b0a': APT_DOMAIN_FINDINGS,
};


