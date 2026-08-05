import { describe, expect, it } from 'vitest';

import { AP_ALL_ENDPOINTS, AP_FLOW_CAUSAL_EDGE_TYPE, AP_FLOW_EDGE_TYPE, AP_FLOW_NODE_TYPE, applyFindingFilter, type AttackPathFlowNode, buildAttackPathFlow, buildCausalChainFlow, buildCausalEdges, buildClusteredAttackPathFlow, buildFindingPathFlow, buildKillChainMeta, displayIp, FILTER_TO_FINDING_TYPES, findingCategoryNoun, friendlyNodeId, maskFindingValue, orderSimulationPickerOptions, pivotEndpointIds, scopeChainFlowToSeeds } from '../../../../../../admin/components/simulations/simulation/attack_path/attack-path-flow-helpers';
import type { AttackPathDTO } from '../../../../../../utils/api-types';

// Identity translator with {param} interpolation, mirroring the formatter's key fallback, so the label
// assertions read the English source strings.
const tt = (key: string, params?: Record<string, string>): string =>
  (params ? key.replace(/\{(\w+)\}/g, (_, n) => params[n] ?? '') : key);

const dto: AttackPathDTO = {
  mode: 'full',
  counters: {
    endpoints: 1,
    credentials: 1,
    users: 0,
    cves: 0,
    ports: 0,
  },
  attackPathExecutions: [],
  staticAttackPathFindings: [],
  attackPathNodes: [
    {
      id: 'inj-nmap',
      type: 'INJECTOR',
      label: 'nmap',
    },
    {
      id: 'ep-dc01',
      type: 'ASSET',
      label: 'CORP-DC-01',
      status: 'ORANGE',
      hostname: 'CORP-DC-01',
    },
    {
      id: 'ft-creds-dc01',
      type: 'FINDING_TYPE',
      label: 'credentials',
      typeFindings: 'credentials',
      assetNodeId: 'ep-dc01',
    },
    {
      id: 'fn-creds',
      type: 'FINDING',
      label: 'admin:secret',
      value: 'admin:secret',
      typeFindings: 'credentials',
    },
    // An EXECUTION node in the feed must NOT become a flow node.
    {
      id: 'exec-1',
      type: 'EXECUTION',
      label: 'payload',
    },
  ],
  attackPathEdges: [
    {
      edgeId: 'e1',
      edgeSourceId: 'inj-nmap',
      edgeTargetId: 'ep-dc01',
      type: 'EDGE_EXECUTIONS',
      count: 3,
    },
    {
      edgeId: 'e2',
      edgeSourceId: 'ep-dc01',
      edgeTargetId: 'ft-creds-dc01',
      type: 'EDGE_ENDPOINT_FINDINGS_TYPE',
      count: 1,
    },
    {
      edgeId: 'e3',
      edgeSourceId: 'ft-creds-dc01',
      edgeTargetId: 'fn-creds',
      type: 'EDGE_FINDINGS_TYPE_FINDING',
      count: 1,
    },
    // Edge into a node that is not in the set must be dropped, not dangling.
    {
      edgeId: 'e-dangling',
      edgeSourceId: 'ep-dc01',
      edgeTargetId: 'missing',
      type: 'EDGE_EXECUTIONS',
      count: 1,
    },
  ],
};

describe('buildAttackPathFlow', () => {
  it('maps DTO nodes to React Flow nodes by type, dropping the execution feed node', () => {
    const { nodes } = buildAttackPathFlow(dto);
    const byId = Object.fromEntries(nodes.map(n => [n.id, n]));

    expect(nodes).toHaveLength(4); // injector, asset, finding type, finding — not the execution
    expect(byId['inj-nmap'].type).toBe(AP_FLOW_NODE_TYPE.injector);
    expect(byId['ep-dc01'].type).toBe(AP_FLOW_NODE_TYPE.asset);
    expect(byId['ft-creds-dc01'].type).toBe(AP_FLOW_NODE_TYPE.findingType);
    expect(byId['fn-creds'].type).toBe(AP_FLOW_NODE_TYPE.finding);
    expect(nodes.find(n => n.id === 'exec-1')).toBeUndefined();
  });

  it('carries the endpoint status onto the asset node', () => {
    const { nodes } = buildAttackPathFlow(dto);
    const asset = nodes.find(n => n.id === 'ep-dc01');
    expect(asset?.data.status).toBe('ORANGE');
  });

  it('lays nodes out in left-to-right columns by kind', () => {
    const { nodes } = buildAttackPathFlow(dto);
    const x = (id: string) => nodes.find(n => n.id === id)!.position.x;
    expect(x('inj-nmap')).toBeLessThan(x('ep-dc01'));
    expect(x('ep-dc01')).toBeLessThan(x('ft-creds-dc01'));
    expect(x('ft-creds-dc01')).toBeLessThan(x('fn-creds'));
  });

  it('builds grouped edges carrying the count and drops edges with a missing endpoint', () => {
    const { edges } = buildAttackPathFlow(dto);
    expect(edges).toHaveLength(3); // the dangling edge is dropped
    const e1 = edges.find(e => e.id === 'e1');
    expect(e1?.type).toBe(AP_FLOW_EDGE_TYPE);
    expect(e1?.data?.count).toBe(3);
    expect(edges.find(e => e.id === 'e-dangling')).toBeUndefined();
  });
});

describe('applyFindingFilter', () => {
  const base = buildAttackPathFlow(dto);

  it('returns the input unchanged when no filter is active', () => {
    const res = applyFindingFilter(base.nodes, base.edges, null);
    expect(res.nodes).toBe(base.nodes);
    expect(res.edges).toBe(base.edges);
  });

  it('lights the injector -> endpoint backbone and dims findings for the endpoints filter', () => {
    const { nodes } = applyFindingFilter(base.nodes, base.edges, 'endpoints');
    const byId = Object.fromEntries(nodes.map(n => [n.id, n]));
    expect(byId['inj-nmap'].data.dimmed).toBe(false);
    expect(byId['ep-dc01'].data.dimmed).toBe(false);
    expect(byId['ft-creds-dc01'].data.dimmed).toBe(true);
    expect(byId['fn-creds'].data.dimmed).toBe(true);
  });

  it('lights the whole upstream path to a matching finding type and dims the rest', () => {
    const { nodes } = applyFindingFilter(base.nodes, base.edges, ['credentials']);
    const byId = Object.fromEntries(nodes.map(n => [n.id, n]));
    // credential finding + finding-type, plus the endpoint and injector reaching them, are all lit.
    expect(byId['fn-creds'].data.dimmed).toBe(false);
    expect(byId['ft-creds-dc01'].data.dimmed).toBe(false);
    expect(byId['ep-dc01'].data.dimmed).toBe(false);
    expect(byId['inj-nmap'].data.dimmed).toBe(false);
  });

  it('dims every node when no node matches the filter', () => {
    const { nodes } = applyFindingFilter(base.nodes, base.edges, ['cve']);
    expect(nodes.every(n => n.data.dimmed === true)).toBe(true);
  });
});

