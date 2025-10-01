import type { AttackPattern } from '../../../../utils/api-types';
import AttackPatternChip from '../../../AttackPatternChip';

type Props = {
  attackPatternIds: string[];
  attackPatterns: AttackPattern[];
};

const AttackPatternFragment = ({ attackPatternIds = [], attackPatterns }: Props) => {
  return attackPatternIds.map((id) => {
    const attackPattern = attackPatterns.find(ap => ap.attack_pattern_id === id);
    return attackPattern && (
      <AttackPatternChip key={attackPattern.attack_pattern_id} attackPattern={attackPattern}></AttackPatternChip>
    );
  });
};

export default AttackPatternFragment;
