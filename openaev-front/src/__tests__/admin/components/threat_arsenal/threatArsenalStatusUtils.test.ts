import { describe, expect, it } from 'vitest';

import { getStatusSeverity } from '../../../../admin/components/threat_arsenal/threatArsenalStatusUtils';

describe('getStatusSeverity', () => {
  describe.each([
    ['VERIFIED', 'low'],
    ['UNVERIFIED', 'medium'],
    ['DEPRECATED', 'neutral'],
    [undefined, 'neutral'],
    [null, 'neutral'],
    ['UNKNOWN', 'neutral'],
  ])('given_%s', (status, expected) => {
    it(`should_return_${expected}`, () => {
      // Arrange / Act
      const severity = getStatusSeverity(status);

      // Assert
      expect(severity).toBe(expected);
    });
  });
});
