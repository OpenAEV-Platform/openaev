import { type AttackPattern } from '../api-types';

// eslint-disable-next-line import/prefer-default-export
export const sortAttackPattern = (attackPattern1: AttackPattern, attackPattern2: AttackPattern) => {
  if (attackPattern1.attack_pattern_name < attackPattern2.attack_pattern_name) {
    return -1;
  }
  if (attackPattern1.attack_pattern_name > attackPattern2.attack_pattern_name) {
    return 1;
  }
  return 0;
};
