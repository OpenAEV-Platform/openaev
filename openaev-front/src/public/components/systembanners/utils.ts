import { type PlatformSettings } from '../../../utils/api-types';
import { utcDate } from '../../../utils/Time';
import { isNotEmptyField, recordEntries, recordKeys } from '../../../utils/utils';

const SYSTEM_BANNER_HEIGHT_PER_MESSAGE = 18;
type BannerMessage = Record<'debug' | 'info' | 'warn' | 'error' | 'fatal', string[]>;

export const computeBanners = (settings: PlatformSettings): BannerMessage => {
  let bannerLevel = settings.platform_banner_by_level as BannerMessage;
  const ee = settings.platform_license ?? {};
  if (ee.license_is_enterprise) {
    if (!ee.license_is_validated) {
      bannerLevel = { error: [`The current ${ee.license_type} license has expired, Enterprise Edition is disabled.`] } as BannerMessage;
    } else if (ee.license_is_extra_expiration) {
      bannerLevel = { error: [`The current ${ee.license_type} license has expired, Enterprise Edition will be disabled in ${ee.license_extra_expiration_days} days.`] } as BannerMessage;
    } else if (ee.license_type === 'trial') {
      bannerLevel = { warn: [`This is a trial Enterprise Edition version, valid until ${utcDate(ee.license_expiration_date).format('YYYY-MM-DD')}.`] } as BannerMessage;
    }
  }
  return bannerLevel;
};

// eslint-disable-next-line import/prefer-default-export
export const computeBannerSettings = (settings: PlatformSettings) => {
  const bannerByLevel = computeBanners(settings);
  const isBannerActivated = bannerByLevel !== undefined && isNotEmptyField(recordKeys(bannerByLevel));
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