describe('friendlyNodeId', () => {
  it('shows just the injector name for a per-contract injector id (drops the contract uuid)', () => {
    expect(friendlyNodeId('NODE_INJECTOR|NetExec|8f3c-contract-uuid')).toBe('NetExec');
  });

  it('keeps the plain injector name for a contractless (2-segment) injector id', () => {
    expect(friendlyNodeId('NODE_INJECTOR|Nmap')).toBe('Nmap');
  });

  it('keeps the full key for non-injector nodes (endpoints)', () => {
    expect(friendlyNodeId('NODE_ENDPOINT|10.0.0.1')).toBe('10.0.0.1');
  });

  it('returns an empty string for an undefined id', () => {
    expect(friendlyNodeId(undefined)).toBe('');
  });
});

describe('pivotEndpointIds', () => {
  it('flags an endpoint that is both an EDGE_EXECUTIONS source and target as a pivot', () => {
    const pivots = pivotEndpointIds([
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'NODE_INJECTOR|nmap',
        edgeTargetId: 'NODE_ENDPOINT|a',
      },
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'NODE_ENDPOINT|a',
        edgeTargetId: 'NODE_ENDPOINT|b',
      },
    ]);
    expect([...pivots]).toEqual(['NODE_ENDPOINT|a']);
  });

  it('never flags a plain target or a source-only endpoint (injector→asset only)', () => {
    expect(pivotEndpointIds([
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'NODE_INJECTOR|nmap',
        edgeTargetId: 'NODE_ENDPOINT|a',
      },
    ]).size).toBe(0);
  });

  it('ignores non-EDGE_EXECUTIONS edges', () => {
    expect(pivotEndpointIds([
      {
        type: 'EDGE_FINDINGS_TYPE_FINDING',
        edgeSourceId: 'NODE_ENDPOINT|a',
        edgeTargetId: 'NODE_ENDPOINT|a',
      },
    ]).size).toBe(0);
  });

  it('never flags an endpoint whose only source role is a self-loop (endpoint-local action)', () => {
    // A Whoami-style local action emits source = target = the endpoint; that is not lateral movement.
    expect(pivotEndpointIds([
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'NODE_INJECTOR|nmap',
        edgeTargetId: 'NODE_ENDPOINT|a',
      },
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'NODE_ENDPOINT|a',
        edgeTargetId: 'NODE_ENDPOINT|a',
      },
    ]).size).toBe(0);
  });
});

describe('orderSimulationPickerOptions', () => {
  // ISO start dates keyed by simulation id (the shape the picker resolves from simulation meta).
  const dates: Record<string, string> = {
    a: '2026-07-27T12:24:00Z',
    b: '2026-07-28T08:05:00Z',
    c: '2026-07-29T11:20:00Z',
  };
  const startDateOf = (simId?: string) => dates[simId ?? ''] ?? '';

  it('orders the options most recent first, regardless of the incoming order', () => {
    // Arrange: rows in a jumbled order (as the backend returns them).
    const rows = [{ simulationId: 'a' }, { simulationId: 'c' }, { simulationId: 'b' }];
    // Act
    const ordered = orderSimulationPickerOptions(rows, null, startDateOf);
    // Assert: newest (c, Jul 29) first, oldest (a, Jul 27) last.
    expect(ordered.map(o => o.simulationId)).toEqual(['c', 'b', 'a']);
  });

  it('does not mutate the input array', () => {
    const rows = [{ simulationId: 'a' }, { simulationId: 'c' }, { simulationId: 'b' }];
    orderSimulationPickerOptions(rows, null, startDateOf);
    expect(rows.map(o => o.simulationId)).toEqual(['a', 'c', 'b']);
  });

  it('keeps a selected row that is absent from the list and orders it by its own date', () => {
    // Arrange: the selected run has no summary row in `simulations` yet, but its date resolves.
    const rows = [{ simulationId: 'a' }, { simulationId: 'b' }];
    const selected = { simulationId: 'c' };
    // Act
    const ordered = orderSimulationPickerOptions(rows, selected, startDateOf);
    // Assert: selected (newest) is included and sorted to the front, not just prepended blindly.
    expect(ordered.map(o => o.simulationId)).toEqual(['c', 'b', 'a']);
  });

  it('does not duplicate a selected row that is already in the list', () => {
    const rows = [{ simulationId: 'a' }, { simulationId: 'c' }, { simulationId: 'b' }];
    const ordered = orderSimulationPickerOptions(rows, { simulationId: 'c' }, startDateOf);
    expect(ordered.map(o => o.simulationId)).toEqual(['c', 'b', 'a']);
  });

  it('sorts rows with an unknown date to the end', () => {
    const rows = [{ simulationId: 'unknown' }, { simulationId: 'b' }, { simulationId: 'c' }];
    const ordered = orderSimulationPickerOptions(rows, null, startDateOf);
    expect(ordered.map(o => o.simulationId)).toEqual(['c', 'b', 'unknown']);
  });
});

describe('file finding type wiring', () => {
  it('maps the files card to the native file finding type', () => {
    expect(FILTER_TO_FINDING_TYPES.files).toEqual(['file']);
    // shares stays distinct from files — a file is never folded into the share type.
    expect(FILTER_TO_FINDING_TYPES.shares).toEqual(['share']);
  });

  it('reads a file finding as the "files" category noun', () => {
    expect(findingCategoryNoun('file')).toBe('files');
  });
});

describe('maskFindingValue', () => {
  it('masks secret finding types', () => {
    expect(maskFindingValue('credentials', 'admin:secret')).toBe('admin : ••••••');
    expect(maskFindingValue('credentials', 'nosecrethere')).toBe('••••••••');
    expect(maskFindingValue('credentials', ':secretonly')).toBe('••••••••');
    expect(maskFindingValue('sid', 'S-1-5-21')).toBe('••••••••');
    expect(maskFindingValue('password_policy', 'complex')).toBe('••••••••');
  });

  it('shows non-secret finding values as-is', () => {
    expect(maskFindingValue('cve', 'CVE-2023-1')).toBe('CVE-2023-1');
    expect(maskFindingValue('port', '443')).toBe('443');
    expect(maskFindingValue('username', 'bob')).toBe('bob');
  });

  it('displays a file as its basename, keeping the full path out of the label', () => {
    expect(maskFindingValue('file', '\\\\WINTERFELL\\SYSVOL\\scripts\\secret.ps1')).toBe('secret.ps1');
    expect(maskFindingValue('file', 'ftp01:/home/user/config.ini')).toBe('config.ini');
    // A bare name (no separators) is returned unchanged.
    expect(maskFindingValue('file', 'notes.txt')).toBe('notes.txt');
  });

  it('returns an empty string for an undefined value', () => {
    expect(maskFindingValue('port', undefined)).toBe('');
  });
});

