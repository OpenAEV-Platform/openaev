import { describe, expect, it } from 'vitest';

import {
  ACTIONABLE_FINDING_CATEGORIES,
  FINDING_AGGREGATION_CATEGORIES,
  getFindingAggregationCategory,
} from '../../../../admin/components/findings/findingAggregationCategories';

describe('finding aggregation categories', () => {
  it('should expose the seven actionable workflow groups in order', () => {
    // Act
    const values = ACTIONABLE_FINDING_CATEGORIES.map(category => category.value);

    // Assert
    expect(values).toEqual([
      'SURFACE_REACHABILITY',
      'IDENTITIES',
      'CREDENTIAL_ACCESS',
      'PRIVILEGE_TRUST_STRUCTURE',
      'EXPLOITABLE_WEAKNESSES',
      'RESOURCES',
      'CONFIGURATION_POSTURE',
    ]);
  });

  it('should keep informative findings outside the actionable workflow', () => {
    // Act
    const category = getFindingAggregationCategory('INFORMATIVE');

    // Assert
    expect(FINDING_AGGREGATION_CATEGORIES).toHaveLength(8);
    expect(category.label).toBe('Informative');
  });

  it('should expose an unknown server value instead of masking it', () => {
    // Act
    const category = getFindingAggregationCategory('UNKNOWN');

    // Assert
    expect(category.label).toBe('Unknown category');
  });
});
