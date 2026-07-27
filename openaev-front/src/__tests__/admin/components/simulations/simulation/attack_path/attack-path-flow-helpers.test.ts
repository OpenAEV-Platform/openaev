import { describe, expect, it } from 'vitest';

import { AP_ALL_ENDPOINTS, AP_FLOW_CAUSAL_EDGE_TYPE, AP_FLOW_EDGE_TYPE, AP_FLOW_NODE_TYPE, applyFindingFilter, type AttackPathFlowNode, buildAttackPathFlow, buildCausalChainFlow, buildCausalEdges, buildClusteredAttackPathFlow, buildKillChainMeta, maskFindingValue } from '../../../../../../admin/components/simulations/simulation/attack_path/attack-path-flow-helpers';
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
    }]);
    expect(meta.get('inj-smb')?.consumedFindingKeys).toEqual([{
      keyType: 'share_name',
      operator: 'EQ',
      value: 'ADMIN$',
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
  it('draws a solid finding edge, reconciling the primitive key type (share_name -> file)', () => {
    // Arrange
    const meta = buildKillChainMeta(killChainDto);
    const nodes = [injectorNode('inj-smb'), findingNode('find-share', 'file', 'ADMIN$')];
    // Act
    const edges = buildCausalEdges(nodes, id => (id ? meta.get(id) : undefined), tt);
    // Assert
    expect(edges).toHaveLength(1);
    expect(edges[0].type).toBe(AP_FLOW_CAUSAL_EDGE_TYPE);
    expect(edges[0].source).toBe('find-share');
    expect(edges[0].target).toBe('inj-smb');
    expect(edges[0].data?.causalKind).toBe('finding');
  });

  it('emits no edge when no produced finding matches the consumed key', () => {
    const meta = buildKillChainMeta(killChainDto);
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
});
