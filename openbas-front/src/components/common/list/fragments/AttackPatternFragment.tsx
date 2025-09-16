import type { AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { useHelper } from '../../../../store';
import type { AttackPattern } from '../../../../utils/api-types';
import AttackPatternChip from '../../../AttackPatternChip';

const AttackPatternFragment = ({ attackPatternIds = [] }: { attackPatternIds: string[] }) => {
  return attackPatternIds.map((id) => {
    const { attackPatterns }: { attackPatterns: AttackPattern[] } = useHelper((helper: AttackPatternHelper) => {
      return { attackPatterns: helper.getAttackPatterns() };
    });

    const attackPattern = attackPatterns.find(ap => ap.attack_pattern_id === id);
    return attackPattern && (
      <AttackPatternChip key={attackPattern.attack_pattern_id} attackPattern={attackPattern}></AttackPatternChip>
    );
  });
};

export default AttackPatternFragment;
