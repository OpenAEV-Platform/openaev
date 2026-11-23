import { type MitigationFinding, type MockMitigation } from './MitigationCard';

// PROTOTYPE - MOCK DATA ONLY. Canned mitigation suggestions used by the prototype screens.
export const MOCK_MITIGATIONS: Record<'windows' | 'linux' | 'network', MockMitigation> = {
  windows: {
    criticality: 'critical',
    addresses: [{ type: 'credentials' }],
    summary: 'LSASS memory can be read from this Windows 10 22H2 host while running as SYSTEM. Block and detect credential dumping. The weakness stays until LSA protection is turned on (see Remediation).',
    steps: [
      {
        text: 'Turn on the Attack Surface Reduction rule "Block credential stealing from the Windows local security authority subsystem (lsass.exe)". Audit mode first, then Enabled.',
        command: 'Add-MpPreference -AttackSurfaceReductionRules_Ids 9e6c4e1f-7d60-472f-ba1a-a39ef669e4b2 -AttackSurfaceReductionRules_Actions Enabled',
      },
      { text: 'Alert on the block events 1121 (and on the audit events 1122) of the Microsoft-Windows-Windows Defender/Operational log in your SIEM.' },
      { text: 'Log every process that opens LSASS with Sysmon event 10, so attempts that are not blocked are still seen.' },
    ],
    risk: 'medium',
    impact: 'Security, backup and monitoring tools that read LSASS memory can be blocked. Review the audit events before enforcing.',
    rollback: 'Remove the rule: Remove-MpPreference -AttackSurfaceReductionRules_Ids 9e6c4e1f-7d60-472f-ba1a-a39ef669e4b2',
    limitedContext: false,
  },
  linux: {
    criticality: 'high',
    addresses: [{ type: 'credentials' }],
    summary: 'The command ran as www-data and reached sensitive files. Confine the web service account and record what it reads. The exposed files themselves stay readable (see Remediation).',
    steps: [
      {
        text: 'Confine the service with an AppArmor profile in enforce mode.',
        command: 'sudo aa-enforce /etc/apparmor.d/usr.sbin.nginx',
      },
      {
        text: 'Record every read of the credential file, to alert on access by anything but the service.',
        command: 'sudo auditctl -w /etc/app/credentials.conf -p r -k app_credentials',
      },
      {
        text: 'Mount /tmp with noexec so dropped scripts and binaries cannot run.',
        command: 'mount -o remount,noexec,nosuid /tmp',
      },
    ],
    risk: 'low',
    impact: 'Applications that execute helper scripts from /tmp will fail until they use another directory. An AppArmor profile that is too strict blocks legitimate file access.',
    rollback: 'Switch the AppArmor profile back to complain mode (aa-complain), remount /tmp without noexec and delete the audit rule (auditctl -W /etc/app/credentials.conf -p r -k app_credentials).',
    limitedContext: false,
  },
  network: {
    criticality: 'low',
    addresses: [{
      type: 'portscan',
      value: '445/tcp',
    }],
    summary: 'The scan reached an exposed service. Without agent details this is a generic recommendation: reduce what the network exposes.',
    steps: [
      { text: 'Restrict the exposed service to the management VLAN with a firewall rule.' },
      { text: 'Close services that do not need to be reachable and re-scan to confirm.' },
    ],
    risk: 'low',
    impact: 'Clients outside the allowed network lose access to the service.',
    rollback: 'Remove the firewall rule and re-enable the previous service configuration.',
    limitedContext: true,
  },
};

// What the suggestion adds for each finding type the action produced (finding TYPES only: the
// finding values are masked and never leave the platform). A mitigation is a compensating control, so each
// finding type has a prevention (block or limit the attack) and a detection (see it and alert).
interface MitigationPart {
  summary: string;
  criticality: MockMitigation['criticality'];
  steps: MockMitigation['steps'];
  impact: string;
  rollback: string;
  risk: MockMitigation['risk'];
}