describe('buildClusteredAttackPathFlow', () => {
  const clusteredDto: AttackPathDTO = {
    mode: 'collapsed',
    counters: {
      endpoints: 2,
      credentials: 5,
      users: 0,
      cves: 1,
      ports: 0,
    },
    attackPathExecutions: [],
    staticAttackPathFindings: [],
    attackPathNodes: [
      {
        id: 'inj',
        type: 'INJECTOR',
        label: 'impacket',
      },
      {
        id: 'ep1',
        type: 'ASSET',
        label: 'EP1',
        findingCounts: {
          credentials: 3,
          cve: 1,
        },
      },
      {
        id: 'ep2',
        type: 'ASSET',
        label: 'EP2',
        findingCounts: { credentials: 2 },
      },
    ],
    attackPathEdges: [
      {
        edgeId: 'x1',
        edgeSourceId: 'inj',
        edgeTargetId: 'ep1',
        type: 'EDGE_EXECUTIONS',
        count: 5,
      },
      {
        edgeId: 'x2',
        edgeSourceId: 'inj',
        edgeTargetId: 'ep2',
        type: 'EDGE_EXECUTIONS',
        count: 2,
      },
    ],
  };

  it('dedups endpoints into one shared hub with a global finding cluster per type', () => {
    const { nodes, edges } = buildClusteredAttackPathFlow(clusteredDto, new Map(), tt);
    const byId = Object.fromEntries(nodes.map(n => [n.id, n]));

    expect(byId['inj'].type).toBe(AP_FLOW_NODE_TYPE.injector);
    // ONE shared endpoint hub (deduped across injectors) carrying the distinct endpoint count
    expect(byId['cl-ep-all'].type).toBe(AP_FLOW_NODE_TYPE.endpointCluster);
    expect(byId['cl-ep-all'].data.count).toBe(2);
    expect(byId['cl-ep-all'].data.injectorId).toBe(AP_ALL_ENDPOINTS);
    // one GLOBAL finding cluster per type (keyed by type only), counts summed across all endpoints
    expect(byId['cl-ft-credentials'].type).toBe(AP_FLOW_NODE_TYPE.findingCluster);
    expect(byId['cl-ft-credentials'].data.count).toBe(5); // 3 + 2
    expect(byId['cl-ft-cve'].data.count).toBe(1);
    // no real endpoint nodes while collapsed
    expect(byId['ep1']).toBeUndefined();
    // edges: injector -> shared hub carries no count badge (the reached-endpoint count is shown on the
    // hub itself, so repeating it on the injector edge is redundant) -> findings
    expect(edges.find(e => e.id === 'inj-cl-ep-all')?.data?.count).toBe(1);
    expect(edges.find(e => e.id === 'inj-cl-ep-all')?.data?.label).toBeUndefined();
    expect(edges.filter(e => e.source === 'cl-ep-all')).toHaveLength(2);
    expect(edges[0].type).toBe(AP_FLOW_EDGE_TYPE);
  });

  it('replaces the shared hub with the deduped real endpoints when expanded', () => {
    const { nodes, edges } = buildClusteredAttackPathFlow(clusteredDto, new Map([[AP_ALL_ENDPOINTS, 15]]), tt);
    const ids = nodes.map(n => n.id);
    expect(ids).toContain('ep1');
    expect(ids).toContain('ep2');
    expect(nodes.find(n => n.id === 'ep1')?.type).toBe(AP_FLOW_NODE_TYPE.asset);
    expect(edges.find(e => e.id === 'cl-ep-all-ep1')?.target).toBe('ep1');
  });

  it('keeps an endpoint reached only by an endpoint-local action (self-loop) visible, without a self arrow', () => {
    // Arrange: ep2 is reached ONLY by a Whoami-style local action (execution edge source = target = ep2).
    const selfLoopDto: AttackPathDTO = {
      ...clusteredDto,
      attackPathEdges: [
        {
          edgeId: 'x1',
          edgeSourceId: 'inj',
          edgeTargetId: 'ep1',
          type: 'EDGE_EXECUTIONS',
          count: 5,
        },
        {
          edgeId: 'x-self',
          edgeSourceId: 'ep2',
          edgeTargetId: 'ep2',
          type: 'EDGE_EXECUTIONS',
          count: 1,
        },
      ],
    };
    // Act: collapsed (hub) and expanded views.
    const collapsed = buildClusteredAttackPathFlow(selfLoopDto, new Map(), tt);
    const expanded = buildClusteredAttackPathFlow(selfLoopDto, new Map([[AP_ALL_ENDPOINTS, 15]]), tt);
    // Assert: ep2 counts as reached (hub count 2) and renders when expanded, with its findings…
    expect(collapsed.nodes.find(n => n.id === 'cl-ep-all')?.data.count).toBe(2);
    expect(expanded.nodes.find(n => n.id === 'ep2')?.type).toBe(AP_FLOW_NODE_TYPE.asset);
    // …but no arrow loops back onto it: the only edge INTO ep2 comes from the shared hub.
    expect(expanded.edges.find(e => e.source === 'ep2' && e.target === 'ep2')).toBeUndefined();
    expect(expanded.edges.filter(e => e.target === 'ep2').map(e => e.source)).toEqual(['cl-ep-all']);
  });

  it('still ignores a malformed execution edge that has a target but no source', () => {
    // Arrange: ep2's only edge is sourceless — malformed, not an endpoint-local action.
    const malformedDto: AttackPathDTO = {
      ...clusteredDto,
      attackPathEdges: [
        {
          edgeId: 'x1',
          edgeSourceId: 'inj',
          edgeTargetId: 'ep1',
          type: 'EDGE_EXECUTIONS',
          count: 5,
        },
        {
          edgeId: 'x-broken',
          edgeTargetId: 'ep2',
          type: 'EDGE_EXECUTIONS',
          count: 1,
        },
      ],
    };
    // Act
    const { nodes } = buildClusteredAttackPathFlow(malformedDto, new Map(), tt);
    // Assert: ep2 is NOT counted as reached (pre-existing behavior preserved).
    expect(nodes.find(n => n.id === 'cl-ep-all')?.data.count).toBe(1);
  });
});

// Kill-chain: two NetExec-style steps, the SMB step (inj-smb) consumes a share and depends on the
// port-scan step (inj-nmap). Executions carry the kill-chain fields and are linked to their injector
// via edge.executionIds ↔ execution.ref (the real DTO linkage).
const killChainDto: AttackPathDTO = {
  mode: 'full',
  attackPathNodes: [
    {
      id: 'inj-nmap',
      type: 'INJECTOR',
      label: 'nmap',
    },
    {
      id: 'inj-smb',
      type: 'INJECTOR',
      label: 'NetExec',
    },
    {
      id: 'ep-1',
      type: 'ASSET',
      label: 'DC-01',
    },
  ],
  attackPathExecutions: [
    {
      id: 'x1',
      type: 'EXECUTION',
      ref: 'exec-1',
      stepTemplateId: 'step-A',
      consumedFindingKeys: [{
        keyType: 'port',
        operator: 'EQ',
        value: '445',
        matchedFindingIds: ['find-port'],
      }],
      dependsOn: [],
    },
    {
      id: 'x2',
      type: 'EXECUTION',
      ref: 'exec-2',
      stepTemplateId: 'step-B',
      consumedFindingKeys: [{
        keyType: 'share_name',
        operator: 'EQ',
        value: 'ADMIN$',
        matchedFindingIds: ['find-share'],
      }],
      dependsOn: ['step-A'],
    },
  ],
  attackPathEdges: [
    {
      type: 'EDGE_EXECUTIONS',
      edgeSourceId: 'inj-nmap',
      edgeTargetId: 'ep-1',
      executionIds: ['exec-1'],
    },
    {
      type: 'EDGE_EXECUTIONS',
      edgeSourceId: 'inj-smb',
      edgeTargetId: 'ep-1',
      executionIds: ['exec-2'],
    },
  ],
};

