import { type MockMitigation } from './MitigationCard';

// PROTOTYPE - MOCK DATA ONLY. Canned mitigation suggestions used by the prototype screens.
export const MOCK_MITIGATIONS: Record<'windows' | 'linux' | 'network', MockMitigation> = {
  windows: {
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
    summary: 'The scan reached an exposed service. Without agent details this is a generic recommendation: reduce what the network exposes.',
    steps: [
      { text: 'Restrict the exposed service to the management VLAN with a firewall rule.' },
      { text: 'Disable anonymous and guest access on the service (for example SMB or FTP).' },
      { text: 'Require signing or encryption on the protocol where supported.' },
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
  steps: MockMitigation['steps'];
  impact: string;
  rollback: string;
  risk: MockMitigation['risk'];
}> = {
  portscan: {
    steps: [
      {
        text: 'Restrict the exposed port to the management network at the host and network firewall.',
        command: 'New-NetFirewallRule -DisplayName "Restrict SMB" -Direction Inbound -LocalPort 445 -Protocol TCP -RemoteAddress 10.0.0.0/24 -Action Allow',
      },
      { text: 'Close services that do not need to be reachable and re-scan to confirm.' },
    ],
    impact: 'Clients outside the allowed network lose access to the service.',
    rollback: 'Remove the firewall rule and re-enable the previous service configuration.',
    risk: 'low',
  },
  share: {
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

const GENERIC_STEP: MockMitigation['steps'][number] = { text: 'Apply the vendor hardening baseline for the technique this action uses, then re-run the action to validate.' };

/**
 * Mock of what XTM One would answer. The suggestion depends on the ACTION (payload / injector) and
 * on the finding types it produced; the agent environment only adds precision when there is one.
 */
export const buildMitigation = (input: {
  actionName: string;
  findingTypes: string[];
  hasAgent: boolean;
}): MockMitigation => {
  const known = input.findingTypes
    .map(type => type.toLowerCase())
    .map(type => Object.keys(FINDING_TYPE_MITIGATIONS).find(key => type.includes(key)))
    .filter((key): key is string => !!key);
  const unique = [...new Set(known)];
  const parts = unique.map(key => FINDING_TYPE_MITIGATIONS[key]);
  const steps = parts.length > 0 ? parts.flatMap(part => part.steps) : [GENERIC_STEP];
  if (input.hasAgent) {
    steps.push(MOCK_MITIGATIONS.windows.steps[0]);
  }
  const risks = parts.map(part => part.risk);
  let risk: MockMitigation['risk'] = 'low';
  if (risks.includes('high')) risk = 'high';
  else if (risks.includes('medium')) risk = 'medium';
  const findingsText = unique.length > 0 ? unique.join(', ') : 'no typed';
  return {
    summary: `"${input.actionName}" produced ${findingsText} findings. This suggestion targets what the action exposed${input.hasAgent ? ' and the agent environment' : ', without agent details'}.`,
    steps,
    risk,
    impact: parts.length > 0 ? parts.map(part => part.impact).join(' ') : 'Depends on the baseline applied.',
    rollback: parts.length > 0 ? parts.map(part => part.rollback).join(' ') : 'Revert the baseline change.',
    limitedContext: !input.hasAgent,
  };
};
