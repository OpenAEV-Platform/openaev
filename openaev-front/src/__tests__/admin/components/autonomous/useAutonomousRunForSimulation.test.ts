import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import useAutonomousRunForSimulation, { DISCOVERY_POLL_MS, DISCOVERY_RETRY_MS, MAX_DISCOVERY_NOT_FOUND, useAutonomousRunForScenario } from '../../../../admin/components/autonomous/useAutonomousRunForSimulation';

const mocks = vi.hoisted(() => ({
  fetchAutonomousRunBySimulation: vi.fn(),
  fetchAutonomousRunByScenario: vi.fn(),
}));

vi.mock('../../../../actions/autonomous/autonomous-actions', () => ({
  fetchAutonomousRunBySimulation: mocks.fetchAutonomousRunBySimulation,
  fetchAutonomousRunByScenario: mocks.fetchAutonomousRunByScenario,
}));

// Detection is EE- and XTM-One-gated; the streak logic under test only runs when eligible.
vi.mock('../../../../utils/hooks/useAuth', () => ({ default: () => ({ settings: { platform_xtm_one_configured: true } }) }));

vi.mock('../../../../utils/hooks/useEnterpriseEdition', () => ({ default: () => ({ isValidated: true }) }));

const RUN = { autonomous_run_id: 'run-1' };
const notFound = () => ({ response: { status: 404 } });
const serverError = () => ({ response: { status: 500 } });

const flush = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
};
const tick = async (ms = DISCOVERY_POLL_MS) => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};
// Sequential probe/settle cycles: each tick must fully flush (probe fired, rejection handled,
// state committed) before the next timer advance, so the ticks cannot be collapsed or parallel.
const tickTimes = async (times: number, ms: number) => {
  for (let i = 0; i < times; i += 1) {
    // eslint-disable-next-line no-await-in-loop -- ordered cycles, see above
    await tick(ms);
  }
};

