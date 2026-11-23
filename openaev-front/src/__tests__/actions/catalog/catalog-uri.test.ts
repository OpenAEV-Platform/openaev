import { describe, expect, it } from 'vitest';

import { catalogItemUri, catalogVersionUri } from '../../../actions/catalog/catalog-uri';

describe('catalog-uri', () => {
  it.each([
    ['payloads' as const, '/api/catalog/payloads/a%2Fb'],
    ['scenarios' as const, '/api/catalog/scenarios/a%2Fb'],
  ])('given_%s_should_encodeTheExternalId', (kind, expected) => {
    // Act
    const uri = catalogItemUri(kind, 'a/b');

    // Assert
    expect(uri).toBe(expected);
  });

  it('given_aVersion_should_encodeItUnderVersions', () => {
    // Act
    const uri = catalogVersionUri('scenarios', 'phishing', '1.0 beta');

    // Assert
    expect(uri).toBe('/api/catalog/scenarios/phishing/versions/1.0%20beta');
  });
});
