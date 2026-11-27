import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import useUsageImpact from '../../../../../../admin/components/threat_arsenal/versions/usages/useUsageImpact';
import { type PayloadUsagesOutput } from '../../../../../../utils/api-types';
import { aUsage, intlWrapper, someUsages } from '../../threatArsenalTestUtils';

const impactOf = (usages: PayloadUsagesOutput | null) => renderHook(() => useUsageImpact(usages), { wrapper: intlWrapper }).result.current;

describe('useUsageImpact', () => {
  it('given no usages loaded should say nothing', () => {
    // Act
    const impact = impactOf(null);

    // Assert
    expect(impact).toBe('');
  });

  it('given usages of every type should count the scenarios then each type', () => {
    // Arrange
    const usages = someUsages([
      aUsage({
        usage_id: 'a',
        usage_scenario_id: 's1',
      }),
      aUsage({
        usage_id: 'b',
        usage_scenario_id: 's2',
      }),
      aUsage({
        usage_id: 'c',
        usage_type: 'atomic_testing',
        usage_scenario_id: undefined,
      }),
      aUsage({
        usage_id: 'd',
        usage_type: 'step',
        usage_scenario_id: undefined,
      }),
    ]);

    // Act
    const impact = impactOf(usages);

    // Assert
    expect(impact).toBe('2 scenarios · 2 scenario injects · 1 atomic test · 1 chained step');
  });

  it('given one scenario inject should use the singular labels and skip the missing types', () => {
    // Arrange
    const usages = someUsages([aUsage()]);

    // Act
    const impact = impactOf(usages);

    // Assert
    expect(impact).toBe('1 scenario · 1 scenario inject');
  });
});