const FINDING_TYPE_MITIGATIONS: Record<string, Record<'prevention' | 'detection', MitigationPart>> = {
  portscan: {
    prevention: {
      summary: 'The scan found services reachable from the attacker position. Limit who can reach them. The services stay exposed (see Remediation).',
      criticality: 'low',
      // Replaced by one step per reported port in buildMitigations (target OS unknown: no command).
      steps: [
        { text: 'Restrict the exposed ports to the management network at the host and network firewall.' },
      ],
      impact: 'Clients outside the allowed network lose access to the service.',
      rollback: 'Remove the firewall rule.',
      risk: 'low',
    },
    detection: {
      summary: 'Make a scan of these ports visible: a source that probes many ports is the first sign of an attack.',
      criticality: 'low',
      steps: [
        { text: 'Log inbound connections to the exposed ports on the host and network firewall.' },
        { text: 'Alert when one source address reaches many ports (or many hosts) in a few minutes.' },
      ],
      impact: 'Adds firewall log volume. Vulnerability scanners will alert unless they are allow-listed.',
      rollback: 'Disable the alert rule.',
      risk: 'low',
    },
  },
  share: {
    prevention: {
      summary: 'Windows administrative shares are reachable from the attacker position. Limit who can reach SMB. The share configuration itself is not fixed (see Remediation).',
      criticality: 'high',
      steps: [
        {
          text: 'Only accept inbound SMB from the networks that need the file service.',
          command: 'Set-NetFirewallRule -DisplayName "File and Printer Sharing (SMB-In)" -RemoteAddress 10.10.0.0/24',
        },
      ],
      impact: 'Clients outside the allowed ranges lose access to the shares. Include every subnet that uses the server.',
      rollback: 'Set the remote address of the rule back to Any: Set-NetFirewallRule -DisplayName "File and Printer Sharing (SMB-In)" -RemoteAddress Any',
      risk: 'medium',
    },
    detection: {
      summary: 'Record every share access and alert when the anonymous logon opens a share.',
      criticality: 'high',
      steps: [
        {
          text: 'Audit share access on the file servers.',
          command: 'auditpol /set /subcategory:"File Share" /success:enable /failure:enable',
        },
        { text: 'Alert on events 5140 where the account is ANONYMOUS LOGON, and on events 5145 for the files that were read.' },
      ],
      impact: 'Adds Security log volume on busy file servers.',
      rollback: 'Disable the audit subcategory: auditpol /set /subcategory:"File Share" /success:disable /failure:disable',
      risk: 'low',
    },
  },
  credentials: {
    prevention: {
      summary: 'Credentials were captured by the action. Block untrusted processes from reading them. The credentials themselves must be rotated (see Remediation).',
      criticality: 'critical',
      steps: [
        { text: 'Enable the host rule of your EDR or operating system that blocks untrusted processes from reading credentials from memory.' },
      ],
      impact: 'Security, backup and monitoring tools that read credential stores can be blocked until they are excluded.',
      rollback: 'Switch the blocking rule back to audit or detect mode.',
      risk: 'medium',
    },
    detection: {
      summary: 'Credentials were captured by the action. Make every attempt to read them visible.',
      criticality: 'critical',
      steps: [
        { text: 'Alert on every process that opens the credential store (LSASS on Windows, the shadow file on Linux) in your SIEM or EDR.' },
        { text: 'Alert on the block and audit events of the endpoint protection that guards the credential store.' },
      ],
      impact: 'Security agents open the credential store legitimately: exclude them to avoid noise.',
      rollback: 'Disable the alert rule.',
      risk: 'low',
    },
  },
  zerologon: {
    prevention: {
      summary: 'CVE-2020-1472 (Zerologon) lets an unauthenticated attacker on the network reset the domain controller machine account password. Limiting who can reach its RPC endpoint mapper removes the network path while the patch is pending (see Remediation).',
      criticality: 'critical',
      steps: [
        { text: 'Only accept inbound RPC (TCP 135 and the dynamic high port) and SMB (445) to the domain controller from the management and server subnets, with a firewall rule.' },
      ],
      impact: 'Servers and applications that reach the domain controller over RPC from outside the allowed range lose that access.',
      rollback: 'Remove the firewall rule.',
      risk: 'medium',
    },
    detection: {
      summary: 'Microsoft added dedicated Netlogon event IDs for this CVE. Event 5829 is a vulnerable connection that was still allowed; 5827 and 5828 are ones a patched domain controller already denied.',
      criticality: 'critical',
      steps: [
        { text: 'Forward the System log of every domain controller to your SIEM and alert on Netlogon events 5827, 5828 and 5829.' },
      ],
      impact: 'A non-Windows device still configured as an exception will also trigger 5830/5831. Review and remove the exception instead of ignoring the alert.',
      rollback: 'Disable the alert rule.',
      risk: 'low',
    },
  },
  log4shell: {
    prevention: {
      summary: 'CVE-2021-44228 (Log4Shell) is triggered by an attacker-controlled string, for example a request header, that log4j evaluates as a JNDI lookup. A reverse-proxy rule that rejects the lookup syntax keeps the exploit string from reaching the application while the patch is pending (see Remediation).',
      criticality: 'critical',
      steps: [
        { text: 'Block the JNDI lookup pattern at the reverse proxy or WAF, in the URI, User-Agent and X-Forwarded-For headers: reject any request containing the dollar-brace jndi: syntax.' },
      ],
      impact: 'A legitimate request would never carry this exact string; in practice this control has no real false positives, but an obfuscated or nested variant can still slip past it.',
      rollback: 'Remove the proxy rule.',
      risk: 'low',
    },
    detection: {
      summary: 'The exploit string appears in any field log4j later formats into a log line: URL, request body, or a header. Searching raw web events for the pattern, including its common obfuscated variants, catches both successful and scanning-only attempts.',
      criticality: 'critical',
      steps: [
        { text: 'Search the web server or reverse-proxy access log for the JNDI lookup pattern (ldap, ldaps, rmi and dns forms, plus the nested-lookup obfuscation) and alert on a match.' },
      ],
      impact: 'Your own vulnerability scanner probing for the same weakness will also match; exclude its source address from the alert, not from the search.',
      rollback: 'Disable the alert rule.',
      risk: 'low',
    },
  },
};

