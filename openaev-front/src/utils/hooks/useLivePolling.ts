import { useEffect, useRef } from 'react';

interface UseLivePollingOptions {
  /** Turn the cadence on/off (e.g. only while a workflow id is known). Defaults to on. */
  enabled?: boolean;
  /** Cadence in ms. Matches the attack-path live cadence (3 s) by default. */
  intervalMs?: number;
}

/**
 * Runs `onTick` on a fixed cadence while the tab is visible, so a view that reads a REST resource
 * once on mount can stay live during a run (the AI authoring steps / scope) without the user having
 * to reload the tab. Paused while the tab is hidden and given one immediate catch-up read when it
 * comes back, mirroring the attack-path live graph so every real-time surface behaves the same.
 *
 * The callback is held in a ref so the timer is not torn down and rebuilt whenever the caller passes
 * a fresh closure; only `enabled` / `intervalMs` restart it.
 */
const useLivePolling = (onTick: () => void, { enabled = true, intervalMs = 3000 }: UseLivePollingOptions = {}) => {
  const savedTick = useRef(onTick);
  savedTick.current = onTick;

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    let timer: number | undefined;
    const start = () => {
      if (timer === undefined) {
        timer = window.setInterval(() => savedTick.current(), intervalMs);
      }
    };
    const stop = () => {
      if (timer !== undefined) {
        window.clearInterval(timer);
        timer = undefined;
      }
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        stop();
      } else {
        // Immediate catch-up so a returning tab is up to date before the next tick.
        savedTick.current();
        start();
      }
    };
    if (document.visibilityState !== 'hidden') {
      start();
    }
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [enabled, intervalMs]);
};

export default useLivePolling;
