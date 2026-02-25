import { type PlatformSettings } from '../../../utils/api-types';
import { isNotEmptyField, recordEntries, recordKeys } from '../../../utils/utils';
import { LICENSE_OPTION_TRIAL } from '../trialbanners/LicenseBanner';

const SYSTEM_BANNER_HEIGHT_PER_MESSAGE = 18;
export type BannerMessage = Record<'debug' | 'info' | 'warn' | 'error' | 'fatal', string[]>;
// eslint-disable-next-line import/prefer-default-export
export const computeBannerSettings = (settings: PlatformSettings) => {
  const bannerByLevel = settings.platform_banner_by_level;
  const isBannerActivated = (bannerByLevel !== undefined
    && isNotEmptyField(recordKeys(bannerByLevel)))
  || settings.platform_license?.license_type === LICENSE_OPTION_TRIAL
  || (settings.platform_base_url === 'https://demo.openaev.io' && settings.enabled_dev_features?.includes('OPENAEV_TRIALS_XTMHUB'));
  let numberOfElements = 0;
  if (settings.platform_banner_by_level !== undefined) {
    for (const bannerLevel of recordEntries(settings.platform_banner_by_level)) {
      numberOfElements += bannerLevel[1].length;
    }
  }
  const bannerHeight = isBannerActivated ? `${(SYSTEM_BANNER_HEIGHT_PER_MESSAGE * numberOfElements) + 16}px` : '0';
  const bannerHeightNumber = isBannerActivated ? (SYSTEM_BANNER_HEIGHT_PER_MESSAGE * numberOfElements) + 16 : 0;
  return {
    bannerByLevel,
    bannerHeight,
    bannerHeightNumber,
  };
};
