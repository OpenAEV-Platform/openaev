import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import useAttackPathLiveGraph, { BATCH_TTL_MS, DELTA_POLL_MS } from '../../../../../../admin/components/simulations/simulation/attack_path/useAttackPathLiveGraph';

const mocks = vi.hoisted(() => ({
  fetchAttackPathGraph: vi.fn(),
  fetchAttackPathGraphDelta: vi.fn(),
}));

vi.mock('../../../../../../actions/attack-path/attack-path-actions', () => ({
  fetchAttackPathGraph: mocks.fetchAttackPathGraph,
  fetchAttackPathGraphDelta: mocks.fetchAttackPathGraphDelta,
}));

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
});
