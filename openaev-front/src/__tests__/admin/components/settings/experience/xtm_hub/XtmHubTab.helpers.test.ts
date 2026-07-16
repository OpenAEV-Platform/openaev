import { describe, expect, it } from 'vitest';

import { getRegistrationPlatformTitle, getXtmHubProductName } from '../../../../../../admin/components/settings/experience/xtm_hub/XtmHubTab.helpers';

describe('XtmHubTab helpers', () => {
  describe('getXtmHubProductName', () => {
    it('returns product name from search params', () => {
      expect(getXtmHubProductName('?productName=OpenAEV')).toEqual('OpenAEV');
    });

    it('returns null when product name is absent', () => {
      expect(getXtmHubProductName('?foo=bar')).toBeNull();
    });

    it('returns null when product name is blank', () => {
      expect(getXtmHubProductName('?productName=%20%20')).toBeNull();
    });
  });

  describe('getRegistrationPlatformTitle', () => {
    it('uses auto registration product name when available', () => {
      expect(
        getRegistrationPlatformTitle({
          autoRegistrationProductName: 'OpenAEV',
          fallbackPlatformTitle: 'OpenAEV Platform',
        }),
      ).toEqual('OpenAEV');
    });

    it('falls back to platform title when product name is missing', () => {
      expect(
        getRegistrationPlatformTitle({
          autoRegistrationProductName: null,
          fallbackPlatformTitle: 'OpenAEV Platform',
        }),
      ).toEqual('OpenAEV Platform');
    });
  });
});