const injectorNode = (id: string): AttackPathFlowNode => ({
  id,
  type: AP_FLOW_NODE_TYPE.injector,
  position: {
    x: 0,
    y: 0,
  },
  data: {},
});
const findingNode = (id: string, typeFindings: string, value: string): AttackPathFlowNode => ({
  id,
  type: AP_FLOW_NODE_TYPE.finding,
  position: {
    x: 0,
    y: 0,
  },
  data: {
    typeFindings,
    value,
  },
});

describe('buildKillChainMeta', () => {
  it('aggregates consumed keys per injector via executionIds <-> exec.ref', () => {
    // Act
    const meta = buildKillChainMeta(killChainDto);
    // Assert
    expect(meta.get('inj-nmap')?.consumedFindingKeys).toEqual([{
      keyType: 'port',
      operator: 'EQ',
      value: '445',
      matchedFindingIds: ['find-port'],
    }]);
    expect(meta.get('inj-smb')?.consumedFindingKeys).toEqual([{
      keyType: 'share_name',
      operator: 'EQ',
      value: 'ADMIN$',
      matchedFindingIds: ['find-share'],
    }]);
  });

  it('resolves dependsOn (step template ids) to the injector node ids that ran them', () => {
    // Act
    const meta = buildKillChainMeta(killChainDto);
    // Assert: step-B depends on step-A, which ran on inj-nmap
    expect(meta.get('inj-smb')?.dependsOn).toEqual(['inj-nmap']);
    expect(meta.get('inj-nmap')?.dependsOn).toEqual([]);
  });

  it('returns an empty map when the DTO carries no kill-chain data', () => {
    expect(buildKillChainMeta(null).size).toBe(0);
    expect(buildKillChainMeta({
      mode: 'full',
      attackPathExecutions: [],
      attackPathEdges: [],
    }).size).toBe(0);
  });
});

describe('buildCausalEdges', () => {
  it('anchors a solid finding edge on the backend-resolved matchedFindingIds', () => {
    // Arrange: the backend resolved that this consumer's key matched the finding node 'find-share'.
    const meta = buildKillChainMeta(killChainDto);
    const nodes = [injectorNode('inj-smb'), findingNode('find-share', 'share', 'ADMIN$')];
    // Act
    const edges = buildCausalEdges(nodes, id => (id ? meta.get(id) : undefined), tt);
    // Assert
    expect(edges).toHaveLength(1);
    expect(edges[0].type).toBe(AP_FLOW_CAUSAL_EDGE_TYPE);
    expect(edges[0].source).toBe('find-share');
    expect(edges[0].target).toBe('inj-smb');
    expect(edges[0].data?.causalKind).toBe('finding');
  });

  it('links a complex finding the front cannot parse (portscan "host:port (service)") to a primitive port key', () => {
    // Arrange: a portscan finding whose value is "host:port (service)". The old front matcher could never
    // derive `port == 445` from that string (wrong type `portscan` != `port`, and no sub-field parse) — but
    // the backend did, and listed the node id in matchedFindingIds. This is the exact case the migration fixes.
    const fid = 'NODE_FINDING|portscan|10.0.0.1:445 (microsoft-ds)';
    const meta = new Map([['inj-nmap', {
      dependsOn: [],
      consumedFindingKeys: [{
        keyType: 'port',
        operator: 'EQ',
        value: '445',
        matchedFindingIds: [fid],
      }],
    }]]);
    const nodes = [injectorNode('inj-nmap'), findingNode(fid, 'portscan', '10.0.0.1:445 (microsoft-ds)')];
    // Act
    const edges = buildCausalEdges(nodes, id => (id ? meta.get(id) : undefined), tt);
    // Assert: the solid causal edge is drawn, driven purely by the backend match.
    expect(edges).toHaveLength(1);
    expect(edges[0].source).toBe(fid);
    expect(edges[0].target).toBe('inj-nmap');
    expect(edges[0].data?.causalKind).toBe('finding');
  });

  it('emits no finding edge when the key matched nothing on the backend (node id absent from matchedFindingIds)', () => {
    const meta = buildKillChainMeta(killChainDto);
    // inj-nmap's key matched 'find-port'; a 'find-cve' node is not in that set, and inj-nmap has no dependsOn.
    const nodes = [injectorNode('inj-nmap'), findingNode('find-cve', 'cve', 'CVE-2024-0001')];
    const edges = buildCausalEdges(nodes, id => (id ? meta.get(id) : undefined), tt);
    expect(edges).toHaveLength(0);
  });
});

// Causal execution-chain: Nmap (step-A) produces port 445; NetExec (step-B) consumes it and dependsOn
// step-A. The chain must read left-to-right in dependsOn order, with the finding on its producer step and
// a FORWARD causal edge to the consumer.
const chainDto: AttackPathDTO = {
  mode: 'full',
  attackPathNodes: [
    {
      id: 'inj-nmap',
      type: 'INJECTOR',
      label: 'Nmap',
    },
    {
      id: 'inj-smb',
      type: 'INJECTOR',
      label: 'NetExec',
    },
    {
      id: 'ep-1',
      type: 'ASSET',
      label: 'DC-01',
      ip: '10.0.0.1',
    },
    {
      id: 'NODE_FINDING|port|445',
      type: 'FINDING',
      typeFindings: 'port',
      value: '445',
      label: '445',
    },
  ],
  attackPathExecutions: [
    {
      id: 'x1',
      type: 'EXECUTION',
      ref: 'exec-1',
      stepTemplateId: 'step-A',
      findingsNodeIds: ['NODE_FINDING|port|445'],
      dependsOn: [],
    },
    {
      id: 'x2',
      type: 'EXECUTION',
      ref: 'exec-2',
      stepTemplateId: 'step-B',
      consumedFindingKeys: [{
        keyType: 'port',
        operator: 'EQ',
        value: '445',
        matchedFindingIds: ['NODE_FINDING|port|445'],
      }],
      dependsOn: ['step-A'],
    },
  ],
  attackPathEdges: [
    {
      type: 'EDGE_EXECUTIONS',
      edgeSourceId: 'inj-nmap',
      edgeTargetId: 'ep-1',
      executionIds: ['exec-1'],
    },
    {
      type: 'EDGE_EXECUTIONS',
      edgeSourceId: 'inj-smb',
      edgeTargetId: 'ep-1',
      executionIds: ['exec-2'],
    },
  ],
};