const PORT_SERVICES: Record<string, string> = {
  445: 'SMB',
  3389: 'RDP',
  22: 'SSH',
  21: 'FTP',
};

const GENERIC_MITIGATIONS: Record<'prevention' | 'detection', Pick<MockMitigation, 'summary' | 'steps' | 'impact' | 'rollback'>> = {
  prevention: {
    summary: 'No typed finding was produced, so this is a generic suggestion: block the technique this action uses.',
    steps: [{ text: 'Enable the prevention rule of your EDR for the technique this action uses, in block mode, then re-run the action to validate.' }],
    impact: 'A legitimate tool that uses the same technique can be blocked. Start in monitor mode.',
    rollback: 'Switch the rule back to monitor mode.',
  },
  detection: {
    summary: 'No typed finding was produced, so this is a generic suggestion: make the technique this action uses visible.',
    steps: [{ text: 'Create a detection rule in your SIEM or EDR for the command line of this action, then re-run the action to confirm it alerts.' }],
    impact: 'Administration scripts that use the same command can alert. Tune the rule.',
    rollback: 'Disable the detection rule.',
  },
};

// Finding types whose values are sensitive: only the type is known outside the platform.
export const SENSITIVE_TYPES = ['credentials', 'password', 'hash', 'secret', 'token'];

// The mock content is written per scenario. A finding type of the product (cve) is refined by its value:
// CVE-2020-1472 is the Zerologon content, CVE-2021-44228 the Log4Shell one.
const CVE_SCENARIOS: Record<string, string> = {
  'cve-2020-1472': 'zerologon',
  'cve-2021-44228': 'log4shell',
};
export const mockKeyOf = (finding: MitigationFinding) => {
  const type = finding.type.toLowerCase();
  return type === 'cve' ? (CVE_SCENARIOS[(finding.value ?? '').toLowerCase()] ?? type) : type;
};

