import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, type ReactNode, Suspense, useEffect, useRef, useState } from 'react';

import connectStreamToLiveRouter from '../stream/connectStreamToLiveRouter';
import { createLiveRouter } from './liveRouter';
import { LiveRouterContext } from './liveRouterContext';
import { createAppQueryClient } from './queryClient';
import { rightsKey, rightsShrank, type SessionRights } from './sessionRights';

// Dev-only: the DEV guard removes this import from production builds.
const ReactQueryDevtools = import.meta.env.DEV
  ? lazy(() => import('@tanstack/react-query-devtools').then(module => ({ default: module.ReactQueryDevtools })))
  : null;

interface Props {
  children: ReactNode;
  /** The logged-in user's rights; none on anonymous pages. */
  rights?: SessionRights;
}

/**
 * One query cache per mount, with its live router. `Root` mounts one for anonymous pages and one per
 * logged-in user and tenant (keyed by them), so a session that ends or changes drops the cache. A
 * change of rights keeps the mounted tree: lost rights reset every query, new ones refetch them.
 */
const AppQueryProvider = ({ children, rights }: Props) => {
  const [queryClient] = useState(createAppQueryClient);
  const [router] = useState(() => createLiveRouter(queryClient));

  const currentRightsKey = rights ? rightsKey(rights) : '';
  const previousRights = useRef(rights);
  useEffect(() => {
    const previous = previousRights.current;
    previousRights.current = rights;
    if (!previous || !rights || rightsKey(previous) === currentRightsKey) return;
    if (rightsShrank(previous, rights)) {
      router.resetAll();
    } else {
      router.invalidateAll();
    }
    // `rights` is a new object on every render: only its key says whether it changed.
  }, [router, currentRightsKey]);

  const pendingClear = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => {
    clearTimeout(pendingClear.current);
    const disconnect = connectStreamToLiveRouter(router);
    return () => {
      disconnect();
      router.dispose();
      // The session ended: drop its data, which also cancels its fetches. Deferred, so that
      // StrictMode's simulated unmount, followed at once by a remount, keeps the cache.
      pendingClear.current = setTimeout(() => queryClient.clear(), 0);
    };
  }, [queryClient, router]);

  return (
    <QueryClientProvider client={queryClient}>
      <LiveRouterContext.Provider value={router}>
        {children}
      </LiveRouterContext.Provider>
      {ReactQueryDevtools && (
        <Suspense fallback={null}>
          <ReactQueryDevtools initialIsOpen={false} />
        </Suspense>
      )}
    </QueryClientProvider>
  );
};

export default AppQueryProvider;
