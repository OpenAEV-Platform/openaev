import { describe, expect, it } from 'vitest';

import { getStatusLabel } from '../../../../../../admin/components/atomic_testings/atomic_testing/target_result/targetResultsSteps';

describe('targetResultsSteps', () => {
  describe('Function: getStatusLabel()', () => {
    it.each([
      ['DETECTION', ['UNKNOWN'], 'No Expectation for Detection'],
      ['DETECTION', ['PENDING'], 'Waiting for Detection'],
      ['DETECTION', ['SUCCESS'], 'Attack Detected'],
      ['DETECTION', ['FAILED'], 'Attack Not Detected'],
      ['PREVENTION', ['UNKNOWN'], 'No Expectation'],
      ['PREVENTION', ['PENDING'], 'Waiting for Prevention'],
      ['PREVENTION', ['SUCCESS'], 'Attack Prevented'],
      ['PREVENTION', ['PARTIAL'], 'Attack Not Prevented'],
      ['MANUAL', ['UNKNOWN'], 'No Expectation for Manual'],
      ['ARTICLE', ['PENDING'], 'Waiting for Validation'],
      ['CHALLENGE', ['SUCCESS'], 'Validation Success'],
      ['MANUAL', ['FAILED'], 'Validation Failed'],
      ['VULNERABILITY', ['SUCCESS'], ''],
    ])('given type %s with statuses %j, should return "%s"', (type, statuses, expected) => {
      // -- ACT & ASSERT --
      expect(getStatusLabel(type, statuses)).toBe(expected);
    });
  });
});
