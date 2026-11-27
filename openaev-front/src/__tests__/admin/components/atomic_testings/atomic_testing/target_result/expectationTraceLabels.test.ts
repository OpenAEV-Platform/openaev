import { describe, expect, it } from 'vitest';

import {
  hasCollectorTraces,
  traceMatchTypeLabelKey,
  traceWording,
} from '../../../../../../admin/components/atomic_testings/atomic_testing/target_result/expectationTraceLabels';

describe('expectationTraceLabels', () => {
  describe('Function: traceMatchTypeLabelKey()', () => {
    it.each([
      ['SOURCE_IP', 'Source IP'],
      [undefined, undefined],
    ] as const)('given match type %s, should return %s', (matchType, expected) => {
      // -- ACT & ASSERT --
      expect(traceMatchTypeLabelKey(matchType)).toBe(expected);
    });
  });

  describe('Function: hasCollectorTraces()', () => {
    it.each([
      ['Prevented', true],
      ['Detected', true],
      ['Logged', true],
      ['logged', true],
      ['Not Logged', false],
      ['Not Detected', false],
      ['Success', false],
      [undefined, false],
    ])('given result %s, should return %s', (result, expected) => {
      // -- ACT & ASSERT --
      expect(hasCollectorTraces(result)).toBe(expected);
    });
  });

  describe('Function: traceWording()', () => {
    it('given a Logging expectation, should talk about log events', () => {
      // -- ACT --
      const wording = traceWording('LOGGING');

      // -- ASSERT --
      expect(wording.column).toBe('Log events');
      expect(wording.timeColumn).toBe('Logging time');
    });

    it.each(['DETECTION', 'PREVENTION', undefined])('given expectation type %s, should talk about alerts', (type) => {
      // -- ACT --
      const wording = traceWording(type);

      // -- ASSERT --
      expect(wording.column).toBe('Alerts');
      expect(wording.timeColumn).toBe('Detection time');
    });
  });
});
