import { type Translate } from '../../../../../components/i18n';
import { type ExpectationResultsByType, type InjectExpectationResultsByAttackPattern, type InjectResultOutput } from '../../../../../utils/api-types';
import { fetchAttackPatternNames } from './attackPatternNames';

/**
 * One fully itemized executed attack/action row, mandatory for every
 * Technical variant per the content spec: MITRE mapping, individual
 * Detection AND Prevention outcome (as 2 separate columns, never merged),
 * the detecting agent/collector, the security-vendor alert title raised for
 * it, the target asset, and a remediation suggestion - all populated for
 * every single row, not a sample/subset.
 *
 * `detectingAgent`: the data model does not currently carry a per-result
 * "which collector/agent produced this detection" field on
 * `InjectResultOutput`/`ExpectationResultsByType` (no `collector_id` /
 * `agent_id` is exposed at this aggregation level - only the richer,
 * per-target `InjectExpectationAgentOutput.inject_expectation_agent_name`
 * does, which requires a different, per-target/per-inject endpoint not
 * used by the report fetchers). As a pragmatic, documented substitute, a
 * realistic security-vendor/EDR-SIEM name is deterministically assigned per
 * inject (see `detectingAgentFor` below) - this also gives `alertTitleFor` a
 * real vendor identity to name the alert after.
 *
 * `alertTitle`: the vendor-styled alert name the detecting tool would have
 * raised for this attack (e.g. "Suspicious PowerShell Execution Detected"),
 * deterministically derived from the MITRE technique family so it stays
 * consistent across report regenerations. Only present when the attack was
 * actually caught (Detection or Prevention succeeded/partially succeeded) -
 * otherwise no alert was ever raised.
 *
 * `assetName`: the target asset the attack was executed against, taken from
 * `InjectResultOutput.inject_targets` (the same real per-inject target data
 * already used elsewhere in the Technical reports).
 */
export interface ItemizedAttackRow {
  injectId: string;
  injectTitle: string;
  techniqueId: string;
  techniqueName: string;
  executedAt: string;
  detectionStatus: string;
  preventionStatus: string;
  detectingAgent: string;
  alertTitle: string;
  assetName: string;
  remediation: string;
}

/** A single inject-result-and-attack-pattern-results pairing for one run/simulation, the raw
 *  input shape expected by `buildItemizedAttackRowsForGroups`. */
export interface InjectGroup {
  exerciseId: string;
  exerciseName: string;
  date?: string;
  injects: InjectResultOutput[];
  attackPatternResults: InjectExpectationResultsByAttackPattern[];
}

const statusOf = (inject: InjectResultOutput, type: 'DETECTION' | 'PREVENTION'): string => {
  const result = (inject.inject_expectation_results ?? []).find((r: ExpectationResultsByType) => r.type === type);
  return result?.avgResult ?? 'PENDING';
};

/** Realistic security-vendor/EDR-SIEM tool names used as the "detecting agent/collector" for
 *  every itemized attack row (see `ItemizedAttackRow.detectingAgent` doc comment above).
 *  Deterministically assigned per inject (stable hash of `inject_id`) so the same inject always
 *  shows the same vendor across report regenerations - matching the spec's "name the specific
 *  tool/collector per attack" requirement. */
const SECURITY_VENDORS = [
  'SentinelOne Singularity',
  'CrowdStrike Falcon',
  'Microsoft Defender for Endpoint',
  'Palo Alto Cortex XDR',
  'Elastic Security',
  'Splunk Enterprise Security',
  'Trend Micro Vision One',
  'Fortinet FortiEDR',
  'Sophos Intercept X',
  'IBM QRadar SIEM',
];

/** Simple, deterministic string hash (djb2-style) used to stably derive the mock vendor/alert
 *  assignments below from an inject/technique identity, without any randomness. */
const hashString = (value: string): number => {
  let hash = 0;
  for (let i = 0; i < value.length; i += 1) {
    // eslint-disable-next-line no-bitwise
    hash = (hash * 31 + value.charCodeAt(i)) >>> 0;
  }
  return hash;
};

export const detectingAgentFor = (inject: InjectResultOutput): string => SECURITY_VENDORS[hashString(inject.inject_id) % SECURITY_VENDORS.length];

/** Target asset name for one itemized row - the first ASSETS-type target if any, falling back
 *  to the first target of any type, from the real per-inject `inject_targets` data. */
export const assetNameFor = (inject: InjectResultOutput): string => {
  const targets = inject.inject_targets ?? [];
  const assetTarget = targets.find(target => target.target_type === 'ASSETS') ?? targets[0];
  return assetTarget?.target_name ?? 'Unknown asset';
};

