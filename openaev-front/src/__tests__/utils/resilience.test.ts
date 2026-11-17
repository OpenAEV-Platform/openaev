import { describe, expect, it } from 'vitest';

import { computeResilience } from '../../utils/resilience';

describe('computeResilience', () => {
  it('counts pending expectations in the total', () => {
    expect(computeResilience({
      success: 14,
      partial: 0,
      failed: 20,
      pending: 28,
    })).toEqual({
      total: 62,
      resolved: 34,
      resilience: 23,
    });
  });

  it('counts partial results as resolved but not as successes', () => {
    expect(computeResilience({
      success: 1,
      partial: 1,
      failed: 0,
      pending: 2,
    })).toEqual({
      total: 4,
      resolved: 2,
      resilience: 25,
    });
  });

  it('returns 0 with nothing resolved', () => {
    expect(computeResilience({
      success: 0,
      partial: 0,
      failed: 0,
      pending: 3,
    })).toEqual({
      total: 3,
      resolved: 0,
      resilience: 0,
    });
    expect(computeResilience({
      success: 0,
      partial: 0,
      failed: 0,
      pending: 0,
    }).resilience).toBe(0);
  });
});
