import { type Theme } from '@mui/material';
import { describe, expect, it } from 'vitest';

import colorStyles from '../../components/Color';
import { computeInjectExpectationLabel, computeStatusStyle, getStatusColor, statusSeverity } from '../../utils/statusUtils';

const theme = {
  palette: {
    success: { main: 'success' },
    warning: { main: 'warning' },
    error: { main: 'error' },
    grey: { 500: 'grey' },
  },
} as unknown as Theme;

describe('statusUtils', () => {
  describe('Function: computeInjectExpectationLabel()', () => {
    it.each([
      ['SUCCESS', 'PREVENTION', 'Prevented'],
      ['PARTIAL', 'PREVENTION', 'Partially Prevented'],
      ['FAILED', 'PREVENTION', 'Not Prevented'],
      ['SUCCESS', 'DETECTION', 'Detected'],
      ['PARTIAL', 'DETECTION', 'Partially Detected'],
      ['FAILED', 'DETECTION', 'Not Detected'],
      ['SUCCESS', 'VULNERABILITY', 'Not vulnerable'],
      ['PARTIAL', 'VULNERABILITY', 'Partially Vulnerable'],
      ['FAILED', 'VULNERABILITY', 'Vulnerable'],
      ['PENDING', 'DETECTION', 'Pending'],
      ['success', 'detection', 'Detected'],
      ['FAILED', 'MANUAL', 'Failed'],
      ['UNKNOWN', 'DETECTION', undefined],
      ['SUCCESS', 'NOT_A_TYPE', undefined],
      [undefined, 'DETECTION', undefined],
    ])('given status %s on type %s, should return %s', (status, type, expected) => {
      // -- ACT --
      const label = computeInjectExpectationLabel(status, type);

      // -- ASSERT --
      expect(label).toBe(expected);
    });
  });

  describe('Function: statusSeverity() and computeStatusStyle()', () => {
    it.each([
      ['Prevented', 'SUCCESS'],
      ['Partially Detected', 'PARTIAL'],
      ['Not Detected', 'FAILED'],
      ['Partially Vulnerable', 'PARTIAL'],
    ])('given the result label %s, should colour it as %s', (label, result) => {
      // -- ACT & ASSERT --
      expect(statusSeverity(label)).toBe(statusSeverity(result));
      expect(computeStatusStyle(label)).toBe(computeStatusStyle(result));
    });

    it('given an unknown status, should fall back to the neutral blue grey', () => {
      // -- ACT & ASSERT --
      expect(computeStatusStyle('NOT_A_STATUS')).toBe(colorStyles.blueGrey);
      expect(statusSeverity(undefined)).toBe('neutral');
    });
  });

  describe('Function: getStatusColor()', () => {
    it.each([
      ['Detected', 'success'],
      ['Partially Prevented', 'warning'],
      ['Not Prevented', 'error'],
      ['Vulnerable', 'error'],
      ['Pending', 'grey'],
      ['NOT_A_STATUS', 'error'],
    ])('given %s, should return the %s colour', (status, expected) => {
      // -- ACT & ASSERT --
      expect(getStatusColor(theme, status)).toBe(expected);
    });
  });
});