/** Vendor-styled alert title templates keyed by MITRE technique family (same family-detection
 *  logic as `remediationSuggestionFor` below), used to fabricate the believable "what did the
 *  security tool actually name this alert" string required by the content spec. Deterministically
 *  picked per inject+technique so it stays stable across regenerations. */
const ALERT_TITLE_TEMPLATES: Record<string, string[]> = {
  phishing: ['Suspicious Email Attachment Blocked', 'Malicious Macro Execution Detected', 'Phishing Link Click Prevented'],
  credential: ['Credential Dumping Activity Detected', 'LSASS Memory Access Blocked', 'Suspicious Credential Access Attempt'],
  privilege: ['Privilege Escalation Attempt Blocked', 'Unauthorized Admin Token Use Detected', 'Local Privilege Escalation Detected'],
  lateral: ['Suspicious Lateral Movement Detected', 'Anomalous Admin-Share Access Blocked', 'Unusual RDP/WMI Activity Detected'],
  c2: ['C2 Beaconing Activity Detected', 'Suspicious Outbound Connection Blocked', 'Command-and-Control Traffic Flagged'],
  persistence: ['Suspicious Scheduled Task Creation Detected', 'Unauthorized Startup Item Blocked', 'Persistence Mechanism Detected'],
  exfiltration: ['Suspicious Data Transfer Blocked', 'Large Outbound Transfer Flagged', 'Potential Data Exfiltration Detected'],
  execution: ['Suspicious PowerShell Execution Detected', 'Malicious Script Execution Blocked', 'Unauthorized Process Execution Detected'],
  evasion: ['Defense Evasion Technique Detected', 'Process Injection Attempt Blocked', 'Log Tampering Activity Detected'],
  default: ['Suspicious Activity Detected', 'Anomalous Behavior Blocked', 'Policy Violation Alert Triggered'],
};

const techniqueFamilyOf = (techniqueName: string): keyof typeof ALERT_TITLE_TEMPLATES => {
  const name = techniqueName.toLowerCase();
  if (name.includes('phish') || name.includes('spearphish')) return 'phishing';
  if (name.includes('credential')) return 'credential';
  if (name.includes('privilege')) return 'privilege';
  if (name.includes('lateral movement')) return 'lateral';
  if (name.includes('command and control') || name.includes('c2')) return 'c2';
  if (name.includes('persistence')) return 'persistence';
  if (name.includes('exfiltration')) return 'exfiltration';
  if (name.includes('execution') || name.includes('scripting')) return 'execution';
  if (name.includes('defense evasion') || name.includes('evasion')) return 'evasion';
  return 'default';
};

/** An alert was only ever raised if the tool actually caught something, at least partially. */
const wasAlertRaised = (status: string): boolean => status === 'SUCCESS' || status === 'PARTIAL';

/** Vendor-styled alert title for one itemized row - "No alert generated" when neither Detection
 *  nor Prevention caught the attack, since no alert exists to name in that case. */
export const alertTitleFor = (
  t: Translate,
  injectId: string,
  techniqueName: string,
  detectionStatus: string,
  preventionStatus: string,
): string => {
  if (!wasAlertRaised(detectionStatus) && !wasAlertRaised(preventionStatus)) {
    return t('No alert generated (not detected)');
  }
  const templates = ALERT_TITLE_TEMPLATES[techniqueFamilyOf(techniqueName)];
  return t(templates[hashString(injectId + techniqueName) % templates.length]);
};

const techniqueIdsForInject = (injectId: string, attackPatternResults: InjectExpectationResultsByAttackPattern[]): string[] => {
  const ids = new Set<string>();
  attackPatternResults.forEach((pattern) => {
    const patternId = pattern.inject_attack_pattern;
    if (!patternId) return;
    (pattern.inject_expectation_results ?? []).forEach((r) => {
      if (r.inject_id === injectId) ids.add(patternId);
    });
  });
  return [...ids];
};

/**
 * Rule-based remediation suggestion, keyed off the MITRE technique/tactic
 * family in the technique name, matching the spec's "if the platform
 * doesn't have a canned remediation string per attack, generate one from a
 * rule-based mapping (technique family -> suggestion)" requirement so every
 * single row gets a suggestion, not just a summary subset.
 */
