import { describe, expect, it } from 'vitest';

import { evaluateWatchdogTick, THROTTLE_GAP_FACTOR } from '../../../utils/sse/watchdogPolicy';

const TICK = 1500;
const PING_MAX = 5000;

const base = {
  tickInterval: TICK,
  pingMaxTime: PING_MAX,
};

describe('evaluateWatchdogTick', () => {
  it('skips while the tab is hidden, even with a stale ping clock', () => {
    expect(evaluateWatchdogTick({
      ...base,
      now: 600_000,
      lastPingDate: 0,
      lastTickAt: 600_000 - TICK,
      hidden: true,
    })).toBe('skip');
  });

  it('resets the ping clock after a timer-throttling gap instead of reconnecting', () => {
    // The previous tick is 10 minutes old (background throttling / OS sleep):
    // the stale lastPingDate proves nothing about connection health.
    expect(evaluateWatchdogTick({
      ...base,
      now: 600_000,
      lastPingDate: 0,
      lastTickAt: 0,
      hidden: false,
    })).toBe('reset-ping-clock');
  });

  it('reconnects when pings are genuinely absent while visible and ticking normally', () => {
    expect(evaluateWatchdogTick({
      ...base,
      now: 10_000,
      lastPingDate: 10_000 - PING_MAX - 1,
      lastTickAt: 10_000 - TICK,
      hidden: false,
    })).toBe('reconnect');
  });

  it('skips when pings are fresh', () => {
    expect(evaluateWatchdogTick({
      ...base,
      now: 10_000,
      lastPingDate: 9_500,
      lastTickAt: 10_000 - TICK,
      hidden: false,
    })).toBe('skip');
  });

  it('treats a tick gap just under the throttling threshold as a normal tick', () => {
    const now = 100_000;
    expect(evaluateWatchdogTick({
      ...base,
      now,
      lastPingDate: now - PING_MAX - 1,
      lastTickAt: now - TICK * THROTTLE_GAP_FACTOR,
      hidden: false,
    })).toBe('reconnect');
  });
});
