export type WatchdogDecision = 'skip' | 'reset-ping-clock' | 'reconnect';

export interface WatchdogTickInput {
  now: number;
  lastPingDate: number;
  lastTickAt: number;
  hidden: boolean;
  tickInterval: number;
  pingMaxTime: number;
}

/**
 * A tick arriving later than `tickInterval * THROTTLE_GAP_FACTOR` after the
 * previous one means the browser throttled the timer (background tab) or the
 * OS slept - not that the connection is dead.
 */
export const THROTTLE_GAP_FACTOR = 4;

/**
 * Decides what the SSE ping watchdog should do on a given tick.
 *
 * The naive check (`now - lastPingDate > pingMaxTime` -> reconnect) false
 * positives after a background period: browsers throttle timers in hidden
 * tabs, so `lastPingDate` bookkeeping lags even when the connection is
 * perfectly healthy. On refocus the first tick would then close and reopen the
 * EventSource, and the `open` handler refetches every mounted loader - a
 * refetch storm on every tab switch. Instead:
 *
 * - while hidden, do nothing (a reconnect verdict is meaningless there);
 * - after a detected throttling gap, reset the ping clock and let the next
 *   regular ticks re-evaluate with fresh foreground data;
 * - only reconnect when pings are genuinely absent while the tab is visible
 *   and ticking normally.
 */
export const evaluateWatchdogTick = ({
  now,
  lastPingDate,
  lastTickAt,
  hidden,
  tickInterval,
  pingMaxTime,
}: WatchdogTickInput): WatchdogDecision => {
  if (hidden) {
    return 'skip';
  }
  if (now - lastTickAt > tickInterval * THROTTLE_GAP_FACTOR) {
    return 'reset-ping-clock';
  }
  if (now - lastPingDate > pingMaxTime) {
    return 'reconnect';
  }
  return 'skip';
};
