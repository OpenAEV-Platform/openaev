import { describe, expect, it } from 'vitest';

import getFindingTypeLabel from '../../../../admin/components/findings/FindingTypeLabel';

const translate = (key: string) => key;

describe('getFindingTypeLabel', () => {
  describe.each(['aws', 'azure', 'gcp', 'kubernetes', 'custom-cloud'])('given an OCSF provider', (provider) => {
    it('should display the provider-independent Misconfig type', () => {
      // Act
      const label = getFindingTypeLabel(translate, 'ocsf', provider);

      // Assert
      expect(label).toBe('Misconfig');
    });
  });

  it('given no OCSF provider should display the generic misconfiguration label', () => {
    // Act
    const label = getFindingTypeLabel(translate, 'ocsf');

    // Assert
    expect(label).toBe('Misconfig');
  });

  it('given a non-OCSF finding should preserve its existing label', () => {
    // Act
    const label = getFindingTypeLabel(translate, 'credentials', 'aws');

    // Assert
    expect(label).toBe('Credentials');
  });
});
