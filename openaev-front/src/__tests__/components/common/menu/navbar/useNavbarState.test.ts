import { describe, expect, it } from 'vitest';

import { NAV_AUTO_COLLAPSE_BREAKPOINT, resolveInitialNavOpen } from '../../../../../components/common/menu/navbar/useNavbarState';

describe('resolveInitialNavOpen', () => {
  it('honours an explicit stored preference over the viewport', () => {
    // A user who collapsed the navigation on a wide screen keeps it collapsed.
    expect(resolveInitialNavOpen('false', 1920)).toBe(false);
    // A user who expanded it on a narrow screen keeps it expanded.
    expect(resolveInitialNavOpen('true', 375)).toBe(true);
  });

  it('starts collapsed below the breakpoint when no preference is stored', () => {
    expect(resolveInitialNavOpen(null, NAV_AUTO_COLLAPSE_BREAKPOINT - 1)).toBe(false);
    expect(resolveInitialNavOpen(null, 375)).toBe(false);
  });

  it('starts expanded at and above the breakpoint when no preference is stored', () => {
    expect(resolveInitialNavOpen(null, NAV_AUTO_COLLAPSE_BREAKPOINT)).toBe(true);
    expect(resolveInitialNavOpen(null, 1920)).toBe(true);
  });

  it('treats any unrecognised stored value as no preference', () => {
    // Guards against a legacy or corrupted localStorage entry silently
    // pinning the navigation open on small screens.
    expect(resolveInitialNavOpen('', 375)).toBe(false);
    expect(resolveInitialNavOpen('yes', 375)).toBe(false);
    expect(resolveInitialNavOpen('yes', 1920)).toBe(true);
  });
});
