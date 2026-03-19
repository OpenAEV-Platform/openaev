import { type AttackPattern, type EsSeriesData, type KillChainPhase } from '../../../../../../utils/api-types';

export interface ResolvedTTPData {
  key: string | undefined;
  value: number | undefined;
  label: string | undefined;
  attack_pattern_external_id: string | null;
  kill_chain_phase_external_id: string[] | undefined;
}

// Memoized parent lookup with iterative approach to avoid stack overflow on deep hierarchies
const retrieveParent = (attackPattern: AttackPattern, attackPatternMap: Record<string, AttackPattern>): AttackPattern => {
  let current = attackPattern;
  while (current.attack_pattern_parent) {
    const parent = attackPatternMap[current.attack_pattern_parent];
    if (!parent) break;
    current = parent;
  }
  return current;
};

// Build an index map from attack_pattern_id to AttackPattern for O(1) lookup
// This changes the algorithm from O(n*m) to O(n+m) where n=data.length, m=attackPatterns.length
const buildAttackPatternIdIndex = (attackPatternMap: Record<string, AttackPattern>): Map<string, AttackPattern> => {
  const index = new Map<string, AttackPattern>();
  for (const attackPattern of Object.values(attackPatternMap)) {
    index.set(attackPattern.attack_pattern_id, attackPattern);
  }
  return index;
};

export const resolvedData = (
  attackPatternMap: Record<string, AttackPattern>,
  killChainPhaseMap: Record<string, KillChainPhase>,
  data: EsSeriesData[],
): ResolvedTTPData[] => {
  // Build index once - O(m) where m = number of attack patterns
  const attackPatternIdIndex = buildAttackPatternIdIndex(attackPatternMap);

  // Process data with O(1) lookups - O(n) where n = data.length
  const result: ResolvedTTPData[] = [];
  for (const d of data) {
    if (!d.key) continue;

    const attackPattern = attackPatternIdIndex.get(d.key);
    if (attackPattern) {
      const parent = retrieveParent(attackPattern, attackPatternMap);
      result.push({
        key: d.key,
        value: d.value,
        label: d.label,
        attack_pattern_external_id: parent.attack_pattern_external_id,
        kill_chain_phase_external_id: attackPattern.attack_pattern_kill_chain_phases?.map(
          phase => killChainPhaseMap[phase]?.phase_external_id,
        ).filter(Boolean) as string[] | undefined,
      });
    }
  }
  return result;
};

// Pre-index resolved data by kill chain phase for faster filtering
export const buildKillChainPhaseIndex = (data: ResolvedTTPData[]): Map<string, ResolvedTTPData[]> => {
  const index = new Map<string, ResolvedTTPData[]>();
  for (const item of data) {
    if (item.kill_chain_phase_external_id) {
      for (const phase of item.kill_chain_phase_external_id) {
        const existing = index.get(phase) ?? [];
        existing.push(item);
        index.set(phase, existing);
      }
    }
  }
  return index;
};

export const filterByKillChainPhase = (data: ResolvedTTPData[], killChainPhase: string): ResolvedTTPData[] => {
  return data.filter(d => d.kill_chain_phase_external_id?.includes(killChainPhase));
};

export const SUCCESS_100_COLOR = '#103822';
export const SUCCESS_75_COLOR = '#2f5e3d';
export const SUCCESS_50_COLOR = '#644100';
export const SUCCESS_25_COLOR = '#5C1717';