/**
 * Mock of what XTM One would answer: a LIST of mitigations, one per kind of finding the execution
 * produced, each tied to the findings it addresses and carrying its own criticality (returned by
 * XTM One, displayed as is). The request carries the command, the finding types (and the values of
 * the non-sensitive ones) and the environment when there is an agent.
 */
export const buildMitigations = (input: {
  actionName: string;
  command?: string;
  findings: MitigationFinding[];
  hasAgent: boolean;
  platform?: string;
}): MockMitigation[] => {
  const byKey = new Map<string, MitigationFinding[]>();
  input.findings.forEach((finding) => {
    const key = Object.keys(FINDING_TYPE_MITIGATIONS).find(k => mockKeyOf(finding).includes(k));
    if (key) byKey.set(key, [...(byKey.get(key) ?? []), finding]);
  });
  const kinds = ['prevention', 'detection'] as const;
  if (byKey.size === 0) {
    return kinds.map(kind => ({
      ...GENERIC_MITIGATIONS[kind],
      criticality: 'medium' as const,
      addresses: [],
      risk: 'low' as const,
      limitedContext: !input.hasAgent,
      controlType: kind,
    }));
  }
  return [...byKey.entries()].flatMap(([key, findings]) => {
    const seen = new Set<string>();
    const addresses = findings
      .map(finding => ({
        type: finding.type,
        value: SENSITIVE_TYPES.some(sensitive => finding.type.toLowerCase().includes(sensitive)) ? undefined : finding.value,
      }))
      .filter((finding) => {
        const id = `${finding.type}|${finding.value ?? ''}`;
        if (seen.has(id)) return false;
        seen.add(id);
        return true;
      });
    return kinds.map((kind) => {
      const part = FINDING_TYPE_MITIGATIONS[key][kind];
      let steps = [...part.steps];
      if (key === 'portscan' && kind === 'prevention') {
        const portSteps = findings
          .filter(finding => finding.value)
          .map(finding => ({ text: `Restrict ${finding.value} (${PORT_SERVICES[finding.value!.split('/')[0]] ?? 'service'}) to the management network, or close it if nothing needs it.` }));
        if (portSteps.length > 0) steps = portSteps;
      }
      if (input.hasAgent && key === 'credentials' && kind === 'prevention' && (input.platform ?? 'windows').toLowerCase().includes('windows')) steps.push(MOCK_MITIGATIONS.windows.steps[0]);
      return {
        criticality: part.criticality,
        addresses,
        summary: part.summary,
        steps,
        risk: part.risk,
        impact: part.impact,
        rollback: part.rollback,
        limitedContext: !input.hasAgent,
        controlType: kind,
      };
    });
  });
};

/** First mitigation of the list, for screens that show a single one (report prototype). */
export const buildMitigation = (input: {
  actionName: string;
  findingTypes: string[];
  hasAgent: boolean;
}): MockMitigation => buildMitigations({
  actionName: input.actionName,
  findings: input.findingTypes.map(type => ({ type })),
  hasAgent: input.hasAgent,
})[0];

