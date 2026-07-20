import { type Theme } from '@mui/material';
import { describe, expect, it } from 'vitest';

import {
  calcPercentage,
  colorByAverageForExpectation,
  colorByLabel,
  EMPTY_DATA,
  isStatus,
} from '../../admin/components/workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';

const mockTheme = {
  palette: {
    widgets: {
      securityDomains: {
        colors: {
          success: 'rgb(2,129,8)',
          intermediate: 'rgb(255 216 0)',
          warning: 'rgb(245, 166, 35)',
          failed: 'rgb(220, 81, 72)',
          pending: 'rgba(248,243,243,0.37)',
          unknown: 'rgba(73,72,72,0.37)',
        },
      },
    },
  },
} as unknown as Theme;

const colors = mockTheme.palette.widgets.securityDomains.colors;

describe('colorByAverageForExpectation', () => {
  describe('when average is negative', () => {
    it('returns EMPTY_DATA for -1', () => {
      expect(colorByAverageForExpectation(-1, mockTheme)).toBe(EMPTY_DATA);
    });

    it('returns EMPTY_DATA for -100', () => {
      expect(colorByAverageForExpectation(-100, mockTheme)).toBe(EMPTY_DATA);
    });
  });

  describe('when average is between 0 and 25 (failed)', () => {
    it('returns failed color for 0', () => {
      expect(colorByAverageForExpectation(0, mockTheme)).toBe(colors.failed);
    });

    it('returns failed color for 10', () => {
      expect(colorByAverageForExpectation(10, mockTheme)).toBe(colors.failed);
    });

    it('returns failed color for 25', () => {
      expect(colorByAverageForExpectation(25, mockTheme)).toBe(colors.failed);
    });
  });

  describe('when average is between 26 and 50 (warning)', () => {
    it('returns warning color for 26', () => {
      expect(colorByAverageForExpectation(26, mockTheme)).toBe(colors.warning);
    });

    it('returns warning color for 50', () => {
      expect(colorByAverageForExpectation(50, mockTheme)).toBe(colors.warning);
    });
  });

  describe('when average is between 51 and 75 (intermediate)', () => {
    it('returns intermediate color for 51', () => {
      expect(colorByAverageForExpectation(51, mockTheme)).toBe(colors.intermediate);
    });

    it('returns intermediate color for 75', () => {
      expect(colorByAverageForExpectation(75, mockTheme)).toBe(colors.intermediate);
    });
  });

  describe('when average is between 76 and 100 (success)', () => {
    it('returns success color for 76', () => {
      expect(colorByAverageForExpectation(76, mockTheme)).toBe(colors.success);
    });

    it('returns success color for 100', () => {
      expect(colorByAverageForExpectation(100, mockTheme)).toBe(colors.success);
    });
  });

  describe('when average exceeds 100', () => {
    it('returns unknown color for 101', () => {
      expect(colorByAverageForExpectation(101, mockTheme)).toBe(colors.unknown);
    });
  });
});

describe('isStatus', () => {
  it('matches statuses case-insensitively (raw ES bucket keys)', () => {
    expect(isStatus('success', 'SUCCESS')).toBe(true);
    expect(isStatus('SUCCESS', 'SUCCESS')).toBe(true);
    expect(isStatus('Failed', 'FAILED')).toBe(true);
  });

  it('does not match different statuses', () => {
    expect(isStatus('PENDING', 'SUCCESS')).toBe(false);
    expect(isStatus('failed', 'SUCCESS')).toBe(false);
  });

  it('handles null and undefined values', () => {
    expect(isStatus(null, 'SUCCESS')).toBe(false);
    expect(isStatus(undefined, 'SUCCESS')).toBe(false);
    expect(isStatus('', 'SUCCESS')).toBe(false);
  });
});

describe('calcPercentage', () => {
  it('computes the percentage over the given total', () => {
    expect(calcPercentage(1, 4)).toBe(25);
    expect(calcPercentage(3, 3)).toBe(100);
    expect(calcPercentage(0, 5)).toBe(0);
  });

  it('returns -1 (empty state) when the total is zero, e.g. pending-only domains', () => {
    expect(calcPercentage(0, 0)).toBe(-1);
  });
});

describe('colorByLabel', () => {
  it('resolves success and failed colors case-insensitively', () => {
    expect(colorByLabel('success', mockTheme)).toBe(colors.success);
    expect(colorByLabel('SUCCESS', mockTheme)).toBe(colors.success);
    expect(colorByLabel('failed', mockTheme)).toBe(colors.failed);
    expect(colorByLabel('FAILED', mockTheme)).toBe(colors.failed);
  });

  it('falls back to the pending color for anything else', () => {
    expect(colorByLabel('PENDING', mockTheme)).toBe(colors.pending);
    expect(colorByLabel(null, mockTheme)).toBe(colors.pending);
  });
});
