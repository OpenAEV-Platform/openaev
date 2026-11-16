import { describe, expect, it } from 'vitest';

import { expectationTypeLabelKey } from '../../../../../../admin/components/common/injects/expectations/Expectation';
import { isManualExpectation, isTechnicalExpectation } from '../../../../../../admin/components/common/injects/expectations/ExpectationUtils';

describe('Expectation type helpers', () => {
  describe('Function: expectationTypeLabelKey()', () => {
    it.each([
      ['PREVENTION', 'Prevention'],
      ['detection', 'Detection'],
      ['HUMAN_RESPONSE', 'Human Response'],
      ['ARTICLE', 'Article'],
      ['NOT_A_TYPE', undefined],
      [undefined, undefined],
    ])('given %s, should return %s', (type, expected) => {
      // -- ACT & ASSERT --
      expect(expectationTypeLabelKey(type)).toBe(expected);
    });
  });

  describe('Functions: isTechnicalExpectation() and isManualExpectation()', () => {
    it.each([
      ['PREVENTION', true, false],
      ['DETECTION', true, false],
      ['VULNERABILITY', true, false],
      ['MANUAL', false, true],
      ['CHALLENGE', false, true],
      ['ARTICLE', false, true],
      ['HUMAN_RESPONSE', false, false],
    ])('given %s, should be technical: %s, manual: %s', (type, technical, manual) => {
      // -- ACT & ASSERT --
      expect(isTechnicalExpectation(type)).toBe(technical);
      expect(isManualExpectation(type)).toBe(manual);
    });
  });
});