// What a REMEDIATION adds for each finding type: it removes the root cause (configuration, credential or
// exposure) instead of detecting or blocking the attack. Same shape as a mitigation, for the shared cards.
const FINDING_TYPE_REMEDIATIONS: Record<string, {
  summary: string;
  criticality: MockMitigation['criticality'];
  steps: MockMitigation['steps'];
  impact: string;
  rollback: string;
  risk: MockMitigation['risk'];
}> = {
  portscan: {
    summary: 'The scan found services that do not need to be exposed. Remove the exposure at its source.',
    criticality: 'medium',
    steps: [
      { text: 'Uninstall or disable every service that is not needed on the host, then re-scan to confirm the ports are closed.' },
      { text: 'Keep the services that are needed behind the management network only, and document the owner of each one.' },
    ],
    impact: 'Clients that relied on a removed service lose access to it.',
    rollback: 'Reinstall the service and restore its previous configuration.',
    risk: 'low',
  },
  share: {
    summary: 'Network shares accept access that they should not. Fix the share configuration and the protocol settings.',
    criticality: 'high',
    steps: [
      {
        text: 'Remove anonymous and "Everyone" access from every share and grant access to named groups only.',
        command: 'Revoke-SmbShareAccess -Name <share> -AccountName Everyone -Force',
      },
      {
        text: 'Require SMB signing on servers and clients, and turn off null session access.',
        command: 'Set-SmbServerConfiguration -RequireSecuritySignature $true -RestrictNullSessAccess $true -Force',
      },
      {
        text: 'Disable the automatic administrative shares on hosts that do not need them.',
        command: 'Set-SmbServerConfiguration -AutoShareServer $false -Force',
      },
    ],
    impact: 'Scripts and tools that read shares anonymously stop working. Test before production.',
    rollback: 'Restore the previous share permissions and security policy.',
    risk: 'medium',
  },
  credentials: {
    summary: 'The action could read credentials. Make the exposed ones useless and remove the way they were read.',
    criticality: 'critical',
    steps: [
      { text: 'Rotate the exposed credentials now and reset the password of the affected accounts. A stolen hash is valid until the password changes.' },
      {
        text: 'Run LSASS as a protected process so only signed code can open it (restart required).',
        command: 'reg add HKLM\\SYSTEM\\CurrentControlSet\\Control\\Lsa /v RunAsPPL /t REG_DWORD /d 1 /f',
      },
      { text: 'Enable Credential Guard (group policy "Turn On Virtualization Based Security", Credential Guard "Enabled with UEFI lock") so secrets are not in LSASS memory.' },
      {
        text: 'Give every computer a unique local administrator password with Windows LAPS.',
        command: 'Update-LapsADSchema',
      },
    ],
    impact: 'Users must reset their password. Applications that use the old credentials fail until updated. Software that needs NTLMv1 or unconstrained delegation stops working with Credential Guard.',
    rollback: 'A credential rotation cannot be rolled back. Set RunAsPPL back to 0 and disable the Credential Guard policy only if an application breaks.',
    risk: 'high',
  },
  zerologon: {
    summary: 'Before the February 2021 enforcement date, a non-Windows device could be listed as an exception to Netlogon secure RPC and keep using the vulnerable connection. Remove any such exception: it is a bypass of the fix, not a fix itself. The vulnerability itself is only removed by the security update (see the patch below).',
    criticality: 'high',
    steps: [
      { text: 'Open "Domain controller: Allow vulnerable Netlogon secure channel connections" in the Default Domain Controllers Policy and confirm every listed device has since been updated.' },
      { text: 'Set the policy back to Not Defined (or an empty list) once nothing still needs it.' },
    ],
    impact: 'A device still relying on the exception loses its domain connection until it is updated or replaced.',
    rollback: 'Re-add the specific device to the exception list as a temporary measure.',
    risk: 'medium',
  },
  log4shell: {
    summary: 'Apache\'s own advisory lists removing the JndiLookup class from the classpath as a workaround for log4j-core 2.10 and later: with the class gone, the vulnerable lookup cannot run, whatever the configuration. It stays in place until every copy of the library is upgraded (see the patch below), which it does not replace.',
    criticality: 'critical',
    steps: [
      {
        text: 'Remove the vulnerable class from every log4j-core jar found on the host.',
        command: 'zip -q -d log4j-core-*.jar org/apache/logging/log4j/core/lookup/JndiLookup.class',
      },
      { text: 'Restart the affected service so the running JVM reloads the modified jar.' },
    ],
    impact: 'Only the JNDI lookup feature is removed. A configuration that deliberately uses a JNDI lookup (uncommon) stops working.',
    rollback: 'Redeploy the original, unmodified jar.',
    risk: 'low',
  },
};

