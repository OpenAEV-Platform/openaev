import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, type ReactNode, Suspense, useEffect, useRef, useState } from 'react';

import { onBulkOperationEnd, onConnectionLost, onConnectionOpen, onEntityChange } from '../stream/streamClient';
import bulkEntityTypes from './bulkOperationEntities';
import { createLiveRouter } from './liveRouter';
import { LiveRouterContext } from './liveRouterContext';
import { createAppQueryClient } from './queryClient';

// Dev-only: the DEV guard removes this import from production builds.
const ReactQueryDevtools = import.meta.env.DEV
  ? lazy(() => import('@tanstack/react-query-devtools').then(module => ({ default: module.ReactQueryDevtools })))
  : null;

interface Props {
  children: ReactNode;
  /** Changes when the user's rights change: every cached query is then refetched. */
  rightsKey: string;
}

/**
 * One query cache per mount. `Root` mounts it only for a logged-in user, keyed by that user and
 * tenant, so a session that ends or changes drops the cache. A change of rights keeps the cache (and
 * the mounted tree) but refetches every query. It also routes the stream's entity changes to the
 * live queries.
 */
const AppQueryProvider = ({ children, rightsKey }: Props) => {
  const [queryClient] = useState(createAppQueryClient);
  const [router] = useState(() => createLiveRouter(queryClient));

  const previousRightsKey = useRef(rightsKey);
  useEffect(() => {
    if (previousRightsKey.current === rightsKey) return;
    previousRightsKey.current = rightsKey;
    router.invalidateAll();
  }, [router, rightsKey]);

  useEffect(() => {
    const unsubscribes = [
      onEntityChange(change => router.applyChanges([change])),
      onBulkOperationEnd(entity => router.invalidateType(bulkEntityTypes(entity))),
      // Any data read before the server registered this connection may have missed changes: the
      // queries loaded while the stream was connecting, or during an outage. Queries still fetching
      // refetch once their fetch settles.
      onConnectionOpen(() => router.invalidateAllLive()),
      // Marking stale without a request re-arms the fallback polling: while the platform is down,
      // fetching right away would only fail.
      onConnectionLost(() => router.markAllLiveStale()),
    ];
    return () => {
      unsubscribes.forEach(unsubscribe => unsubscribe());
      router.dispose();
    };
  }, [router]);

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
