import { useEffect, useRef } from 'react';

import { fetchMe } from '../../actions/Application';
import { MESSAGING$ } from '../Environment';
import { useAppDispatch } from '../hooks';

export const REFRESH_ME_THROTTLE_MS = 60_000;

// Reloads the user's capabilities on focus or after a refused write, at most once per window
const useRefreshMe = (enabled: boolean) => {
  const dispatch = useAppDispatch();
  const lastRefresh = useRef(Date.now());

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    const refresh = () => {
      const now = Date.now();
      if (now - lastRefresh.current < REFRESH_ME_THROTTLE_MS) {
        return;
      }
      lastRefresh.current = now;
      dispatch(fetchMe());
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        refresh();
      }
    };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', onVisibilityChange);
    const subscription = MESSAGING$.refreshMe.subscribe(refresh);
    return () => {
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      subscription.unsubscribe();
    };
  }, [enabled]);
};

export default useRefreshMe;
