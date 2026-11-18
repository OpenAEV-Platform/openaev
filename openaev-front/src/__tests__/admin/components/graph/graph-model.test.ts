import { describe, expect, it } from 'vitest';

import { nodeCounterpart } from '../../../../admin/components/graph/graph-model';

describe('nodeCounterpart', () => {
  it('matches a CVE finding on its CVE id', () => {
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'CVE',
      node_label: 'CVE-2024-3094',
    })).toEqual({
      kind: 'vulnerabilities',
      key: 'CVE-2024-3094',
    });
  });

  it('reads the finding type whatever its case', () => {
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'cve',
      node_label: 'CVE-2021-44228',
    })?.key).toBe('CVE-2021-44228');
  });

  it('has no counterpart for another finding type', () => {
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'PORT',
      node_label: '443',
    })).toBeNull();
  });

  it('has no counterpart for a node the graph only knows by name', () => {
    expect(nodeCounterpart({
      node_type: 'ASSET',
      node_subtype: 'Endpoint',
      node_label: 'srv-web-01',
    })).toBeNull();
    expect(nodeCounterpart({
      node_type: 'SCENARIO',
      node_subtype: null,
      node_label: 'Ransomware',
    })).toBeNull();
  });

  it('has no counterpart for a CVE finding without a value', () => {
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'CVE',
      node_label: '',
    })).toBeNull();
  });
});
