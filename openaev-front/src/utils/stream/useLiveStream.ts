import { useLayoutEffect } from 'react';

import { startStream, stopStream } from './streamClient';

/**
 * Keeps the platform stream open while the calling application layout (admin, private) is mounted.
 * Standalone pages (report rendering, comcheck, channels, challenges, lessons...) mount no layout,
 * so they stay without a stream: an open connection keeps the network busy, and the headless report
 * renderer waits for an idle network before capturing. A user or tenant change remounts the layout
 * (the query provider is keyed by them), which opens a stream bound to the new session.
 *
 * A layout effect: it runs before the passive effects of the layout and its children, so the data
 * loaders mounting with it already see the stream started (see useDataLoader).
 */
const useLiveStream = (): void => {
  useLayoutEffect(() => {
    startStream();
    return stopStream;
  }, []);
};

export default useLiveStream;