describe('buildCausalChainFlow', () => {
  it('lays the chain out in dependsOn order with a forward causal edge finding -> consumer', () => {
    // Act
    const { nodes, edges } = buildCausalChainFlow(chainDto, tt);
    const byId = Object.fromEntries(nodes.map(n => [n.id, n]));

    // The producer (nmap) sits upstream (left) of the consumer (netexec).
    expect(byId['inj-nmap'].position.x).toBeLessThan(byId['inj-smb'].position.x);
    // Endpoint nodes are keyed by DEPTH+asset: the producer (depth 0) and consumer (depth 1) are at
    // different depths, so the shared asset ep-1 renders once per depth (the causal chain is preserved).
    expect(byId['chain-ep|0|ep-1'].type).toBe(AP_FLOW_NODE_TYPE.asset);
    expect(byId['chain-ep|1|ep-1'].type).toBe(AP_FLOW_NODE_TYPE.asset);
    // The produced finding is placed on its producer step, upstream of the consuming injector.
    expect(byId['NODE_FINDING|port|445'].type).toBe(AP_FLOW_NODE_TYPE.finding);
    expect(byId['NODE_FINDING|port|445'].position.x).toBeLessThan(byId['inj-smb'].position.x);

    // The causal edge flows FORWARD: produced finding -> the injector that consumes it.
    const causal = edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE);
    expect(causal).toHaveLength(1);
    expect(causal[0].source).toBe('NODE_FINDING|port|445');
    expect(causal[0].target).toBe('inj-smb');
    expect(causal[0].data?.causalKind).toBe('finding');
  });

  it('falls back to a dashed dependsOn edge when the consumed finding value is not produced', () => {
    // NetExec depends on nmap's step but consumes a key no produced finding matches.
    const noMatch: AttackPathDTO = {
      ...chainDto,
      attackPathExecutions: [
        {
          id: 'x1',
          type: 'EXECUTION',
          ref: 'exec-1',
          stepTemplateId: 'step-A',
          findingsNodeIds: [],
          dependsOn: [],
        },
        {
          id: 'x2',
          type: 'EXECUTION',
          ref: 'exec-2',
          stepTemplateId: 'step-B',
          consumedFindingKeys: [{
            keyType: 'port',
            operator: 'EQ',
            value: '3389',
          }],
          dependsOn: ['step-A'],
        },
      ],
    };
    const { edges } = buildCausalChainFlow(noMatch, tt);
    const causal = edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE);
    expect(causal).toHaveLength(1);
    expect(causal[0].source).toBe('inj-nmap');
    expect(causal[0].target).toBe('inj-smb');
    expect(causal[0].data?.causalKind).toBe('depend');
  });

  it('draws a solid causal edge for an IS_NOT_NULL event (any produced finding of the type matches)', () => {
    // NetExec's event consumes `port IS_NOT_NULL` (fires on any port found); nmap produced port 445. The
    // value is null for IS_NOT_NULL, so presence of a matching-type finding must still emit the edge.
    const isNotNull: AttackPathDTO = {
      ...chainDto,
      attackPathExecutions: [
        {
          id: 'x1',
          type: 'EXECUTION',
          ref: 'exec-1',
          stepTemplateId: 'step-A',
          findingsNodeIds: ['NODE_FINDING|port|445'],
          dependsOn: [],
        },
        {
          id: 'x2',
          type: 'EXECUTION',
          ref: 'exec-2',
          stepTemplateId: 'step-B',
          consumedFindingKeys: [{
            keyType: 'port',
            operator: 'IS_NOT_NULL',
            value: null as unknown as string,
            eventName: 'PORT FOUND',
            matchedFindingIds: ['NODE_FINDING|port|445'],
          }],
          dependsOn: [],
        },
      ],
    };
    const { edges } = buildCausalChainFlow(isNotNull, tt);
    const causal = edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE);
    expect(causal).toHaveLength(1);
    expect(causal[0].source).toBe('NODE_FINDING|port|445');
    expect(causal[0].target).toBe('inj-smb');
    expect(causal[0].data?.causalKind).toBe('finding');
  });

  it('converges N matching findings into the consumer with a single label (Option A grouping)', () => {
    // A hub endpoint yields THREE shares; NetExec's event consumes `share_name IS_NOT_NULL`, which
    // reconciles to `share` and matches every one of them. We draw the fan-in (one grey edge per finding,
    // so the grouping is visible) but label only ONE — three stacked "Triggered …" labels over the
    // consumer was the original illegibility.
    const hub: AttackPathDTO = {
      ...chainDto,
      attackPathNodes: [
        ...(chainDto.attackPathNodes ?? []),
        {
          id: 'NODE_FINDING|share|NETLOGON',
          type: 'FINDING',
          typeFindings: 'share',
          value: 'NETLOGON',
          label: 'NETLOGON',
        },
        {
          id: 'NODE_FINDING|share|SYSVOL',
          type: 'FINDING',
          typeFindings: 'share',
          value: 'SYSVOL',
          label: 'SYSVOL',
        },
        {
          id: 'NODE_FINDING|share|CertEnroll',
          type: 'FINDING',
          typeFindings: 'share',
          value: 'CertEnroll',
          label: 'CertEnroll',
        },
      ],
      attackPathExecutions: [
        {
          id: 'x1',
          type: 'EXECUTION',
          ref: 'exec-1',
          stepTemplateId: 'step-A',
          findingsNodeIds: ['NODE_FINDING|share|NETLOGON', 'NODE_FINDING|share|SYSVOL', 'NODE_FINDING|share|CertEnroll'],
          dependsOn: [],
        },
        {
          id: 'x2',
          type: 'EXECUTION',
          ref: 'exec-2',
          stepTemplateId: 'step-B',
          consumedFindingKeys: [{
            keyType: 'share_name',
            operator: 'IS_NOT_NULL',
            value: null as unknown as string,
            eventName: 'SHARE',
            matchedFindingIds: ['NODE_FINDING|share|NETLOGON', 'NODE_FINDING|share|SYSVOL', 'NODE_FINDING|share|CertEnroll'],
          }],
          dependsOn: [],
        },
      ],
    };
    const { edges } = buildCausalChainFlow(hub, tt);
    const causal = edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE);
    // One grey edge per produced share (the fan-in), all targeting the consumer.
    expect(causal).toHaveLength(3);
    expect(causal.every(e => e.target === 'inj-smb')).toBe(true);
    expect(causal.every(e => /^NODE_FINDING\|share\|/.test(e.source ?? ''))).toBe(true);
    expect(causal.every(e => e.data?.causalKind === 'finding')).toBe(true);
    // …but only ONE of them carries the "Triggered …" label.
    expect(causal.filter(e => e.data?.label).length).toBe(1);
  });

  it('collapses more than 4 same-type findings into one cluster and routes the causal edge through it', () => {
    const fids = ['a', 'b', 'c', 'd', 'e']; // 5 shares > cap of 4
    const collapsed: AttackPathDTO = {
      ...chainDto,
      attackPathNodes: [
        {
          id: 'inj-A',
          type: 'INJECTOR',
          label: 'A',
        },
        {
          id: 'inj-C',
          type: 'INJECTOR',
          label: 'C',
        },
        {
          id: 'ep-1',
          type: 'ASSET',
          label: 'EP1',
          ip: '10.0.0.1',
        },
        ...fids.map(v => ({
          id: `NODE_FINDING|share|${v}`,
          type: 'FINDING' as const,
          typeFindings: 'share',
          value: v,
          label: v,
        })),
      ],
      attackPathExecutions: [
        {
          id: 'xA',
          type: 'EXECUTION',
          ref: 'exec-A',
          stepTemplateId: 'step-A',
          findingsNodeIds: fids.map(v => `NODE_FINDING|share|${v}`),
          dependsOn: [],
        },
        {
          id: 'xC',
          type: 'EXECUTION',
          ref: 'exec-C',
          stepTemplateId: 'step-C',
          consumedFindingKeys: [{
            keyType: 'share_name',
            operator: 'IS_NOT_NULL',
            value: null as unknown as string,
            eventName: 'SHARE',
            matchedFindingIds: fids.map(v => `NODE_FINDING|share|${v}`),
          }],
          dependsOn: ['step-A'],
        },
      ],
      attackPathEdges: [
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'inj-A',
          edgeTargetId: 'ep-1',
          executionIds: ['exec-A'],
        },
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'inj-C',
          edgeTargetId: 'ep-1',
          executionIds: ['exec-C'],
        },
      ],
    };
    // Collapsed (default): one cluster node, no individual file leaves, and ONE causal edge from the cluster.
    const collapsedFlow = buildCausalChainFlow(collapsed, tt);
    const clusterNodes = collapsedFlow.nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.findingCluster);
    expect(clusterNodes).toHaveLength(1);
    expect(clusterNodes[0].data.count).toBe(5);
    expect(collapsedFlow.nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.finding)).toHaveLength(0);
    const collapsedCausal = collapsedFlow.edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE && e.data?.causalKind === 'finding');
    expect(collapsedCausal).toHaveLength(1);
    expect(collapsedCausal[0].source).toBe(clusterNodes[0].id);
    expect(collapsedCausal[0].target).toBe('inj-C');

    // Expanded: the 5 findings render individually (no cluster) and the fan-in has 5 edges, one labelled.
    const expandedFlow = buildCausalChainFlow(collapsed, tt, new Set([clusterNodes[0].id]));
    expect(expandedFlow.nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.findingCluster)).toHaveLength(0);
    expect(expandedFlow.nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.finding)).toHaveLength(5);
    const expandedCausal = expandedFlow.edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE && e.data?.causalKind === 'finding');
    expect(expandedCausal).toHaveLength(5);
    expect(expandedCausal.filter(e => e.data?.label).length).toBe(1);

    // causalSourceByFinding: while collapsed, every one of the 5 findings resolves to the cluster that
    // represents them (there is no node of their own to resolve to); once expanded, each resolves to
    // itself. A caller seeding a highlight/focus on a raw finding id must go through this map first, or
    // it seeds on an id with no matching node at all (see the scopeChainFlowToSeeds fallback test).
    fids.forEach(v => expect(collapsedFlow.causalSourceByFinding.get(`NODE_FINDING|share|${v}`)).toBe(clusterNodes[0].id));
    fids.forEach(v => expect(expandedFlow.causalSourceByFinding.get(`NODE_FINDING|share|${v}`)).toBe(`NODE_FINDING|share|${v}`));
  });

  it('routes findings on overflow-hidden endpoints through the "+N" endpoint cluster in causalSourceByFinding', () => {
    // 6 distinct endpoints at one depth > cap of 4: ep-5/ep-6 collapse into the `chain-epc|0` endpoint
    // cluster and their findings never render at all — causalSourceByFinding must resolve those findings
    // to that cluster (the third resolution case, alongside "itself" and "its type cluster" covered
    // above), so a seed/highlight on one of them still anchors on something rendered.
    const eps = ['1', '2', '3', '4', '5', '6'];
    const overflow: AttackPathDTO = {
      ...chainDto,
      attackPathNodes: [
        {
          id: 'inj-A',
          type: 'INJECTOR',
          label: 'A',
        },
        ...eps.map(v => ({
          id: `ep-${v}`,
          type: 'ASSET' as const,
          label: `EP${v}`,
          ip: `10.0.0.${v}`,
        })),
        ...eps.map(v => ({
          id: `NODE_FINDING|cred|${v}`,
          type: 'FINDING' as const,
          typeFindings: 'credentials',
          value: `cred-${v}`,
          label: `cred-${v}`,
        })),
      ],
      attackPathExecutions: eps.map(v => ({
        id: `x${v}`,
        type: 'EXECUTION' as const,
        ref: `exec-${v}`,
        stepTemplateId: 'step-A',
        findingsNodeIds: [`NODE_FINDING|cred|${v}`],
        dependsOn: [],
      })),
      attackPathEdges: eps.map(v => ({
        type: 'EDGE_EXECUTIONS' as const,
        edgeSourceId: 'inj-A',
        edgeTargetId: `ep-${v}`,
        executionIds: [`exec-${v}`],
      })),
    };

    const flow = buildCausalChainFlow(overflow, tt);
    const epCluster = flow.nodes.find(n => n.id === 'chain-epc|0');
    expect(epCluster).toBeDefined();
    expect(epCluster!.data.count).toBe(2);
    // The hidden endpoints' findings have no node of their own; they resolve to the endpoint cluster.
    ['5', '6'].forEach((v) => {
      expect(flow.nodes.some(n => n.id === `NODE_FINDING|cred|${v}`)).toBe(false);
      expect(flow.causalSourceByFinding.get(`NODE_FINDING|cred|${v}`)).toBe('chain-epc|0');
    });
    // The visible endpoints' findings render individually and resolve to themselves.
    ['1', '2', '3', '4'].forEach(v => expect(flow.causalSourceByFinding.get(`NODE_FINDING|cred|${v}`)).toBe(`NODE_FINDING|cred|${v}`));
  });

  it('anchors the causal edge on the finding of the resolved producer, not another injector sharing the type', () => {
    // Two injectors each produce a `share` finding; a consumer whose event `share_name IS_NOT_NULL` matches
    // BOTH depends (dependsOn, #6985) only on producer A. The edge must anchor on A's finding, never B's.
    const twoProducers: AttackPathDTO = {
      ...chainDto,
      attackPathNodes: [
        {
          id: 'inj-A',
          type: 'INJECTOR',
          label: 'A',
        },
        {
          id: 'inj-B',
          type: 'INJECTOR',
          label: 'B',
        },
        {
          id: 'inj-C',
          type: 'INJECTOR',
          label: 'C',
        },
        {
          id: 'ep-1',
          type: 'ASSET',
          label: 'EP1',
          ip: '10.0.0.1',
        },
        {
          id: 'ep-2',
          type: 'ASSET',
          label: 'EP2',
          ip: '10.0.0.2',
        },
        {
          id: 'NODE_FINDING|share|shareA',
          type: 'FINDING',
          typeFindings: 'share',
          value: 'shareA',
          label: 'shareA',
        },
        {
          id: 'NODE_FINDING|share|shareB',
          type: 'FINDING',
          typeFindings: 'share',
          value: 'shareB',
          label: 'shareB',
        },
      ],
      attackPathExecutions: [
        {
          id: 'xA',
          type: 'EXECUTION',
          ref: 'exec-A',
          stepTemplateId: 'step-A',
          findingsNodeIds: ['NODE_FINDING|share|shareA'],
          dependsOn: [],
        },
        {
          id: 'xB',
          type: 'EXECUTION',
          ref: 'exec-B',
          stepTemplateId: 'step-B',
          findingsNodeIds: ['NODE_FINDING|share|shareB'],
          dependsOn: [],
        },
        {
          id: 'xC',
          type: 'EXECUTION',
          ref: 'exec-C',
          stepTemplateId: 'step-C',
          consumedFindingKeys: [{
            keyType: 'share_name',
            operator: 'IS_NOT_NULL',
            value: null as unknown as string,
            eventName: 'SHARE',
            // The backend matched BOTH shares (the SHARE event); the front's dependency-preference then
            // narrows the fan-in to the resolved producer's finding (shareA), never shareB's.
            matchedFindingIds: ['NODE_FINDING|share|shareA', 'NODE_FINDING|share|shareB'],
          }],
          dependsOn: ['step-A'],
        },
      ],
      attackPathEdges: [
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'inj-A',
          edgeTargetId: 'ep-1',
          executionIds: ['exec-A'],
        },
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'inj-B',
          edgeTargetId: 'ep-2',
          executionIds: ['exec-B'],
        },
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'inj-C',
          edgeTargetId: 'ep-1',
          executionIds: ['exec-C'],
        },
      ],
    };
    const { edges } = buildCausalChainFlow(twoProducers, tt);
    const causal = edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE && e.data?.causalKind === 'finding');
    expect(causal).toHaveLength(1);
    expect(causal[0].target).toBe('inj-C');
    expect(causal[0].source).toBe('NODE_FINDING|share|shareA');
  });

  it('merges same-depth injectors hitting the same asset onto one shared endpoint node', () => {
    // Arrange: two INDEPENDENT injectors (no dependsOn → both at depth 0) target the SAME asset ep-1.
    const parallel: AttackPathDTO = {
      ...chainDto,
      attackPathExecutions: [
        {
          id: 'x1',
          type: 'EXECUTION',
          ref: 'exec-1',
          stepTemplateId: 'step-A',
          findingsNodeIds: [],
          dependsOn: [],
        },
        {
          id: 'x2',
          type: 'EXECUTION',
          ref: 'exec-2',
          stepTemplateId: 'step-B',
          findingsNodeIds: [],
          dependsOn: [],
        },
      ],
    };

    // Act
    const { nodes, edges } = buildCausalChainFlow(parallel, tt);

    // Assert: a single endpoint node for ep-1 (keyed by depth 0 + asset), not one per injector.
    const epNodes = nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.asset);
    expect(epNodes).toHaveLength(1);
    expect(epNodes[0].id).toBe('chain-ep|0|ep-1');
    // Both injectors point their own labelled edge at that shared endpoint.
    const toEp = edges.filter(e => e.target === 'chain-ep|0|ep-1');
    expect(toEp.map(e => e.source).sort()).toEqual(['inj-nmap', 'inj-smb']);
    // No causal edge: independent injectors share the asset but form no kill chain.
    expect(edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE)).toHaveLength(0);
  });

  it('renders an endpoint reached only by an endpoint-local action without an injector node or self arrow', () => {
    // Arrange: a Whoami-style local action — the execution edge's source IS its target endpoint.
    const selfLoop: AttackPathDTO = {
      mode: 'full',
      attackPathNodes: [
        {
          id: 'ep-1',
          type: 'ASSET',
          label: 'DC-01',
          ip: '10.0.0.1',
        },
        {
          id: 'NODE_FINDING|username|bob',
          type: 'FINDING',
          typeFindings: 'username',
          value: 'bob',
          label: 'bob',
        },
      ],
      attackPathExecutions: [
        {
          id: 'x-local',
          type: 'EXECUTION',
          ref: 'exec-local',
          stepTemplateId: 'step-L',
          contractName: 'Whoami',
          findingsNodeIds: ['NODE_FINDING|username|bob'],
          dependsOn: [],
        },
      ],
      attackPathEdges: [
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'ep-1',
          edgeTargetId: 'ep-1',
          executionIds: ['exec-local'],
        },
      ],
    };
    // Act
    const { nodes, edges } = buildCausalChainFlow(selfLoop, tt);
    const byId = Object.fromEntries(nodes.map(n => [n.id, n]));
    // Assert: the endpoint and its finding render…
    expect(byId['chain-ep|0|ep-1'].type).toBe(AP_FLOW_NODE_TYPE.asset);
    expect(byId['NODE_FINDING|username|bob'].type).toBe(AP_FLOW_NODE_TYPE.finding);
    // …but no injector node is drawn for the local action and no arrow loops onto the endpoint.
    expect(nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.injector)).toHaveLength(0);
    expect(edges.find(e => e.source === 'ep-1' || e.target === 'ep-1')).toBeUndefined();
    // Every emitted edge resolves to placed nodes (nothing dangles on the suppressed injector).
    const nodeIds = new Set(nodes.map(n => n.id));
    expect(edges.every(e => nodeIds.has(e.source) && nodeIds.has(e.target))).toBe(true);
  });

  it('emits no causal edge into an endpoint-local step (its injector node is suppressed)', () => {
    // Arrange: nmap produces port 445 on ep-1; a LOCAL action on ep-1 consumes it (dependsOn step-A).
    const localConsumer: AttackPathDTO = {
      mode: 'full',
      attackPathNodes: [
        {
          id: 'inj-nmap',
          type: 'INJECTOR',
          label: 'Nmap',
        },
        {
          id: 'ep-1',
          type: 'ASSET',
          label: 'DC-01',
          ip: '10.0.0.1',
        },
        {
          id: 'NODE_FINDING|port|445',
          type: 'FINDING',
          typeFindings: 'port',
          value: '445',
          label: '445',
        },
      ],
      attackPathExecutions: [
        {
          id: 'x1',
          type: 'EXECUTION',
          ref: 'exec-1',
          stepTemplateId: 'step-A',
          findingsNodeIds: ['NODE_FINDING|port|445'],
          dependsOn: [],
        },
        {
          id: 'x-local',
          type: 'EXECUTION',
          ref: 'exec-local',
          stepTemplateId: 'step-L',
          contractName: 'Whoami',
          consumedFindingKeys: [{
            keyType: 'port',
            operator: 'EQ',
            value: '445',
            matchedFindingIds: ['NODE_FINDING|port|445'],
          }],
          dependsOn: ['step-A'],
        },
      ],
      attackPathEdges: [
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'inj-nmap',
          edgeTargetId: 'ep-1',
          executionIds: ['exec-1'],
        },
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'ep-1',
          edgeTargetId: 'ep-1',
          executionIds: ['exec-local'],
        },
      ],
    };
    // Act
    const { nodes, edges } = buildCausalChainFlow(localConsumer, tt);
    // Assert: no causal edge targets the raw endpoint id (its "injector" node was never placed), and
    // every edge still resolves to placed nodes.
    const nodeIds = new Set(nodes.map(n => n.id));
    expect(edges.filter(e => e.type === AP_FLOW_CAUSAL_EDGE_TYPE && e.target === 'ep-1')).toHaveLength(0);
    expect(edges.every(e => nodeIds.has(e.source) && nodeIds.has(e.target))).toBe(true);
  });
});

