import type { AttackPatternSimple } from '../../../../../utils/api-types';

// Attack-path POC (issue 6647): a front-only, static injector -> MITRE ATT&CK technique lookup.
//
// Why static (front) and not resolved from the backend: the attack-path graph nodes only carry an
// injector short name (nmap, hydra, ...) and the seeded executions use synthetic contract ids
// (`contract-<injector>`) that do not join any real InjectorContract, so a live
// contract -> attackPatterns resolution returns nothing for the demo dataset.
// TODO(#6647): for GA, expose the technique(s) on AttackPathNodeDTO (resolve
// AttackPathExecution.contractExternalId -> InjectorContract.getAttackPatterns()) and drop this map.
const t = (externalId: string, name: string): AttackPatternSimple => ({
  attack_pattern_id: externalId,
  attack_pattern_external_id: externalId,
  attack_pattern_name: name,
});

// Keyed by the injector short name (lower-cased). netexec is the current name for crackmapexec.
const MITRE_BY_INJECTOR: Record<string, AttackPatternSimple[]> = {
  nmap: [t('T1046', 'Network Service Discovery')],
  hydra: [t('T1110', 'Brute Force')],
  crackmapexec: [t('T1021.002', 'Remote Services: SMB/Windows Admin Shares'), t('T1110', 'Brute Force')],
  netexec: [t('T1021.002', 'Remote Services: SMB/Windows Admin Shares'), t('T1110', 'Brute Force')],
  impacket: [t('T1003', 'OS Credential Dumping'), t('T1021.002', 'Remote Services: SMB/Windows Admin Shares')],
  metasploit: [t('T1210', 'Exploitation of Remote Services')],
};

const normalize = (raw?: string): string => (raw ?? '').trim().toLowerCase();

// ATT&CK technique(s) for an injector, by its short name/label (e.g. "nmap", "crackmapexec").
export const mitreForInjectorLabel = (label?: string): AttackPatternSimple[] =>
  MITRE_BY_INJECTOR[normalize(label)] ?? [];

// ATT&CK technique(s) for an execution, from its payload name (e.g. "nmap-payload" -> nmap).
export const mitreForPayloadName = (payloadName?: string): AttackPatternSimple[] => {
  const base = normalize(payloadName).replace(/-payload$/, '');
  return MITRE_BY_INJECTOR[base] ?? [];
};
