import { describe, expect, it } from 'vitest';

import { collectFindingImpact } from '../../../../admin/components/findings/FindingImpactUtils';
import type { RelatedFindingOutput } from '../../../../utils/api-types';

const occurrence = (overrides: Partial<RelatedFindingOutput>): RelatedFindingOutput => ({
  finding_id: 'f',
  finding_type: 'portscan',
  finding_value: '10.0.0.1:9100',
  finding_created_at: '2026-11-18T00:00:00Z',
  finding_updated_at: '2026-11-18T00:00:00Z',
  finding_assets: [],
  finding_inject: {
    inject_id: 'i',
    inject_title: 'Nmap',
  },
  ...overrides,
} as RelatedFindingOutput);

describe('collectFindingImpact', () => {
  it('returns empty lists when there is no occurrence', () => {
    expect(collectFindingImpact([])).toEqual({
      assets: [],
      assetGroups: [],
      scenarios: [],
      simulations: [],
    });
  });

  it('merges every occurrence, deduplicated by id and sorted by name', () => {
    const impact = collectFindingImpact([
      occurrence({
        finding_assets: [
          {
            asset_id: 'a2',
            asset_name: 'web-02',
          },
          {
            asset_id: 'a1',
            asset_name: 'db-01.internal.example.com',
          },
        ],
        finding_asset_groups: [{
          asset_group_id: 'g1',
          asset_group_name: 'Production',
        }],
        finding_scenario: {
          scenario_id: 's1',
          scenario_name: 'Weekly scan',
        },
        finding_simulation: {
          exercise_id: 'e1',
          exercise_name: 'Scan 12 Nov',
        } as RelatedFindingOutput['finding_simulation'],
      }),
      occurrence({
        finding_assets: [{
          asset_id: 'a1',
          asset_name: 'db-01.internal.example.com',
        }],
        finding_asset_groups: [{
          asset_group_id: 'g1',
          asset_group_name: 'Production',
        }],
        finding_scenario: {
          scenario_id: 's1',
          scenario_name: 'Weekly scan',
        },
        finding_simulation: {
          exercise_id: 'e2',
          exercise_name: 'Scan 05 Nov',
        } as RelatedFindingOutput['finding_simulation'],
      }),
    ]);

    expect(impact.assets.map(a => a.asset_id)).toEqual(['a1', 'a2']);
    expect(impact.assets[0].asset_name).toBe('db-01.internal.example.com');
    expect(impact.assetGroups.map(g => g.asset_group_id)).toEqual(['g1']);
    expect(impact.scenarios.map(s => s.scenario_id)).toEqual(['s1']);
    expect(impact.simulations.map(s => s.exercise_id)).toEqual(['e2', 'e1']);
  });

  it('has no scenario or simulation for an atomic testing occurrence', () => {
    const impact = collectFindingImpact([
      occurrence({
        finding_assets: [{
          asset_id: 'a1',
          asset_name: 'host',
        }],
      }),
    ]);

    expect(impact.assets).toHaveLength(1);
    expect(impact.scenarios).toEqual([]);
    expect(impact.simulations).toEqual([]);
  });
});