describe('scopeChainFlowToSeeds', () => {
  // Same "5 shares > cap of 4" shape as the buildCausalChainFlow collapse test above: the 5 findings
  // never render individually, only their `chain-fc|...` cluster does.
  const fids = ['a', 'b', 'c', 'd', 'e'];
  const collapsed: AttackPathDTO = {
    ...chainDto,
    attackPathNodes: [
      {
        id: 'inj-A',
        type: 'INJECTOR',
        label: 'A',
      },
      {
        id: 'ep-1',
        type: 'ASSET',
        label: 'EP1',
        ip: '10.0.0.1',
      },
      ...fids.map(v => ({
        id: `NODE_FINDING|share|${v}`,
        type: 'FINDING' as const,
        typeFindings: 'share',
        value: v,
        label: v,
      })),
    ],
    attackPathExecutions: [
      {
        id: 'xA',
        type: 'EXECUTION',
        ref: 'exec-A',
        stepTemplateId: 'step-A',
        findingsNodeIds: fids.map(v => `NODE_FINDING|share|${v}`),
        dependsOn: [],
      },
    ],
    attackPathEdges: [
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'inj-A',
        edgeTargetId: 'ep-1',
        executionIds: ['exec-A'],
      },
    ],
  };

  it('falls back to the full chain rather than an empty focus when the seed is a collapsed finding', () => {
    const chainFlow = buildCausalChainFlow(collapsed, tt);
    // The seed is one of the 5 collapsed findings — it has no node of its own, only its cluster does.
    const scoped = scopeChainFlowToSeeds(chainFlow, new Set(['NODE_FINDING|share|a']));

    expect(scoped.nodes).not.toHaveLength(0);
    expect(scoped.nodes).toEqual(chainFlow.nodes);
    expect(scoped.edges).toEqual(chainFlow.edges);
  });

  it('scopes down to the seed and its connected nodes when the seed is actually rendered', () => {
    const chainFlow = buildCausalChainFlow(collapsed, tt);
    const injectorNode = chainFlow.nodes.find(n => n.id === 'inj-A');

    const scoped = scopeChainFlowToSeeds(chainFlow, new Set(['inj-A']));

    expect(scoped.nodes).toContainEqual(injectorNode);
    expect(scoped.nodes.length).toBeLessThan(chainFlow.nodes.length);
  });

  it('scopes down correctly, instead of falling back to the full chain, when the seed is resolved through causalSourceByFinding first', () => {
    // Reproduces picking one of the 5 collapsed findings from a drawer/summary list (not clicking an
    // already-rendered graph node): the caller must resolve the raw finding id to whatever actually
    // represents it (its cluster, here) before seeding, exactly as SimulationAttackPath's
    // effectiveSelectedFindingId does — seeding on the raw id instead is the previous bug, covered by
    // the "falls back to the full chain" test above.
    const chainFlow = buildCausalChainFlow(collapsed, tt);
    const resolvedSeed = chainFlow.causalSourceByFinding.get('NODE_FINDING|share|a');
    // Assert the mapping itself first (not a silent cast): if causalSourceByFinding ever stops covering
    // a collapsed finding, this must fail HERE, not as a confusing downstream scoping mismatch.
    expect(resolvedSeed).toBeDefined();

    const scoped = scopeChainFlowToSeeds(chainFlow, new Set([resolvedSeed!]));

    expect(scoped.nodes.map(n => n.id).sort()).toEqual(['chain-ep|0|ep-1', 'inj-A', resolvedSeed].sort());
  });
});

