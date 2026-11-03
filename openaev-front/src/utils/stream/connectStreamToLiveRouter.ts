import { type LiveRouter } from '../query/liveRouter';
import { onBulkOperationEnd, onConnectionOpen, onEntityChange } from './streamClient';

/**
 * Feeds the platform stream into the live router: the only place that knows where changes come from.
 * Returns the unsubscribe.
 */
const connectStreamToLiveRouter = (router: LiveRouter): (() => void) => {
  const unsubscribes = [
    onEntityChange(change => router.applyChanges([change])),
    // A bulk operation streams no event per entity: everything it may have changed is refreshed.
    onBulkOperationEnd(() => router.invalidateAllLive()),
    // Any data read before the server registered this connection may have missed changes: the
    // queries loaded while the stream was connecting, or during an outage. Queries still fetching
    // refetch once their fetch settles.
    onConnectionOpen(() => router.invalidateAllLive()),
  ];
  return () => unsubscribes.forEach(unsubscribe => unsubscribe());
};

export default connectStreamToLiveRouter;
