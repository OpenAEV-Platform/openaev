import { describe, expect, it } from 'vitest';

import { applyDelta, fromSnapshot, toCollapsedDto, toFullDto, withFullSnapshot } from '../../../../../../admin/components/simulations/simulation/attack_path/attack-path-delta-store';
import type { AttackPathDTO } from '../../../../../../utils/api-types';
import type { AttackPathDeltaDTO } from '../../../../../../utils/api-types-custom';

const INJECTOR = 'NODE_INJECTOR|nmap';
const HOST_X = 'NODE_ENDPOINT|host-x';
const HOST_Y = 'NODE_ENDPOINT|host-y';
const EDGE_X = 'EDGE_EXECUTIONS|NODE_INJECTOR|nmap|NODE_ENDPOINT|host-x';
const EDGE_Y = 'EDGE_EXECUTIONS|NODE_INJECTOR|nmap|NODE_ENDPOINT|host-y';
const FINDING_TYPE_NODE = 'NODE_FINDINGS_TYPE|credentials|host-x';
const FINDING_NODE = 'NODE_FINDING|credentials|admin:secret';

// The graph as the collapsed snapshot returns it early in a run: one injector, one reached endpoint.
const snapshotV1: AttackPathDTO = {
  mode: 'collapsed',
  attackPathNodes: [{
    id: INJECTOR,
    type: 'INJECTOR',
    label: 'nmap',
  }, {
    id: HOST_X,
    type: 'ASSET',
    ref: 'host-x',
    label: 'CORP-X',
    hostname: 'CORP-X',
    status: 'RED',
    findingCounts: {},
  }],
  attackPathEdges: [{
    edgeId: EDGE_X,
    edgeSourceId: INJECTOR,
    edgeTargetId: HOST_X,
    type: 'EDGE_EXECUTIONS',
    count: 1,
  }],
  attackPathExecutions: [],
  staticAttackPathFindings: [],
  counters: {
    endpoints: 1,
    credentials: 0,
    users: 0,
    cves: 0,
    ports: 0,
  },
};

// What changed since v1: host-x got a credential and turned orange, host-y was just reached.
const delta: AttackPathDeltaDTO = {
  sinceVersion: 1,
  newVersion: 2,
  resyncRequired: false,
  attackPathNodes: [{
    id: HOST_X,
    type: 'ASSET',
    ref: 'host-x',
    label: 'CORP-X',
    hostname: 'CORP-X',
    status: 'ORANGE',
    findingCounts: { credentials: 1 },
  }, {
    id: HOST_Y,
    type: 'ASSET',
    ref: 'host-y',
    label: 'CORP-Y',
    hostname: 'CORP-Y',
    status: 'RED',
    findingCounts: {},
  }, {
    id: FINDING_TYPE_NODE,
    type: 'FINDING_TYPE',
    label: 'credentials',
    typeFindings: 'credentials',
    assetNodeId: HOST_X,
  }],
  attackPathEdges: [{
    edgeId: EDGE_X,
    edgeSourceId: INJECTOR,
    edgeTargetId: HOST_X,
    type: 'EDGE_EXECUTIONS',
    count: 2,
  }, {
    edgeId: EDGE_Y,
    edgeSourceId: INJECTOR,
    edgeTargetId: HOST_Y,
    type: 'EDGE_EXECUTIONS',
    count: 1,
  }, {
    edgeId: 'EDGE_ENDPOINT_FINDINGS_TYPE|credentials|host-x',
    edgeSourceId: HOST_X,
    edgeTargetId: FINDING_TYPE_NODE,
    type: 'EDGE_ENDPOINT_FINDINGS_TYPE',
  }, {
    edgeId: 'EDGE_FINDINGS_TYPE_FINDING|credentials|host-x|admin:secret',
    edgeSourceId: FINDING_TYPE_NODE,
    edgeTargetId: FINDING_NODE,
    type: 'EDGE_FINDINGS_TYPE_FINDING',
  }],
  attackPathExecutions: [{
    id: 'NODE_EXECUTION|exec-2|host-x|agent-1',
    ref: 'exec-2',
    type: 'EXECUTION',
    status: 'ORANGE',
    payloadName: 'nmap',
  }],
  staticAttackPathFindings: [{
    id: FINDING_NODE,
    type: 'FINDING',
    label: 'admin:secret',
    value: 'admin:secret',
    typeFindings: 'credentials',
    findingsTypeNodeId: FINDING_TYPE_NODE,
    assetNodeId: HOST_X,
    verdicts: { prevention: 'RED' },
  }],
  counters: {
    endpoints: 2,
    credentials: 1,
    users: 0,
    cves: 0,
    ports: 0,
  },
};