describe('buildFindingPathFlow', () => {
  const pathDto: AttackPathDTO = {
    mode: 'collapsed',
    attackPathNodes: [
      {
        id: 'inj-A',
        type: 'INJECTOR',
        label: 'NetExec',
      },
      {
        id: 'ep-1',
        type: 'ASSET',
        label: 'DC-01',
        ref: 'dc-01',
        status: 'RED',
        findingCounts: { username: 1 },
      },
    ],
    attackPathEdges: [
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'inj-A',
        edgeTargetId: 'ep-1',
      },
      {
        type: 'EDGE_EXECUTIONS',
        edgeSourceId: 'ep-1',
        edgeTargetId: 'ep-1',
      },
    ],
  };
  const finding = {
    endpointNodeId: 'ep-1',
    endpointKey: 'dc-01',
    type: 'username',
    value: 'bob',
  };

  it('never lists the endpoint itself as a reaching injector (self-loop filtered)', () => {
    // Act
    const { nodes, edges } = buildFindingPathFlow(pathDto, finding, tt);
    // Assert: only the real injector renders and points at the endpoint; no ep-1 -> ep-1 arrow.
    expect(nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.injector).map(n => n.id)).toEqual(['inj-A']);
    expect(edges.find(e => e.source === 'inj-A' && e.target === 'ep-1')).toBeDefined();
    expect(edges.find(e => e.source === 'ep-1' && e.target === 'ep-1')).toBeUndefined();
  });

  it('still renders the endpoint and its finding clusters when only a local action reached it', () => {
    // Arrange: drop the real injector edge — the endpoint is reached by the local action alone.
    const localOnly: AttackPathDTO = {
      ...pathDto,
      attackPathEdges: [
        {
          type: 'EDGE_EXECUTIONS',
          edgeSourceId: 'ep-1',
          edgeTargetId: 'ep-1',
        },
      ],
    };
    // Act
    const { nodes } = buildFindingPathFlow(localOnly, finding, tt);
    // Assert: no injector, but the endpoint and its per-type cluster still render.
    expect(nodes.filter(n => n.type === AP_FLOW_NODE_TYPE.injector)).toHaveLength(0);
    expect(nodes.find(n => n.id === 'ep-1')?.type).toBe(AP_FLOW_NODE_TYPE.asset);
    expect(nodes.find(n => n.id === 'path-cl-type|username|dc-01')?.type).toBe(AP_FLOW_NODE_TYPE.findingCluster);
  });
});

