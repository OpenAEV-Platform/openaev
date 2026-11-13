import { type MitigationFinding, type MockMitigation } from './MitigationCard';

// PROTOTYPE - MOCK DATA ONLY. Canned mitigation suggestions used by the prototype screens.
export const MOCK_MITIGATIONS: Record<'windows' | 'linux' | 'network', MockMitigation> = {
  windows: {
    criticality: 'critical',
    addresses: [{ type: 'credentials' }],
    summary: 'LSASS memory can be read from this Windows 10 22H2 host while running as SYSTEM. Block credential dumping at the OS level.',
    steps: [
      {
        text: 'Enable LSA protection (RunAsPPL) so only protected processes can open LSASS.',
        command: 'reg add HKLM\\SYSTEM\\CurrentControlSet\\Control\\Lsa /v RunAsPPL /t REG_DWORD /d 1 /f',
      },
      {
        text: 'Turn on the Attack Surface Reduction rule "Block credential stealing from LSASS".',
        command: 'Set-MpPreference -AttackSurfaceReductionRules_Ids 9e6c4e1f-7d60-472f-ba1a-a39ef669e4b2 -AttackSurfaceReductionRules_Actions Enabled',
      },
      { text: 'Enable Credential Guard through Group Policy (Device Guard) and reboot.' },
      { text: 'Remove local admin rights from accounts that do not need them.' },
    ],
    risk: 'medium',
    impact: 'Legacy software that injects into LSASS (some SSO and AV agents) may stop working. A reboot is required.',
    rollback: 'Set RunAsPPL to 0, disable the ASR rule and Credential Guard policy, then reboot.',
    limitedContext: false,
  },
  linux: {
    criticality: 'high',
    addresses: [{ type: 'credentials' }],
    summary: 'The command ran as www-data and reached sensitive files. Restrict what the web service account can read and execute.',
    steps: [
      {
        text: 'Confine the service with an AppArmor profile in enforce mode.',
        command: 'sudo aa-enforce /etc/apparmor.d/usr.sbin.nginx',
      },
      {
        text: 'Remove world-readable permissions on credential files.',
        command: 'sudo chmod o-r /etc/app/credentials.conf',
      },
      {
        text: 'Mount /tmp with noexec so dropped scripts cannot run.',
        command: 'mount -o remount,noexec,nosuid /tmp',
      },
    ],
    risk: 'low',
    impact: 'Applications that execute helper scripts from /tmp will fail until they use another directory.',
    rollback: 'Switch the AppArmor profile back to complain mode and remount /tmp without noexec.',
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
// finding values are masked and never leave the platform).
const FINDING_TYPE_MITIGATIONS: Record<string, {
  summary: string;
  criticality: MockMitigation['criticality'];
  steps: MockMitigation['steps'];
  impact: string;
  rollback: string;
  risk: MockMitigation['risk'];
}> = {
  portscan: {
    summary: 'The scan found services reachable from the attacker position. Reduce what the network exposes.',
    criticality: 'low',
    // Replaced by one step per reported port in buildMitigations (target OS unknown: no command).
    steps: [
      { text: 'Restrict the exposed ports to the management network at the host and network firewall.' },
      { text: 'Close services that do not need to be reachable and re-scan to confirm.' },
    ],
    impact: 'Clients outside the allowed network lose access to the service.',
    rollback: 'Remove the firewall rule and re-enable the previous service configuration.',
    risk: 'low',
  },
  share: {
    summary: 'Windows administrative shares are reachable from the attacker position. Remove open access and require signing.',
    criticality: 'high',
    steps: [
      {
        text: 'Remove anonymous and "Everyone" access from the exposed share.',
        command: 'Revoke-SmbShareAccess -Name <share> -AccountName Everyone -Force',
      },
      {
        text: 'Disable automatic administrative shares and require SMB signing.',
        command: 'Set-SmbServerConfiguration -AutoShareServer $false -RequireSecuritySignature $true -Force',
      },
    ],
    impact: 'Tools that rely on administrative shares (remote backups, some deployment tools) will stop working.',
    rollback: 'Re-enable AutoShareServer and restore the previous share permissions.',
    risk: 'medium',
  },
  credentials: {
    summary: 'Credentials were captured by the action. Rotate them and make this kind of capture harder.',
    criticality: 'critical',
    steps: [
      { text: 'Rotate the captured credentials now and force a password reset for the affected accounts.' },
      { text: 'Deploy LAPS so local administrator passwords are unique per host.' },
      { text: 'Require MFA for the affected accounts and block NTLM where it is not needed.' },
    ],
    impact: 'Users are logged out and must reset their password. Legacy apps that depend on NTLM may fail.',
    rollback: 'Credential rotation cannot be rolled back. Re-allow NTLM through policy if an application breaks.',
    risk: 'high',
  },
};

const PORT_SERVICES: Record<string, string> = {
  445: 'SMB',
  3389: 'RDP',
  22: 'SSH',
  21: 'FTP',
};

const GENERIC_STEP: MockMitigation['steps'][number] = { text: 'Apply the vendor hardening baseline for the technique this action uses, then re-run the action to validate.' };

// Finding types whose values are sensitive: only the type is known outside the platform.
export const SENSITIVE_TYPES = ['credentials', 'password', 'hash', 'secret', 'token'];

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
    const key = Object.keys(FINDING_TYPE_MITIGATIONS).find(k => finding.type.toLowerCase().includes(k));
    if (key) byKey.set(key, [...(byKey.get(key) ?? []), finding]);
  });
  if (byKey.size === 0) {
    return [{
      criticality: 'medium',
      addresses: [],
      summary: `"${input.actionName}" was not prevented. No typed finding was produced, so this is a generic suggestion.`,
      steps: [GENERIC_STEP],
      risk: 'low',
      impact: 'Depends on the baseline applied.',
      rollback: 'Revert the baseline change.',
      limitedContext: !input.hasAgent,
    }];
  }
  return [...byKey.entries()].map(([key, findings]) => {
    const part = FINDING_TYPE_MITIGATIONS[key];
    let steps = [...part.steps];
    if (key === 'portscan') {
      const portSteps = findings
        .filter(finding => finding.value)
        .map(finding => ({ text: `Restrict ${finding.value} (${PORT_SERVICES[finding.value!.split('/')[0]] ?? 'service'}) to the management network, or close it if nothing needs it.` }));
      if (portSteps.length > 0) steps = [...portSteps, part.steps[1]];
    }
    if (input.hasAgent && key === 'credentials' && (input.platform ?? 'windows').toLowerCase().includes('windows')) steps.push(MOCK_MITIGATIONS.windows.steps[0]);
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
      steps,
      risk: part.risk,
      impact: part.impact,
      rollback: part.rollback,
      limitedContext: !input.hasAgent,
    };
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
