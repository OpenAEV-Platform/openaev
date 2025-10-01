import { type AttackPattern } from '../../utils/api-types';

export interface AttackPatternHelper {
  getAttackPatterns: () => AttackPattern[];
  getAttackPatternsMap: () => Record<string, AttackPattern>;
}