// The collapsed snapshot a fresh read would return once the delta above has been committed backend-side.
const snapshotV2: AttackPathDTO = {
  mode: 'collapsed',
  attackPathNodes: [
    snapshotV1.attackPathNodes![0],
    delta.attackPathNodes![0],
    delta.attackPathNodes![1],
  ],
  // Only the execution edges: the collapsed projection never carries the finding edges.
  attackPathEdges: delta.attackPathEdges!.slice(0, 2),
  attackPathExecutions: [],
  staticAttackPathFindings: [],
  counters: delta.counters,
};

// Array order is not part of the contract (the backend builds from grouped queries), so compare graphs
// by id-sorted collections.
const normalise = (dto: AttackPathDTO) => ({
  ...dto,
  attackPathNodes: [...(dto.attackPathNodes ?? [])].sort((a, b) => (a.id ?? '').localeCompare(b.id ?? '')),
  attackPathEdges: [...(dto.attackPathEdges ?? [])].sort((a, b) => (a.edgeId ?? '').localeCompare(b.edgeId ?? '')),
});

describe('attack-path delta store', () => {
  describe('applyDelta', () => {
    it('given_aDelta_should_upsertNodesEdgesAndCounters', () => {
      // Arrange
      const store = fromSnapshot(snapshotV1, 1);

      // Act
      const { store: next, changed, structuralChange, newNodeIds } = applyDelta(store, delta);

      // Assert
      expect(changed).toBe(true);
      expect(structuralChange).toBe(true);
      expect(newNodeIds).toContain(HOST_Y);
      expect(next.version).toBe(2);
      expect(next.nodes.get(HOST_X)?.status).toBe('ORANGE');
      expect(next.edges.get(EDGE_X)?.count).toBe(2);
      expect(next.counters?.credentials).toBe(1);
    });

    it('given_theSameDeltaTwice_should_produceTheSameState', () => {
      // Arrange
      const once = applyDelta(fromSnapshot(snapshotV1, 1), delta);

      // Act
      const twice = applyDelta(once.store, delta);

      // Assert: a replayed delta changes nothing at all, and hands back the very same store.
      expect(twice.changed).toBe(false);
      expect(twice.structuralChange).toBe(false);
      expect(twice.newNodeIds).toEqual([]);
      expect(twice.store).toBe(once.store);
    });

    it('given_aDelta_should_keepTheIdentityOfUntouchedEntries', () => {
      // Arrange
      const store = fromSnapshot(snapshotV1, 1);
      const injectorBefore = store.nodes.get(INJECTOR);

      // Act
      const { store: next } = applyDelta(store, delta);

      // Assert: the injector was not part of the delta, so React Flow keeps its node as-is.
      expect(next.nodes.get(INJECTOR)).toBe(injectorBefore);
      expect(next.nodes.get(HOST_X)).not.toBe(store.nodes.get(HOST_X));
    });

    it('given_aVerdictOnlyDelta_should_notReportAStructuralChange', () => {
      // Arrange
      const store = fromSnapshot(snapshotV1, 1);
      const verdictDelta: AttackPathDeltaDTO = {
        sinceVersion: 1,
        newVersion: 2,
        attackPathNodes: [{
          ...snapshotV1.attackPathNodes![1],
          status: 'GREEN',
        }],
      };

      // Act
      const { changed, structuralChange, newNodeIds } = applyDelta(store, verdictDelta);

      // Assert
      expect(changed).toBe(true);
      expect(structuralChange).toBe(false);
      expect(newNodeIds).toEqual([]);
    });

    it('given_anEmptyDelta_should_advanceTheCursorWithoutTouchingTheGraph', () => {
      // Arrange
      const store = fromSnapshot(snapshotV1, 1);

      // Act
      const { store: next, changed } = applyDelta(store, {
        sinceVersion: 1,
        newVersion: 1,
      });

      // Assert
      expect(changed).toBe(false);
      expect(next).toBe(store);
    });

    it('given_aResyncSignal_should_leaveTheStoreAndTheCursorAlone', () => {
      // Arrange
      const store = applyDelta(fromSnapshot(snapshotV1, 1), delta).store;

      // Act
      const { store: next, changed } = applyDelta(store, {
        sinceVersion: 2,
        newVersion: 9,
        resyncRequired: true,
      });

      // Assert: the caller re-seeds from a snapshot; the cursor must not jump to a version we never read.
      expect(changed).toBe(false);
      expect(next).toBe(store);
      expect(next.version).toBe(2);
    });

    it('given_changedFindings_should_indexThemAndReportTheirTypes', () => {
      // Arrange
      const store = withFullSnapshot(fromSnapshot(snapshotV1, 1), {
        ...snapshotV1,
        mode: 'full',
      });

      // Act
      const { store: next, changedFindingTypes } = applyDelta(store, delta);

      // Assert: the finding lands in both indexes (graph node and deduplicated finding list), and its
      // type is reported so an open findings drawer can refresh itself.
      expect(changedFindingTypes).toEqual(['credentials']);
      expect(next.nodes.get(FINDING_NODE)?.verdicts).toEqual({ prevention: 'RED' });
      expect(next.nodes.get(FINDING_TYPE_NODE)?.typeFindings).toBe('credentials');
      expect(next.staticFindings.get(FINDING_NODE)).toBeDefined();
      expect([...next.edges.values()].filter(e => e.type === 'EDGE_FINDINGS_TYPE_FINDING').length).toBe(1);
      expect([...next.edges.values()].filter(e => e.type === 'EDGE_ENDPOINT_FINDINGS_TYPE').length).toBe(1);
    });
  });

  describe('projections', () => {
    it('given_aSnapshotPlusItsDelta_should_matchTheEquivalentSnapshot', () => {
      // Arrange
      const patched = applyDelta(fromSnapshot(snapshotV1, 1), delta).store;

      // Act
      const collapsed = toCollapsedDto(patched);

      // Assert: snapshot(v) + delta(v→w) ≡ snapshot(w), the property the whole contract rests on.
      expect(normalise(collapsed)).toEqual(normalise(snapshotV2));
    });

    it('given_theSameStore_should_returnTheSameProjectionReference', () => {
      // Arrange
      const store = fromSnapshot(snapshotV1, 1);

      // Act & Assert: memoized, so an unchanged tick re-renders nothing downstream.
      expect(toCollapsedDto(store)).toBe(toCollapsedDto(store));
    });

    it('given_noFullSnapshot_should_notExposeAFullProjection', () => {
      // Arrange & Act
      const store = fromSnapshot(snapshotV1, 1);

      // Assert: runs above the size gate never seed the full graph, so the chain layout stays off.
      expect(toFullDto(store)).toBeNull();
    });

    it('given_aFullSnapshot_should_exposeExecutionsAndFindingNodes', () => {
      // Arrange
      const seeded = withFullSnapshot(fromSnapshot(snapshotV1, 1), {
        ...snapshotV1,
        mode: 'full',
      });

      // Act
      const full = toFullDto(applyDelta(seeded, delta).store);

      // Assert
      expect(full?.attackPathExecutions?.map(e => e.ref)).toEqual(['exec-2']);
      expect(full?.attackPathNodes?.some(n => n.type === 'FINDING')).toBe(true);
      // The collapsed projection stays the endpoint/injector topology, finding nodes excluded.
      expect(toCollapsedDto(applyDelta(seeded, delta).store).attackPathNodes?.every(n => n.type === 'ASSET' || n.type === 'INJECTOR')).toBe(true);
    });
  });
});
