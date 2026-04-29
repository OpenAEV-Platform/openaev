import type { PlatformSettings, PublicPlatformSettings } from '../../utils/api-types';

export interface PlatformSettingsHelper {
  getPlatformSettings: () => PlatformSettings;
  getPublicSettings: () => PublicPlatformSettings;
  getPlatformName: () => string;
}
