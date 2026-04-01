import { describe, expect, it } from 'vitest';

import { getBackgroundColor } from '../../admin/components/workspaces/custom_dashboards/widgets/viz/AttackPatternBox';
import {
  SUCCESS_25_COLOR,
  SUCCESS_50_COLOR,
  SUCCESS_75_COLOR,
  SUCCESS_100_COLOR,
} from '../../admin/components/workspaces/custom_dashboards/widgets/viz/securityCoverageUtils';

describe('getBackgroundColor', () => {
  describe('when successRate is null', () => {
    it('returns undefined', () => {
      expect(getBackgroundColor(null)).toBeUndefined();
    });
  });

  describe('when successRate is 0% (0)', () => {
    it('returns SUCCESS_25_COLOR (red)', () => {
      expect(getBackgroundColor(0)).toBe(SUCCESS_25_COLOR);
    });
  });

  describe('when successRate is 1/100 (0.01)', () => {
    it('returns SUCCESS_25_COLOR (red)', () => {
      expect(getBackgroundColor(0.01)).toBe(SUCCESS_25_COLOR);
    });
  });

  describe('when successRate is just below 25% (0.24)', () => {
    it('returns SUCCESS_25_COLOR (red)', () => {
      expect(getBackgroundColor(0.24)).toBe(SUCCESS_25_COLOR);
    });
  });

  describe('when successRate is exactly 25% (0.25)', () => {
    it('returns SUCCESS_50_COLOR (orange)', () => {
      expect(getBackgroundColor(0.25)).toBe(SUCCESS_50_COLOR);
    });
  });

  describe('when successRate is 49% (0.49)', () => {
    it('returns SUCCESS_50_COLOR (orange)', () => {
      expect(getBackgroundColor(0.49)).toBe(SUCCESS_50_COLOR);
    });
  });

  describe('when successRate is exactly 50% (0.50)', () => {
    it('returns SUCCESS_75_COLOR (green)', () => {
      expect(getBackgroundColor(0.50)).toBe(SUCCESS_75_COLOR);
    });
  });

  describe('when successRate is 74% (0.74)', () => {
    it('returns SUCCESS_75_COLOR (green)', () => {
      expect(getBackgroundColor(0.74)).toBe(SUCCESS_75_COLOR);
    });
  });

  describe('when successRate is exactly 75% (0.75)', () => {
    it('returns SUCCESS_100_COLOR (dark green)', () => {
      expect(getBackgroundColor(0.75)).toBe(SUCCESS_100_COLOR);
    });
  });

  describe('when successRate is 99% (0.99)', () => {
    it('returns SUCCESS_100_COLOR (dark green)', () => {
      expect(getBackgroundColor(0.99)).toBe(SUCCESS_100_COLOR);
    });
  });

  describe('when successRate is 100% (1)', () => {
    it('returns SUCCESS_100_COLOR (dark green)', () => {
      expect(getBackgroundColor(1)).toBe(SUCCESS_100_COLOR);
    });
  });
});
