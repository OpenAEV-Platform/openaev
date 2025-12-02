import * as R from 'ramda';

import { type AttackPattern, type InjectorContractFullOutput } from '../api-types';

export const externalContractTypesWithFindings = ['openaev_nmap', 'openaev_nuclei', 'openaev_aws'];

const computeAttackPatterns = (attackPatternIds: InjectorContractFullOutput['injector_contract_attack_patterns'], attackPatternsMap: Record<string, AttackPattern | undefined>) => {
  const attackPatternParents = (attackPatternIds ?? []).flatMap((attackPattern) => {
    const attackPatternParentId = attackPatternsMap[attackPattern]?.attack_pattern_parent;
    if (attackPatternParentId && attackPatternsMap[attackPatternParentId]) {
      return [attackPatternsMap[attackPatternParentId]];
    }
    return [];
  });
  if (!R.isEmpty(attackPatternParents)) {
    return attackPatternParents;
  }
  return (attackPatternIds ?? []).reduce((acc, attackPattern) => {
    if (attackPatternsMap[attackPattern]) {
      return [...acc, attackPatternsMap[attackPattern]];
    }
    return acc;
  }, [] as AttackPattern[]);
};

export default computeAttackPatterns;