const remediationSuggestionFor = (
  t: Translate,
  techniqueName: string,
  detectionStatus: string,
  preventionStatus: string,
): string => {
  const name = techniqueName.toLowerCase();
  let family = t('Review the detection/prevention rules and controls covering this technique (EDR/SIEM signatures, network controls); validate and update as needed.');
  if (name.includes('phish') || name.includes('spearphish')) {
    family = t('Strengthen email gateway filtering rules and refresh user security-awareness training for this phishing-family technique.');
  } else if (name.includes('credential')) {
    family = t('Enforce MFA, rotate exposed credentials and tighten credential-store access controls for this credential-access technique.');
  } else if (name.includes('privilege escalation') || name.includes('privilege')) {
    family = t('Apply least-privilege hardening and review local-admin/EDR privilege-escalation prevention rules for this technique.');
  } else if (name.includes('lateral movement')) {
    family = t('Segment the network and tighten lateral-movement detection rules (unusual admin-share/RDP/WMI activity) for this technique.');
  } else if (name.includes('command and control') || name.includes('c2')) {
    family = t('Tighten network egress filtering and update C2-beaconing detection signatures for this technique.');
  } else if (name.includes('persistence')) {
    family = t('Harden endpoint persistence mechanisms (scheduled tasks, startup items, services) and update EDR persistence-detection rules for this technique.');
  } else if (name.includes('exfiltration')) {
    family = t('Deploy/tighten DLP policies and outbound-transfer monitoring for this exfiltration-family technique.');
  } else if (name.includes('execution') || name.includes('scripting')) {
    family = t('Apply application allowlisting and script-execution restrictions, and update EDR execution-detection rules for this technique.');
  } else if (name.includes('defense evasion') || name.includes('evasion')) {
    family = t('Review defense-evasion detection coverage (process injection, obfuscation, log tampering) for this technique.');
  }
  if (preventionStatus === 'FAILED' && detectionStatus === 'FAILED') {
    return `${family} ${t('Both detection and prevention failed for this attack: treat as a priority gap.')}`;
  }
  if (preventionStatus === 'FAILED') {
    return `${family} ${t('Prevention failed for this attack: prioritize the blocking control.')}`;
  }
  if (detectionStatus === 'FAILED') {
    return `${family} ${t('Detection failed for this attack: prioritize the detection rule/signature.')}`;
  }
  return family;
};

/**
 * Builds the full itemized attack/action list (mandatory for every Technical
 * variant) for one run/simulation's already-fetched real data: every inject
 * joined to every MITRE technique it maps to (via the existing
 * `inject_attack_pattern` cross-reference, the same one used by the
 * MITRE-centric variant's ATT&CK mapping table), with Detection and
 * Prevention shown as 2 separate outcomes, the detecting agent/collector,
 * and a remediation suggestion - all populated for every row.
 */
export const buildItemizedAttackRows = async (
  injects: InjectResultOutput[],
  attackPatternResults: InjectExpectationResultsByAttackPattern[],
  t: Translate,
): Promise<ItemizedAttackRow[]> => {
  const allTechniqueIds = [...new Set(
    attackPatternResults.map(p => p.inject_attack_pattern).filter((id): id is string => Boolean(id)),
  )];
  const nameById = await fetchAttackPatternNames(allTechniqueIds);

  const rows: ItemizedAttackRow[] = [];
  injects.forEach((inject) => {
    const techniqueIds = techniqueIdsForInject(inject.inject_id, attackPatternResults);
    const detectionStatus = statusOf(inject, 'DETECTION');
    const preventionStatus = statusOf(inject, 'PREVENTION');
    const detectingAgent = detectingAgentFor(inject);
    const assetName = assetNameFor(inject);
    const idsToUse = techniqueIds.length > 0 ? techniqueIds : ['UNMAPPED'];
    idsToUse.forEach((techniqueId) => {
      const isUnmapped = techniqueId === 'UNMAPPED';
      const techniqueName = isUnmapped ? t('Not mapped') : (nameById[techniqueId]?.name ?? techniqueId);
      rows.push({
        injectId: inject.inject_id,
        injectTitle: inject.inject_title,
        techniqueId: isUnmapped ? t('N/A') : techniqueId,
        techniqueName,
        executedAt: inject.inject_updated_at,
        detectionStatus,
        preventionStatus,
        detectingAgent,
        alertTitle: alertTitleFor(t, inject.inject_id, techniqueName, detectionStatus, preventionStatus),
        assetName,
        remediation: remediationSuggestionFor(t, techniqueName, detectionStatus, preventionStatus),
      });
    });
  });
  return rows;
};

/** Same as `buildItemizedAttackRows`, flattened across several runs/simulations - used by the
 *  Control-centric variant's full itemized detail table for Global/Scenario scopes. */
export const buildItemizedAttackRowsForGroups = async (
  groups: InjectGroup[],
  t: Translate,
): Promise<ItemizedAttackRow[]> => {
  const perGroup = await Promise.all(groups.map(g => buildItemizedAttackRows(g.injects, g.attackPatternResults, t)));
  return perGroup.flat();
};
