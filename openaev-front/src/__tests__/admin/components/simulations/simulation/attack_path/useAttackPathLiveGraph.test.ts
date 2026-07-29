import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import useAttackPathLiveGraph, { ATTACK_PATH_VERSION_EVENT, BATCH_TTL_MS, DELTA_POLL_MS, DELTA_SAFETY_NET_MS, NUDGE_DEBOUNCE_MS } from '../../../../../../admin/components/simulations/simulation/attack_path/useAttackPathLiveGraph';

const mocks = vi.hoisted(() => ({
  fetchAttackPathGraph: vi.fn(),
  fetchAttackPathGraphDelta: vi.fn(),
  // The shared stream, reduced to what the hook uses: a subscription and a health verdict. The
  // handler the hook registers is captured so a test can push a nudge through it.
  streamHandlers: new Map<string, (event: MessageEvent) => void>(),
  streamHealthy: false,
  unsubscribed: 0,
}));

vi.mock('../../../../../../actions/attack-path/attack-path-actions', () => ({
  fetchAttackPathGraph: mocks.fetchAttackPathGraph,
  fetchAttackPathGraphDelta: mocks.fetchAttackPathGraphDelta,
}));

vi.mock('../../../../../../utils/hooks/useDataLoader', () => ({
  default: vi.fn(),
  subscribeStreamEvent: (type: string, handler: (event: MessageEvent) => void) => {
    mocks.streamHandlers.set(type, handler);
    return () => {
      mocks.streamHandlers.delete(type);
      mocks.unsubscribed += 1;
    };
  },
  isStreamHealthy: () => mocks.streamHealthy,
}));

/** Pushes a nudge for the given simulation through the hook's stream subscription. */
const pushNudge = async (simulationId: string, version = 99) => {
  const handler = mocks.streamHandlers.get(ATTACK_PATH_VERSION_EVENT);
  await act(async () => {
    handler?.({
      data: JSON.stringify({
        simulation_id: simulationId,
        version,
      }),
    } as MessageEvent);
    await vi.advanceTimersByTimeAsync(NUDGE_DEBOUNCE_MS);
  });
};

const INJECTOR = 'NODE_INJECTOR|nmap';
const HOST_X = 'NODE_ENDPOINT|host-x';
const HOST_Y = 'NODE_ENDPOINT|host-y';

// The collapsed snapshot, carrying the graph version it reflects (the cursor's seed).
const collapsedSnapshot = (graphVersion: number) => ({
  mode: 'collapsed',
  graphVersion,
  attackPathNodes: [{
    id: INJECTOR,
    type: 'INJECTOR',
    label: 'nmap',
  }, {
    id: HOST_X,
    type: 'ASSET',
    ref: 'host-x',
    label: 'CORP-X',
    findingCounts: {},
  }],
  attackPathEdges: [{
    edgeId: 'edge-x',
    edgeSourceId: INJECTOR,
    edgeTargetId: HOST_X,
    type: 'EDGE_EXECUTIONS',
    count: 1,
  }],
  attackPathExecutions: [],
  staticAttackPathFindings: [],
});

// The full read adds the execution feed the causal layout needs; same ids, extra kinds.
const fullSnapshot = (graphVersion: number) => ({
  ...collapsedSnapshot(graphVersion),
  mode: 'full',
  attackPathExecutions: [{
    id: 'NODE_EXECUTION|exec-1|host-x|agent-1',
    ref: 'exec-1',
    type: 'EXECUTION',
    status: 'RED',
  }],
});

const emptyDelta = (since: number, newVersion = since) => ({
  data: {
    sinceVersion: since,
    newVersion,
    resyncRequired: false,
    staticAttackPathFindings: [],
    attackPathExecutions: [],
    attackPathNodes: [],
    attackPathEdges: [],
  },
});

const deltaWithHostY = {
  data: {
    sinceVersion: 41,
    newVersion: 42,
    resyncRequired: false,
    staticAttackPathFindings: [],
    attackPathExecutions: [],
    attackPathNodes: [{
      id: HOST_Y,
      type: 'ASSET',
      ref: 'host-y',
      label: 'CORP-Y',
      findingCounts: {},
    }],
    attackPathEdges: [],
  },
};

