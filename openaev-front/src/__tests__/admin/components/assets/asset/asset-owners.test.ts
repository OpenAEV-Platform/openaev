import { describe, expect, it } from 'vitest';

import {
  groupAssetOwnerRows,
  isOwnerCandidate,
  ownerLink,
  relationSourceLabel,
  toAssetOwnerRows,
} from '../../../../../admin/components/assets/asset/asset-owners';

describe('asset owners', () => {
  const owners = [
    {
      asset_owner_id: 'o1',
      asset_owner_role: 'MANAGED_BY' as const,
      asset_owner_team_id: 't1',
      asset_owner_name: 'Infra',
      asset_owner_source: 'COLLECTOR' as const,
    },
    {
      asset_owner_id: 'o2',
      asset_owner_role: 'OWNED_BY' as const,
      asset_owner_user_id: 'u1',
      asset_owner_name: 'Alice',
      asset_owner_source: 'MANUAL' as const,
    },
    { asset_owner_id: 'o3' },
  ];

  it('maps persons and teams, drops incomplete rows and sorts by name', () => {
    const rows = toAssetOwnerRows(owners);
    expect(rows.map(row => row.id)).toEqual(['o2', 'o1']);
    expect(rows[0]).toMatchObject({
      kind: 'person',
      targetId: 'u1',
    });
    expect(rows[1]).toMatchObject({
      kind: 'team',
      targetId: 't1',
    });
  });

  it('groups business owners before technical owners', () => {
    const groups = groupAssetOwnerRows(toAssetOwnerRows(owners));
    expect(groups.map(group => group.role)).toEqual(['OWNED_BY', 'MANAGED_BY']);
  });

  it('does not offer a person or team already holding the role', () => {
    const rows = toAssetOwnerRows(owners);
    expect(isOwnerCandidate(rows, 'OWNED_BY', 'person', 'u1')).toBe(false);
    expect(isOwnerCandidate(rows, 'MANAGED_BY', 'person', 'u1')).toBe(true);
    expect(isOwnerCandidate(rows, 'MANAGED_BY', 'team', 't1')).toBe(false);
  });

  it('labels automatic sources only', () => {
    expect(relationSourceLabel('MANUAL')).toBeNull();
    expect(relationSourceLabel(undefined)).toBeNull();
    expect(relationSourceLabel('AGENT')).toBe('Agent');
    expect(relationSourceLabel('COLLECTOR')).toBe('Collector');
    expect(relationSourceLabel('SYNC')).toBe('Sync');
  });

  it('links to the person or team page', () => {
    const rows = toAssetOwnerRows(owners);
    expect(ownerLink(rows[0])).toBe('/admin/persons/u1');
    expect(ownerLink(rows[1])).toBe('/admin/teams/t1');
  });
});
