import { describe, expect, it } from 'vitest';

import { AP_FLOW_EDGE_TYPE, AP_FLOW_NODE_TYPE, buildAttackPathFlow } from '../../../../../../admin/components/simulations/simulation/attack_path_poc/attack-path-poc-flow-helpers';
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
