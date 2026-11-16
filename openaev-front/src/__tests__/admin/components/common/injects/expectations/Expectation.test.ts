import { describe, expect, it } from 'vitest';

import { defaultPayloadExpectations, expectationTypeLabelKey } from '../../../../../../admin/components/common/injects/expectations/Expectation';
import {
  enabledExpectations,
  isManualExpectation,
  isTechnicalExpectation,
  predefinedExpectations,
} from '../../../../../../admin/components/common/injects/expectations/ExpectationUtils';

describe('Expectation type helpers', () => {
  describe('Function: expectationTypeLabelKey()', () => {
    it.each([
      ['PREVENTION', 'Prevention'],
      ['detection', 'Detection'],
      ['LOGGING', 'Logging'],
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
      ['LOGGING', true, false],
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

  describe('Functions: enabledExpectations() and predefinedExpectations()', () => {
    const contractExpectations = [
      {
        expectation_type: 'PREVENTION',
        expectation_is_predefined: true,
      },
      {
        expectation_type: 'DETECTION',
        expectation_is_predefined: false,
      },
      {
        expectation_type: 'LOGGING',
        expectation_is_predefined: true,
      },
    ];

    it.each([
      [true, ['PREVENTION', 'DETECTION', 'LOGGING'], ['PREVENTION', 'LOGGING']],
      [false, ['PREVENTION', 'DETECTION'], ['PREVENTION']],
    ])('given Logging enabled: %s, should keep %j and predefine %j', (isLoggingEnabled, enabled, predefined) => {
      // -- ACT --
      const enabledTypes = enabledExpectations(contractExpectations, isLoggingEnabled).map(e => e.expectation_type);
      const predefinedTypes = predefinedExpectations(contractExpectations, isLoggingEnabled).map(e => e.expectation_type);

      // -- ASSERT --
      expect(enabledTypes).toEqual(enabled);
      expect(predefinedTypes).toEqual(predefined);
    });

    it('given no contract expectations, should return an empty list', () => {
      // -- ACT & ASSERT --
      expect(enabledExpectations(undefined, false)).toEqual([]);
      expect(predefinedExpectations(undefined, true)).toEqual([]);
    });
  });

  describe('Function: defaultPayloadExpectations()', () => {
    it.each([
      [true, ['PREVENTION', 'DETECTION', 'LOGGING']],
      [false, ['PREVENTION', 'DETECTION']],
    ])('given Logging enabled: %s, should return %j', (isLoggingEnabled, expected) => {
      // -- ACT & ASSERT --
      expect(defaultPayloadExpectations(isLoggingEnabled)).toEqual(expected);
    });
  });
});