// The endpoint node shows a single relevant IP instead of the full comma-separated list (#5048):
// the asset's live seen IP when known, else the first IPv4 of the frozen list, else its first entry.
describe('displayIp', () => {
  it('prefers the seen IP over the frozen list', () => {
    expect(displayIp('203.0.113.7', '10.0.0.1,10.0.0.2')).toBe('203.0.113.7');
  });

  it('trims the seen IP', () => {
    expect(displayIp(' 203.0.113.7 ', undefined)).toBe('203.0.113.7');
  });

  it('ignores a blank seen IP and falls back to the list', () => {
    expect(displayIp('   ', '192.168.1.10')).toBe('192.168.1.10');
  });

  it('falls back to the first IPv4 of a mixed list', () => {
    expect(displayIp(undefined, 'fe80::1, 2001:db8::1, 192.168.1.10, 10.0.0.2')).toBe('192.168.1.10');
  });

  it('falls back to the first entry when the list has no IPv4', () => {
    expect(displayIp(undefined, 'fe80::1, 2001:db8::1')).toBe('fe80::1');
  });

  it('keeps a single-IP endpoint unchanged', () => {
    expect(displayIp(undefined, '192.168.1.1')).toBe('192.168.1.1');
  });

  it('returns undefined when neither a seen IP nor a list is available', () => {
    expect(displayIp(undefined, undefined)).toBeUndefined();
    expect(displayIp('', '')).toBeUndefined();
    expect(displayIp(undefined, ' , ')).toBeUndefined();
  });
});