const resyncDelta = {
  data: {
    sinceVersion: 41,
    newVersion: 99,
    resyncRequired: true,
    staticAttackPathFindings: [],
    attackPathExecutions: [],
    attackPathNodes: [],
    attackPathEdges: [],
  },
};

// A promise whose resolution the test controls, to drive the races (a snapshot still in flight when the
// user deselects; a reseed still in flight when the cadence would have fired).
const deferred = <T>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return {
    promise,
    resolve,
  };
};

const flush = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
};
const tick = async (ms = DELTA_POLL_MS) => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};

const renderLive = (props?: {
  simulationId?: string;
  fullEligible?: boolean;
  terminal?: boolean;
}) =>
  renderHook(
    ({ simulationId, fullEligible, terminal }: {
      simulationId: string;
      fullEligible: boolean;
      terminal: boolean;
    }) => useAttackPathLiveGraph({
      simulationId,
      fullEligible,
      terminal,
    }),
    {
      initialProps: {
        simulationId: props?.simulationId ?? 'sim-1',
        fullEligible: props?.fullEligible ?? false,
        terminal: props?.terminal ?? false,
      },
    },
  );

describe('useAttackPathLiveGraph', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mocks.fetchAttackPathGraph.mockResolvedValue({ data: collapsedSnapshot(41) });
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(emptyDelta(41));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    mocks.streamHandlers.clear();
    mocks.streamHealthy = false;
    mocks.unsubscribed = 0;
  });

  it('given_aSnapshotWithAGraphVersion_should_pollTheDeltaFromThatCursor', async () => {
    // Arrange & Act
    renderLive();
    await flush();
    await tick();

    // Assert: the cursor is seeded from the snapshot, so the first tick asks only for what came after.
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledWith('sim-1', 41);
  });

  it('given_aResync_should_keepTheGraphMountedWithoutRaisingLoading', async () => {
    // Arrange: the first load settles, then the backend declares the cursor unanswerable.
    const { result } = renderLive();
    await flush();
    const seededDto = result.current.dto;
    expect(seededDto).not.toBeNull();
    expect(result.current.loading).toBe(false);

    // The resync's snapshot read is held open, so the whole reseed window is observable.
    const held = deferred<{ data: ReturnType<typeof collapsedSnapshot> }>();
    mocks.fetchAttackPathGraph.mockReturnValueOnce(held.promise);
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(resyncDelta);

    // Act
    await tick();

    // Assert: while the reseed is in flight the previous graph is still rendered and no loader is
    // requested — a resync is silent, it never unmounts the graph or resets the viewport.
    expect(mocks.fetchAttackPathGraph).toHaveBeenCalledTimes(2);
    expect(result.current.loading).toBe(false);
    expect(result.current.dto).toBe(seededDto);

    // And the reseeded snapshot lands without ever having shown a loading state.
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(emptyDelta(50));
    await act(async () => {
      held.resolve({ data: collapsedSnapshot(50) });
      await Promise.resolve();
    });
    await flush();
    expect(result.current.loading).toBe(false);
    expect(result.current.dto).not.toBeNull();
  });

  it('given_aReseedInFlight_should_suspendTheDeltaCadence', async () => {
    // Arrange: a resync whose snapshot read never settles during the test.
    renderLive();
    await flush();
    const held = deferred<{ data: ReturnType<typeof collapsedSnapshot> }>();
    mocks.fetchAttackPathGraph.mockReturnValueOnce(held.promise);
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(resyncDelta);
    await tick();
    const pollsBeforeReseed = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Act: several cadences elapse while the reseed is still pending.
    await tick(DELTA_POLL_MS * 5);

    // Assert: not one tick fired. Polling on the pre-reseed cursor would answer `resyncRequired`
    // again and again — a resync→reseed storm.
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledTimes(pollsBeforeReseed);

    // Once the reseed lands the cadence resumes, from the reseeded cursor.
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(emptyDelta(50));
    await act(async () => {
      held.resolve({ data: collapsedSnapshot(50) });
      await Promise.resolve();
    });
    await flush();
    await tick();
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenLastCalledWith('sim-1', 50);
  });

  it('given_aDeselectWhileASnapshotIsInFlight_should_notRepopulateTheStore', async () => {
    // Arrange: the initial snapshot is still in flight.
    const held = deferred<{ data: ReturnType<typeof collapsedSnapshot> }>();
    mocks.fetchAttackPathGraph.mockReturnValueOnce(held.promise);
    const { result, rerender } = renderLive();
    await flush();
    expect(result.current.dto).toBeNull();

    // Act: the user deselects the simulation, and only then does the previous read answer.
    rerender({
      simulationId: '',
      fullEligible: false,
      terminal: false,
    });
    await act(async () => {
      held.resolve({ data: collapsedSnapshot(41) });
      await Promise.resolve();
    });
    await flush();

    // Assert: the stale response was dropped, so nothing is selected and nothing is rendered.
    expect(result.current.dto).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it('given_aSimulationSwitch_should_showTheLoaderAgain', async () => {
    // Arrange
    const { result, rerender } = renderLive();
    await flush();
    expect(result.current.loading).toBe(false);

    // Act: a genuine switch, whose snapshot is held open.
    const held = deferred<{ data: ReturnType<typeof collapsedSnapshot> }>();
    mocks.fetchAttackPathGraph.mockReturnValueOnce(held.promise);
    rerender({
      simulationId: 'sim-2',
      fullEligible: false,
      terminal: false,
    });
    await flush();

    // Assert: unlike a resync, a run the user picked has nothing to keep on screen — it loads.
    expect(result.current.loading).toBe(true);
    expect(result.current.dto).toBeNull();
    await act(async () => {
      held.resolve({ data: collapsedSnapshot(7) });
      await Promise.resolve();
    });
    await flush();
    expect(result.current.loading).toBe(false);
  });

  it('given_aDeltaBatch_should_clearItOnceTheAffordanceHasPlayed', async () => {
    // Arrange
    const { result } = renderLive();
    await flush();
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(deltaWithHostY);

    // Act
    await tick();

    // Assert: the batch drives the entrance animation and the announcement, with kinds readable from
    // the node itself rather than from its id.
    expect(result.current.newNodeIds).toEqual([HOST_Y]);
    expect(result.current.newNodes.map(n => n.type)).toEqual(['ASSET']);

    // It is then dropped, so a remount cannot replay a stale animation and the live region does not
    // hold on to a stale summary.
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(emptyDelta(42));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(BATCH_TTL_MS);
    });
    expect(result.current.newNodeIds).toEqual([]);
    expect(result.current.newNodes).toEqual([]);
    expect(result.current.changedFindingTypes).toEqual([]);
  });

  it('given_fullEligibleFlippingAfterTheSeed_should_mergeWithoutReSeeding', async () => {
    // Arrange: the size gate resolves from the picker list, which lands after the first read.
    const { result, rerender } = renderLive({ fullEligible: false });
    await flush();
    expect(result.current.fullDto).toBeNull();
    expect(mocks.fetchAttackPathGraph).toHaveBeenCalledTimes(1);
    // A delta applied in the meantime, so the merge is observably non-destructive.
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(deltaWithHostY);
    await tick();
    mocks.fetchAttackPathGraphDelta.mockResolvedValue(emptyDelta(42));

    // Act
    mocks.fetchAttackPathGraph.mockResolvedValueOnce({ data: fullSnapshot(43) });
    rerender({
      simulationId: 'sim-1',
      fullEligible: true,
      terminal: false,
    });
    await flush();

    // Assert: exactly one extra read — the full one — and no second collapsed read.
    expect(result.current.fullDto).not.toBeNull();
    expect(mocks.fetchAttackPathGraph).toHaveBeenCalledTimes(2);
    expect(mocks.fetchAttackPathGraph).toHaveBeenLastCalledWith('sim-1', 'full');
    expect(result.current.fullDto?.attackPathExecutions?.map(e => e.ref)).toEqual(['exec-1']);
    // The delta applied before the flip survived the merge, and the cursor did not go back to zero.
    expect(result.current.dto?.attackPathNodes?.some(n => n.id === HOST_Y)).toBe(true);
    await tick();
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenLastCalledWith('sim-1', 42);
  });

  // -- The stream nudge (spec 003) --

  it('given_aNudgeForThisSimulation_should_fetchTheDeltaImmediately', async () => {
    // Arrange: the view is up and its cadence timer has not fired yet.
    renderLive();
    await flush();
    const callsBefore = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Act
    await pushNudge('sim-1');

    // Assert: the nudge, not the timer, produced the read.
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsBefore + 1);
  });

  it('given_aNudgeForAnotherSimulation_should_beIgnored', async () => {
    // Arrange: the scenario picker means other simulations' nudges reach this client too.
    renderLive();
    await flush();
    const callsBefore = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Act
    await pushNudge('sim-other');

    // Assert
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsBefore);
  });

  it('given_aBurstOfNudges_should_coalesceIntoALeadingAndOneTrailingRead', async () => {
    // Arrange: versions ABOVE the snapshot's cursor (41), otherwise they are legitimately skipped as
    // already caught up.
    renderLive();
    await flush();
    const callsBefore = mocks.fetchAttackPathGraphDelta.mock.calls.length;
    const handler = mocks.streamHandlers.get(ATTACK_PATH_VERSION_EVENT);

    // Act: several bumps land inside one throttle window, as a hot run produces them.
    await act(async () => {
      [42, 43, 44].forEach(v => handler?.({
        data: JSON.stringify({
          simulation_id: 'sim-1',
          version: v,
        }),
      } as MessageEvent));
      await vi.advanceTimersByTimeAsync(NUDGE_DEBOUNCE_MS);
    });

    // Assert: the first nudge reads at once (a busy run must not go stale waiting for a quiet gap),
    // the rest collapse into one trailing read — two, never three.
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsBefore + 2);
  });

  it('given_aNudgeBelowTheCursor_should_notFetch', async () => {
    // Arrange: a poll already advanced the cursor past this version.
    renderLive();
    await flush();
    const callsBefore = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Act
    await pushNudge('sim-1', 1);

    // Assert
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsBefore);
  });

  it('given_aHealthyStream_should_pollOnlyAsASafetyNet', async () => {
    // Arrange: the stream delivers, so the timer is the net rather than the mechanism.
    mocks.streamHealthy = true;
    renderLive();
    await flush();
    const callsBefore = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Act: well past the 3 s cadence, still short of the safety net.
    await tick(DELTA_POLL_MS * 3);

    // Assert: nothing yet; the net fires later.
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsBefore);
    await tick(DELTA_SAFETY_NET_MS);
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBeGreaterThan(callsBefore);
  });

  it('given_aDeadStream_should_restoreTheThreeSecondCadence', async () => {
    // Arrange: no stream (a stripping proxy, or an environment without EventSource at all).
    mocks.streamHealthy = false;
    renderLive();
    await flush();
    const callsBefore = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Act
    await tick(DELTA_POLL_MS);

    // Assert: the shipped cadence is back, so a dead stream never means "no updates".
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsBefore + 1);
  });

  it('given_aNudgeDuringAFetch_should_scheduleExactlyOneFollowUp', async () => {
    // Arrange: a slow delta read, and a nudge that lands while it is still in flight.
    renderLive();
    await flush();
    let release: (value: unknown) => void = () => {};
    mocks.fetchAttackPathGraphDelta.mockImplementationOnce(
      () => new Promise((resolve) => {
        release = resolve;
      }),
    );
    await pushNudge('sim-1');
    const callsDuring = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Act: two more nudges while the first read hangs, then it completes.
    await pushNudge('sim-1');
    await pushNudge('sim-1');
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsDuring);
    await act(async () => {
      release(emptyDelta(41));
      await Promise.resolve();
      await Promise.resolve();
    });

    // Assert: the queued nudges collapsed into a single follow-up read, so the same batch is never
    // applied twice (which would replay the entrance animation).
    expect(mocks.fetchAttackPathGraphDelta.mock.calls.length).toBe(callsDuring + 1);
  });

  it('given_unmount_should_dropTheSubscription', async () => {
    // Arrange
    const { unmount } = renderLive();
    await flush();
    expect(mocks.streamHandlers.has(ATTACK_PATH_VERSION_EVENT)).toBe(true);

    // Act
    unmount();

    // Assert: no handler is left holding a reference to the unmounted view.
    expect(mocks.streamHandlers.has(ATTACK_PATH_VERSION_EVENT)).toBe(false);
    expect(mocks.unsubscribed).toBeGreaterThan(0);
  });
});