// Patches that match what the scan found: a patch is only proposed for a service whose known vulnerabilities
// have a vendor fix. A finding with no such service (credentials, for example) gets no patch.
const SERVICE_PATCHES: Record<'smb' | 'rdp' | 'ssh', Omit<MockMitigation, 'addresses' | 'limitedContext' | 'controlType'>> = {
  smb: {
    criticality: 'critical',
    summary: 'An SMB server that misses the March 2017 security update is exposed to EternalBlue (CVE-2017-0144, MS17-010), which gives remote code execution without credentials.',
    steps: [
      { text: 'Install the March 2017 security update (MS17-010, CVE-2017-0143 to CVE-2017-0148) or any later cumulative update, then restart.' },
      {
        text: 'Remove SMBv1 where nothing needs it.',
        command: 'Disable-WindowsOptionalFeature -Online -FeatureName SMB1Protocol',
      },
    ],
    risk: 'medium',
    impact: 'A restart is required. Old devices that only speak SMBv1 lose access to the server.',
    rollback: 'Re-enable the SMB1Protocol feature (not recommended) and uninstall the update with wusa /uninstall.',
  },
  rdp: {
    criticality: 'critical',
    summary: 'Remote Desktop Services that miss the 2019 updates are exposed to BlueKeep (CVE-2019-0708, fixed in May 2019) and DejaBlue (CVE-2019-1181 and CVE-2019-1182, fixed in August 2019).',
    steps: [
      { text: 'Install the May 2019 security update (CVE-2019-0708) and the August 2019 one (CVE-2019-1181, CVE-2019-1182), or any later cumulative update, then restart.' },
      { text: 'Confirm the build with Get-HotFix and re-scan port 3389 to check the host is no longer reported as vulnerable.' },
    ],
    risk: 'medium',
    impact: 'A restart is required and remote sessions are closed.',
    rollback: 'Uninstall the update with wusa /uninstall /kb:<number>.',
  },
  ssh: {
    criticality: 'high',
    summary: 'Portable OpenSSH 8.5p1 to 9.7p1 is exposed to CVE-2024-6387 (regreSSHion), a race condition that can give remote code execution as root. OpenSSH 9.8p1 fixes it.',
    steps: [
      {
        text: 'Upgrade the OpenSSH server package to the distribution fix (or 9.8p1) and restart the daemon.',
        command: 'sudo apt update && sudo apt install --only-upgrade openssh-server && sudo systemctl restart ssh',
      },
    ],
    risk: 'low',
    impact: 'New connections use the restarted daemon. Existing sessions stay open.',
    rollback: 'Reinstall the previous package version with the package manager.',
  },
};

// Patches tied directly to a finding type rather than a port (the finding IS the CVE: nothing to infer
// from a port number).
const CVE_PATCHES: Record<'zerologon' | 'log4shell', Omit<MockMitigation, 'addresses' | 'limitedContext' | 'controlType'>> = {
  zerologon: {
    criticality: 'critical',
    summary: 'CVE-2020-1472 (Zerologon, CVSS 10) lets an unauthenticated attacker on the network take over a domain controller. Microsoft fixed it on 11 August 2020; enforcement mode became mandatory on every domain controller on 9 February 2021.',
    steps: [
      { text: 'Install the 11 August 2020 security update (or any later one) on every domain controller, then restart. One unpatched domain controller keeps the domain exposed.' },
    ],
    risk: 'medium',
    impact: 'A restart of each domain controller is required. Update one at a time.',
    rollback: 'Uninstall the update with wusa /uninstall /kb:<number> (not recommended: this reopens the vulnerability).',
  },
  log4shell: {
    criticality: 'critical',
    summary: 'CVE-2021-44228 (Log4Shell) affects log4j-core 2.0-beta9 through 2.14.1. The fixes released between 10 and 28 December 2021 removed JNDI lookups and message-lookup substitution by default.',
    steps: [
      { text: 'Upgrade log4j-core to 2.17.1 or later on Java 8, 2.12.4 on Java 7, or 2.3.2 on Java 6, in every application that bundles it, then redeploy.' },
    ],
    risk: 'low',
    impact: 'A very old logging configuration that depends on removed lookup behavior can need adjustment after the jump to 2.17.x.',
    rollback: 'Redeploy the application with the previous log4j-core version (not recommended: this reopens the vulnerability).',
  },
};

