import { useEffect } from 'react';

import { isCurrentPlatformRoute } from './url-helper';

/**
 * Forces a full page reload when the browser navigates across
 * the tenant ↔ platform boundary.
 *
 * Listens to:
 * - `popstate` — browser back/forward buttons
 * - `pushState` / `replaceState` — SPA navigation (react-router)
 */
const usePlatformBoundaryGuard = (isPlatform: boolean) => {
  useEffect(() => {
    const checkBoundary = () => {
      if (isCurrentPlatformRoute() !== isPlatform) {
        window.location.reload();
      }
    };

    // Patch pushState / replaceState to detect SPA navigation
    const originalPushState = history.pushState.bind(history);
    const originalReplaceState = history.replaceState.bind(history);

    history.pushState = (...args) => {
      originalPushState(...args);
      checkBoundary();
    };
    history.replaceState = (...args) => {
      originalReplaceState(...args);
      checkBoundary();
    };

    window.addEventListener('popstate', checkBoundary);

    return () => {
      window.removeEventListener('popstate', checkBoundary);
      history.pushState = originalPushState;
      history.replaceState = originalReplaceState;
    };
  }, [isPlatform]);
};

export default usePlatformBoundaryGuard;
