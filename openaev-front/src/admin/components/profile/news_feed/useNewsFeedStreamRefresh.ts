import { useEffect, useRef } from 'react';

import { subscribeStreamEvent } from '../../../../utils/hooks/useDataLoader';
import { NEWS_FEED_STREAM_BATCH_MS, parseNewsFeedItemStreamEvent } from '../../nav/newsFeedStream';

/**
 * Calls `onChange` when the stream tells that a news feed item of the user changed, whatever the change
 * (created, updated or deleted by the collector, or marked read), and when the stream reconnects, since the
 * events missed meanwhile are not replayed. The events of a burst are folded into one call.
 */
const useNewsFeedStreamRefresh = (onChange: () => void) => {
  // The latest callback, so that the subscription is not renewed at each render.
  const latestOnChange = useRef(onChange);
  latestOnChange.current = onChange;

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    let lastRefreshAt = Date.now();
    const refresh = () => {
      lastRefreshAt = Date.now();
      latestOnChange.current();
    };
    const unsubscribe = subscribeStreamEvent('message', (event: MessageEvent) => {
      if (parseNewsFeedItemStreamEvent(event) === null || timer !== undefined) {
        return;
      }
      timer = setTimeout(() => {
        timer = undefined;
        refresh();
      }, NEWS_FEED_STREAM_BATCH_MS);
    });
    // The first connection may open right after the load of the mount, which is fresh enough: only a later
    // open is a reconnection.
    const unsubscribeOpen = subscribeStreamEvent('open', () => {
      if (Date.now() - lastRefreshAt >= NEWS_FEED_STREAM_BATCH_MS) {
        refresh();
      }
    });
    return () => {
      unsubscribe();
      unsubscribeOpen();
      clearTimeout(timer);
    };
  }, []);
};

export default useNewsFeedStreamRefresh;
