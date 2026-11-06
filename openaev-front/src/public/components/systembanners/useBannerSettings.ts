import { type PlatformSettings } from '../../../utils/api-types';
import { useNewsFeedRegisterBanner } from '../trialbanners/registerBannerState';
import { computeBannerSettings } from './utils';

/** Space taken by the top banners, including those whose display depends on more than the platform settings. */
const useBannerSettings = (settings: PlatformSettings) => {
  const { isShown } = useNewsFeedRegisterBanner();
  return computeBannerSettings(settings, { isRegisterBannerShown: isShown });
};

export default useBannerSettings;
