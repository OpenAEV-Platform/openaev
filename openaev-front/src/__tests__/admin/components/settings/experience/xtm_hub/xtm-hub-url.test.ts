import { describe, expect, it } from 'vitest';

import { buildXtmHubRegistrationUrl } from '../../../../../../admin/components/settings/experience/xtm_hub/xtm-hub-url';

const PLATFORM_ID = '2295d0ae-6438-4012-9791-6cc66afa354d';
const TENANT_ID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

describe('buildXtmHubRegistrationUrl', () => {
  describe.each([
    ['https://hub.filigran.io'],
    ['https://hub.filigran.io/'],
  ])('with the hub url %s', (hubUrl) => {
    it('links to the hub redirect resolving the registration of the platform and tenant', () => {
      // Act
      const url = buildXtmHubRegistrationUrl(hubUrl, PLATFORM_ID, TENANT_ID);

      // Assert
      expect(url).toBe(`https://hub.filigran.io/redirect/openaev-registration?platform_id=${PLATFORM_ID}&tenant_id=${TENANT_ID}`);
    });
  });

  it('leaves the tenant out when it is unknown', () => {
    // Act
    const url = buildXtmHubRegistrationUrl('https://hub.filigran.io', PLATFORM_ID, undefined);

    // Assert
    expect(url).toBe(`https://hub.filigran.io/redirect/openaev-registration?platform_id=${PLATFORM_ID}`);
  });

  it('falls back to the hub when the platform id is unknown', () => {
    // Act
    const url = buildXtmHubRegistrationUrl('https://hub.filigran.io', undefined, TENANT_ID);

    // Assert
    expect(url).toBe('https://hub.filigran.io');
  });
});