describe('useAutonomousRunForSimulation (404-streak run detection)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // The session-sticky "detected autonomous" hint lives in sessionStorage, which persists across
    // tests in jsdom - clear it so a run detected in one test never leaks into the next.
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('given_aTransient404OnReload_should_keepProbingAndReattachTheRun', async () => {
    // Arrange: the post-reload reconcile window answers 404 once, then the run is queryable again.
    mocks.fetchAutonomousRunBySimulation
      .mockRejectedValueOnce(notFound())
      .mockResolvedValue({ data: RUN });

    // Act: first probe misses.
    const { result } = renderHook(() => useAutonomousRunForSimulation('sim-1'));
    await flush();

    // Assert: one transient miss must NOT latch manual - detection is resolved (no loader churn)
    // but the discovery poll stays alive and the next probe re-attaches the cockpit.
    expect(result.current.resolved).toBe(true);
    expect(result.current.run).toBeNull();
    await tick();
    expect(result.current.run).toEqual(RUN);
  });

  it('given_anUnbrokenStreakOf404s_should_concludeManualAndStopProbing', async () => {
    // Arrange: a genuinely manual simulation answers 404 forever.
    mocks.fetchAutonomousRunBySimulation.mockRejectedValue(notFound());

    // Act: the mount probe plus poll re-probes until the streak threshold is reached.
    const { result } = renderHook(() => useAutonomousRunForSimulation('sim-1'));
    await flush();
    await tickTimes(MAX_DISCOVERY_NOT_FOUND - 1, DISCOVERY_POLL_MS);

    // Assert: the manual verdict is latched after exactly the streak threshold...
    expect(result.current.run).toBeNull();
    expect(mocks.fetchAutonomousRunBySimulation).toHaveBeenCalledTimes(MAX_DISCOVERY_NOT_FOUND);

    // ...and the discovery poll STOPS - a manual entity is never re-probed forever.
    await tick(DISCOVERY_POLL_MS * 3);
    expect(mocks.fetchAutonomousRunBySimulation).toHaveBeenCalledTimes(MAX_DISCOVERY_NOT_FOUND);
  });

  it('given_aStreakBrokenByATransientError_should_notConcludeManualAndStillRecover', async () => {
    // Arrange: two misses, a 5xx blip (which must RESET the streak), two more misses - never an
    // unbroken streak of MAX_DISCOVERY_NOT_FOUND - and then the run becomes queryable.
    mocks.fetchAutonomousRunBySimulation
      .mockRejectedValueOnce(notFound())
      .mockRejectedValueOnce(notFound())
      .mockRejectedValueOnce(serverError())
      .mockRejectedValueOnce(notFound())
      .mockRejectedValueOnce(notFound())
      .mockResolvedValue({ data: RUN });

    // Act
    const { result } = renderHook(() => useAutonomousRunForSimulation('sim-1'));
    await flush();
    await tickTimes(5, DISCOVERY_POLL_MS);

    // Assert: mixed transient errors never accumulated into a false manual verdict - the poll was
    // still alive on the sixth probe and the cockpit attached.
    expect(mocks.fetchAutonomousRunBySimulation).toHaveBeenCalledTimes(6);
    expect(result.current.run).toEqual(RUN);
  });

  it('given_aDetectedRun_should_stopTheDiscoveryPoll', async () => {
    // Arrange
    mocks.fetchAutonomousRunBySimulation.mockResolvedValue({ data: RUN });

    // Act
    const { result } = renderHook(() => useAutonomousRunForSimulation('sim-1'));
    await flush();

    // Assert: once detected, the header/panel keep the run fresh - discovery never re-probes.
    expect(result.current.run).toEqual(RUN);
    await tick(DISCOVERY_POLL_MS * 3);
    expect(mocks.fetchAutonomousRunBySimulation).toHaveBeenCalledTimes(1);
  });

  it('given_aDeclaredAutonomousEntity_should_stayPendingAcrossATransient404AndFastRetry', async () => {
    // Arrange: the scenario carries the scenario_autonomous marker; the reload race answers 404
    // once, then the run is queryable again.
    mocks.fetchAutonomousRunByScenario
      .mockRejectedValueOnce(notFound())
      .mockResolvedValue({ data: RUN });

    // Act: first probe misses.
    const { result } = renderHook(() => useAutonomousRunForScenario('scenario-1', true));
    await flush();

    // Assert: a declared-autonomous entity must NOT resolve to the manual view on a transient miss
    // - it stays pending (Loader, never a manual flash) and the FAST retry attaches the cockpit
    // well before the discovery poll would have.
    expect(result.current.resolved).toBe(false);
    expect(result.current.run).toBeNull();
    await tick(DISCOVERY_RETRY_MS);
    expect(result.current.run).toEqual(RUN);
    expect(result.current.resolved).toBe(true);
  });

  it('given_aDeclaredAutonomousEntityWithATornDownRun_should_settleManualAfterTheFastStreak', async () => {
    // Arrange: the durable marker outlives the run row, so every probe answers 404.
    mocks.fetchAutonomousRunByScenario.mockRejectedValue(notFound());

    // Act: mount probe + fast retries until the streak threshold.
    const { result } = renderHook(() => useAutonomousRunForScenario('scenario-1', true));
    await flush();
    await tickTimes(MAX_DISCOVERY_NOT_FOUND - 1, DISCOVERY_RETRY_MS);

    // Assert: the pending window is bounded - the full streak latches manual, detection resolves,
    // and probing stops for good.
    expect(result.current.resolved).toBe(true);
    expect(result.current.run).toBeNull();
    expect(mocks.fetchAutonomousRunByScenario).toHaveBeenCalledTimes(MAX_DISCOVERY_NOT_FOUND);
    await tick(DISCOVERY_POLL_MS * 3);
    expect(mocks.fetchAutonomousRunByScenario).toHaveBeenCalledTimes(MAX_DISCOVERY_NOT_FOUND);
  });

  it('given_aStickyAutonomousEntityWithoutAMarker_should_stayPendingAcrossATransient404', async () => {
    // Arrange: a first mount detects a run for a LEGACY simulation (no exercise_autonomous marker),
    // which marks the id sticky for the session. A reload (fresh mount, still no marker) then hits
    // the post-reload transient 404 before the run is queryable again.
    mocks.fetchAutonomousRunBySimulation
      .mockResolvedValueOnce({ data: RUN })
      .mockRejectedValueOnce(notFound())
      .mockResolvedValue({ data: RUN });

    // Act: first mount detects the run and records the sticky hint, then unmounts (reload).
    const first = renderHook(() => useAutonomousRunForSimulation('sim-legacy'));
    await flush();
    expect(first.result.current.run).toEqual(RUN);
    first.unmount();

    const { result } = renderHook(() => useAutonomousRunForSimulation('sim-legacy'));
    await flush();

    // Assert: the sticky hint keeps the reload PENDING (no manual flash) despite the falsy marker
    // and the transient 404, and the FAST retry re-attaches before the discovery poll would have.
    expect(result.current.resolved).toBe(false);
    expect(result.current.run).toBeNull();
    await tick(DISCOVERY_RETRY_MS);
    expect(result.current.run).toEqual(RUN);
  });

  it('given_aStickyEntityStrandedByALongBlip_should_selfHealWithoutAReload', async () => {
    // Arrange: a first mount detects the run (sticky), then a blip long enough to exhaust the
    // not-found streak (which latches manual), and finally the run is queryable again.
    mocks.fetchAutonomousRunBySimulation
      .mockResolvedValueOnce({ data: RUN })
      .mockRejectedValue(notFound());

    const first = renderHook(() => useAutonomousRunForSimulation('sim-sticky'));
    await flush();
    expect(first.result.current.run).toEqual(RUN);
    first.unmount();

    // Reload straight into the blip: every probe 404s past the fast streak, so manual latches.
    const { result } = renderHook(() => useAutonomousRunForSimulation('sim-sticky'));
    await flush();
    await tickTimes(MAX_DISCOVERY_NOT_FOUND, DISCOVERY_RETRY_MS);
    expect(result.current.run).toBeNull();

    // The blip clears: a sticky entity keeps re-probing on the discovery cadence even after the
    // manual latch, so the cockpit self-heals without a full reload.
    mocks.fetchAutonomousRunBySimulation.mockResolvedValue({ data: RUN });
    await tick(DISCOVERY_POLL_MS * 2);
    expect(result.current.run).toEqual(RUN);
  });
});
