import { useCallback, useState } from 'react';

import { MESSAGING$ } from '../../../../utils/Environment';

export const NAV_OPEN_STORAGE_KEY = 'navOpen';

// Below this width the navigation starts collapsed rather than amputating
// ~180px of content on small screens. A stored preference always wins.
export const NAV_AUTO_COLLAPSE_BREAKPOINT = 1024;

// Exported and pure so that every consumer of this state resolves it
// identically whatever order they mount in — the top bar offsets itself by the
// navigation width and must not disagree with the navigation itself.
export const resolveInitialNavOpen = (
  storedPreference: string | null,
  viewportWidth: number,
): boolean => {
  if (storedPreference === 'true') return true;
  if (storedPreference === 'false') return false;
  return viewportWidth >= NAV_AUTO_COLLAPSE_BREAKPOINT;
};

export const readNavOpen = (): boolean => resolveInitialNavOpen(
  localStorage.getItem(NAV_OPEN_STORAGE_KEY),
  window.innerWidth,
);

const useNavbarState = (): {
  navOpen: boolean;
  toggleNav: () => void;
} => {
  const [navOpen, setNavOpen] = useState(readNavOpen);

  // The updater must stay pure (StrictMode double-invokes it), so the
  // persistence and the broadcast happen here, not inside setNavOpen.
  const toggleNav = useCallback(() => {
    const next = !navOpen;
    localStorage.setItem(NAV_OPEN_STORAGE_KEY, String(next));
    setNavOpen(next);
    MESSAGING$.toggleNav.next();
  }, [navOpen]);

  return {
    navOpen,
    toggleNav,
  };
};

export default useNavbarState;