const buildPatches = (findings: MitigationFinding[], hasAgent: boolean): MockMitigation[] => {
  const services = new Map<'smb' | 'rdp' | 'ssh', MitigationFinding[]>();
  const cves = new Map<'zerologon' | 'log4shell', MitigationFinding[]>();
  findings.forEach((finding) => {
    const port = finding.value?.split('/')[0];
    const key = finding.type.toLowerCase().includes('share') || port === '445'
      ? 'smb'
      : (port === '3389' && 'rdp') || (port === '22' && 'ssh') || undefined;
    if (key) services.set(key, [...(services.get(key) ?? []), finding]);
    const cveKey = (['zerologon', 'log4shell'] as const).find(k => mockKeyOf(finding).includes(k));
    if (cveKey) cves.set(cveKey, [...(cves.get(cveKey) ?? []), finding]);
  });
  const addressesOf = (matching: MitigationFinding[]) => matching.map(finding => ({
    type: finding.type,
    value: finding.value,
  }));
  return [
    ...[...services.entries()].map(([key, matching]) => ({
      ...SERVICE_PATCHES[key],
      addresses: addressesOf(matching),
      limitedContext: !hasAgent,
      controlType: 'patch' as const,
    })),
    ...[...cves.entries()].map(([key, matching]) => ({
      ...CVE_PATCHES[key],
      addresses: addressesOf(matching),
      limitedContext: !hasAgent,
      controlType: 'patch' as const,
    })),
  ];
};

const GENERIC_REMEDIATION_STEP: MockMitigation['steps'][number] = { text: 'Apply the vendor hardening baseline for the technique this action uses, then re-run the action to confirm the weakness is gone.' };

/**
 * Mock of what XTM One would answer for a REMEDIATION request of a group of assets: one remediation per
 * kind of finding, built from the same inputs as the mitigation (command, findings, operating system).
 */
export const buildRemediations = (input: {
  actionName: string;
  command?: string;
  findings: MitigationFinding[];
  hasAgent: boolean;
  platform?: string;
}): MockMitigation[] => {
  const byKey = new Map<string, MitigationFinding[]>();
  input.findings.forEach((finding) => {
    const key = Object.keys(FINDING_TYPE_REMEDIATIONS).find(k => mockKeyOf(finding).includes(k));
    if (key) byKey.set(key, [...(byKey.get(key) ?? []), finding]);
  });
  if (byKey.size === 0) {
    return [{
      criticality: 'medium',
      addresses: [],
      summary: `"${input.actionName}" was not prevented. No typed finding was produced, so this is a generic remediation.`,
      steps: [GENERIC_REMEDIATION_STEP],
      risk: 'low',
      impact: 'Depends on the baseline applied.',
      rollback: 'Revert the baseline change.',
      limitedContext: !input.hasAgent,
    }];
  }
  const remediations = [...byKey.entries()].map(([key, findings]) => {
    const part = FINDING_TYPE_REMEDIATIONS[key];
    const seen = new Set<string>();
    const addresses = findings
      .map(finding => ({
        type: finding.type,
        value: SENSITIVE_TYPES.some(sensitive => finding.type.toLowerCase().includes(sensitive)) ? undefined : finding.value,
      }))
      .filter((finding) => {
        const id = `${finding.type}|${finding.value ?? ''}`;
        if (seen.has(id)) return false;
        seen.add(id);
        return true;
      });
    return {
      criticality: part.criticality,
      addresses,
      summary: part.summary,
      steps: part.steps,
      risk: part.risk,
      impact: part.impact,
      rollback: part.rollback,
      limitedContext: !input.hasAgent,
      controlType: 'hardening' as const,
    };
  });
  // A patch is added for each exposed service that has a vendor fix; none otherwise.
  return [...remediations, ...buildPatches(input.findings, input.hasAgent)];
};
