import { type AttackPattern, type EsSeriesData, type KillChainPhase } from '../../../../../../utils/api-types';

export interface ResolvedTTPData {
  key: string | undefined;
  value: number | undefined;
  label: string | undefined;
  attack_pattern_external_id: string | null;
  kill_chain_phase_external_id: string[] | undefined;
}

const retrieveParent = (attackPattern: AttackPattern, attackPatternMap: Record<string, AttackPattern>) => {
  if (!attackPattern.attack_pattern_parent) {
    return attackPattern;
  } else {
    const parent = attackPatternMap[attackPattern.attack_pattern_parent];
    return retrieveParent(parent, attackPatternMap);
  }
};

export const resolvedData = (attackPatternMap: Record<string, AttackPattern>, killChainPhaseMap: Record<string, KillChainPhase>, data: EsSeriesData[]) => {
  return data.map((d) => {
    const attackPattern = Object.values(attackPatternMap).find(a => a.attack_pattern_id === d.key);
    if (attackPattern) {
      const parent = retrieveParent(attackPattern, attackPatternMap);
      return {
        key: d.key,
        value: d.value,
        label: d.label,
        attack_pattern_external_id: parent.attack_pattern_external_id,
        kill_chain_phase_external_id: attackPattern.attack_pattern_kill_chain_phases?.map(phase => killChainPhaseMap[phase].phase_external_id),
      };
    }
    return null;
  }).filter(d => d !== null);
};

export const filterByKillChainPhase = (data: ResolvedTTPData[], killChainPhase: string) => {
  return data.filter(d => d.kill_chain_phase_external_id?.includes(killChainPhase));
};

export const SUCCESS_100_COLOR = '#103822';
export const SUCCESS_75_COLOR = '#2f5e3d';
export const SUCCESS_50_COLOR = '#644100';
export const SUCCESS_25_COLOR = '#5C1717';
