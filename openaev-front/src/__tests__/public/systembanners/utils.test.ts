import { describe, expect, it } from 'vitest';

import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import { LICENSE_OPTION_TRIAL, TOP_BANNER_HEIGHT } from '../../../public/components/trialbanners/constants';
import { type PlatformSettings } from '../../../utils/api-types';

const SYSTEM_BANNER_HEIGHT_PER_MESSAGE = 18;
const SYSTEM_BANNER_VERTICAL_PADDING = 16;
// Mirrors DEMO_PLATFORM_URL in utils/Environment (kept private there).
const DEMO_PLATFORM_URL = 'https://demo.openaev.io';

const buildSettings = (overrides: Partial<PlatformSettings> = {}): PlatformSettings => ({
  platform_base_url: 'https://app.example.com',
  ...overrides,
} as unknown as PlatformSettings);

describe('computeBannerSettings', () => {
  it('given no banners should reserve no height', () => {
    const result = computeBannerSettings(buildSettings());

    expect(result.bannerHeightNumber).toBe(0);
    expect(result.bannerHeight).toBe('0px');
  });

  it('given a trial license should reserve the top banner height', () => {
    const result = computeBannerSettings(
      buildSettings({ platform_license: { license_type: LICENSE_OPTION_TRIAL } as PlatformSettings['platform_license'] }),
    );

    expect(result.bannerHeightNumber).toBe(TOP_BANNER_HEIGHT);
    expect(result.bannerHeight).toBe(`${TOP_BANNER_HEIGHT}px`);
  });

  it('given a demo instance should reserve the top banner height', () => {
    const result = computeBannerSettings(buildSettings({ platform_base_url: DEMO_PLATFORM_URL }));

    expect(result.bannerHeightNumber).toBe(TOP_BANNER_HEIGHT);
  });

  it('given system banner messages should reserve per-message height plus padding', () => {
    const result = computeBannerSettings(
      buildSettings({
        platform_banner_by_level: {
          info: ['a', 'b'],
          warn: ['c'],
        } as unknown as PlatformSettings['platform_banner_by_level'],
      }),
    );

    expect(result.bannerHeightNumber).toBe((SYSTEM_BANNER_HEIGHT_PER_MESSAGE * 3) + SYSTEM_BANNER_VERTICAL_PADDING);
  });

  it('given both system messages and a trial license should sum both heights', () => {
    const result = computeBannerSettings(
      buildSettings({
        platform_license: { license_type: LICENSE_OPTION_TRIAL } as PlatformSettings['platform_license'],
        platform_banner_by_level: { info: ['a'] } as unknown as PlatformSettings['platform_banner_by_level'],
      }),
    );

    expect(result.bannerHeightNumber).toBe(
      SYSTEM_BANNER_HEIGHT_PER_MESSAGE + SYSTEM_BANNER_VERTICAL_PADDING + TOP_BANNER_HEIGHT,
    );
  });

  it('given banner levels with only empty messages should reserve no system height', () => {
    const result = computeBannerSettings(
      buildSettings({
        platform_banner_by_level: {
          info: [],
          warn: [],
        } as unknown as PlatformSettings['platform_banner_by_level'],
      }),
    );

    expect(result.bannerHeightNumber).toBe(0);
  });
});
