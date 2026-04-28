import { useCallback } from 'react';
import { Link } from 'react-router';

import { APP_BASE_PATH } from '../../../../utils/Environment';
import { useIsCurrentPlatformRoute } from '../../../../utils/platformContext';
import {
  buildTenantUrl,
  getCurrentTenantId,
  isPlatformRoute,
} from '../../../../utils/url-helper';

/**
 * Determines whether navigating to `targetPath` requires leaving
 * the current BrowserRouter (tenant ↔ platform boundary).
 *
 * Returns the full-reload URL when a boundary crossing is needed,
 * or null when react-router can handle it normally.
 */
const getFullReloadUrl = (targetPath: string, currentIsPlatform: boolean): string | null => {
  const targetIsPlatform = isPlatformRoute(targetPath);
  if (targetIsPlatform && !currentIsPlatform) {
    return `${APP_BASE_PATH}${targetPath}`;
  }
  if (!targetIsPlatform && currentIsPlatform) {
    const tenantId = getCurrentTenantId();
    return buildTenantUrl(tenantId, targetPath, '', '');
  }
  return null;
};

/**
 * Hook that returns a function to resolve menu-link props.
 * Reads the current platform mode from context so callers
 * don't need to pass `isPlatform` themselves.
 *
 * The returned `resolveMenuLink(path)` produces props to spread
 * on a MUI `<MenuItem>` — either react-router `<Link>` (SPA)
 * or a full page reload (tenant ↔ platform boundary crossing).
 */
const useResolveMenuLink = () => {
  const isPlatform = useIsCurrentPlatformRoute();

  return useCallback((path: string) => {
    const reloadUrl = getFullReloadUrl(path, isPlatform);
    if (reloadUrl) {
      return {
        onClick: () => {
          globalThis.location.href = reloadUrl;
        },
      };
    }
    return {
      component: Link,
      to: path,
    };
  }, [isPlatform]);
};

export default useResolveMenuLink;
