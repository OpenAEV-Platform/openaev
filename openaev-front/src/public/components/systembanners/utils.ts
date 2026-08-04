import { type PlatformSettings } from '../../../utils/api-types';
import { isDemoInstance } from '../../../utils/Environment';
import { recordEntries } from '../../../utils/utils';
import { LICENSE_OPTION_TRIAL, TOP_BANNER_HEIGHT } from '../trialbanners/constants';

const SYSTEM_BANNER_HEIGHT_PER_MESSAGE = 18;
// Extra breathing space kept below the system banner messages.
const SYSTEM_BANNER_VERTICAL_PADDING = 16;
export type BannerMessage = Record<'debug' | 'info' | 'warn' | 'error' | 'fatal', string[]>;
// eslint-disable-next-line import/prefer-default-export
export const computeBannerSettings = (settings: PlatformSettings) => {
  const bannerByLevel = settings.platform_banner_by_level;

  let numberOfElements = 0;
  if (bannerByLevel !== undefined) {
    for (const bannerLevel of recordEntries(bannerByLevel)) {
      numberOfElements += bannerLevel[1].length;
    }
  }
  if (settings.platform_run_mode === 'safe') {
    numberOfElements += 1;
  }

  // The system banner is only rendered when it actually has messages (see
  // SystemBanners), so reserve its height only then to avoid an unexplained
  // offset when banner levels exist but carry no messages.
  const isSystemBannerActivated = numberOfElements > 0;
  // Trial / demo / license banner displayed at the very top of the platform.
  const isTopBannerActivated = settings.platform_license?.license_type === LICENSE_OPTION_TRIAL
    || isDemoInstance(settings);

  // Reserve the actual height of each displayed banner so the top bar (logo,
  // search) is never glued to / hidden behind them. The top banner has a fixed
  // height (TOP_BANNER_HEIGHT), the system banner grows with its messages.
  const systemBannerHeight = isSystemBannerActivated
    ? (SYSTEM_BANNER_HEIGHT_PER_MESSAGE * numberOfElements) + SYSTEM_BANNER_VERTICAL_PADDING
    : 0;
  const topBannerHeight = isTopBannerActivated ? TOP_BANNER_HEIGHT : 0;
  const bannerHeightNumber = systemBannerHeight + topBannerHeight;
  const bannerHeight = `${bannerHeightNumber}px`;
  return {
    bannerByLevel,
    bannerHeight,
    bannerHeightNumber,
  };
};
