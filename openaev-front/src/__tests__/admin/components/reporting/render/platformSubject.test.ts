import { describe, expect, it } from 'vitest';

import { platformSubjectState, resolvePlatformName } from '../../../../../admin/components/reporting/render/useReportingRenderData';

// #8168: a PLATFORM report showed the generic "Platform" label instead of the platform name in
// the PDF and in the preview remounted after a generation or a tab change.
describe('PLATFORM report subject', () => {
  it('prefers the tenant platform name over the platform one', () => {
    expect(resolvePlatformName({ platform_name: 'Tenant name' }, { platform_name: 'Platform name' })).toBe('Tenant name');
  });

  it('falls back to the platform name when the tenant settings are not loaded', () => {
    expect(resolvePlatformName(undefined, { platform_name: 'Platform name' })).toBe('Platform name');
    expect(resolvePlatformName({}, { platform_name: 'Platform name' })).toBe('Platform name');
  });

  it('knows no name before any settings are loaded', () => {
    expect(resolvePlatformName(undefined, undefined)).toBeUndefined();
    expect(resolvePlatformName({ platform_name: '' }, {})).toBeUndefined();
  });

  it('stays loading until the name is known, so a capture never prints a placeholder', () => {
    expect(platformSubjectState(undefined)).toEqual({ status: 'loading' });
  });

  it('uses the fallback label only when no name is known', () => {
    expect(platformSubjectState(undefined, 'Platform')).toEqual({
      status: 'success',
      data: {
        name: 'Platform',
        raw: {},
      },
    });
    expect(platformSubjectState('ACME OpenAEV', 'Platform').data?.name).toBe('ACME OpenAEV');
  });

  it('carries the real name once the settings are loaded', () => {
    expect(platformSubjectState('ACME OpenAEV')).toEqual({
      status: 'success',
      data: {
        name: 'ACME OpenAEV',
        raw: {},
      },
    });
  });
});
