import { describe, expect, it } from 'vitest';

import { compareTargetResultTypes, getStatusLabel } from '../../../../../../admin/components/atomic_testings/atomic_testing/target_result/targetResultsSteps';

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
      ['LOGGING', ['SUCCESS'], 'Attack Logged'],
      ['LOGGING', ['FAILED'], 'Attack Not Logged'],
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

  describe('Function: compareTargetResultTypes()', () => {
    it('should sort Logging, then Detection, then Prevention, and unknown types last', () => {
      // -- ARRANGE --
      const types = ['UNKNOWN_TYPE', 'MANUAL', 'PREVENTION', 'VULNERABILITY', 'DETECTION', 'LOGGING'];

      // -- ACT --
      const sorted = types.toSorted(compareTargetResultTypes);

      // -- ASSERT --
      expect(sorted).toEqual(['LOGGING', 'DETECTION', 'PREVENTION', 'VULNERABILITY', 'MANUAL', 'UNKNOWN_TYPE']);
    });
  });
});
