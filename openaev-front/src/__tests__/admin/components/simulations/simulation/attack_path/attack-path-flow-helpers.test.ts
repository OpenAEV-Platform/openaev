import { describe, expect, it } from 'vitest';

import { AP_ALL_ENDPOINTS, AP_FLOW_EDGE_TYPE, AP_FLOW_NODE_TYPE, applyFindingFilter, buildAttackPathFlow, buildClusteredAttackPathFlow, maskFindingValue } from '../../../../../../admin/components/simulations/simulation/attack_path/attack-path-flow-helpers';
import type { AttackPathDTO } from '../../../../../../utils/api-types';

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
    const { nodes, edges } = buildClusteredAttackPathFlow(clusteredDto, new Map());
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
    // edges: injector -> shared hub (labelled with the injector's reached-endpoint count) -> findings
    expect(edges.find(e => e.id === 'inj-cl-ep-all')?.data?.count).toBe(2);
    expect(edges.filter(e => e.source === 'cl-ep-all')).toHaveLength(2);
    expect(edges[0].type).toBe(AP_FLOW_EDGE_TYPE);
  });

  it('replaces the shared hub with the deduped real endpoints when expanded', () => {
    const { nodes, edges } = buildClusteredAttackPathFlow(clusteredDto, new Map([[AP_ALL_ENDPOINTS, 15]]));
    const ids = nodes.map(n => n.id);
    expect(ids).toContain('ep1');
    expect(ids).toContain('ep2');
    expect(nodes.find(n => n.id === 'ep1')?.type).toBe(AP_FLOW_NODE_TYPE.asset);
    expect(edges.find(e => e.id === 'cl-ep-all-ep1')?.target).toBe('ep1');
  });
});
