import { describe, expect, it } from 'vitest';

import { ASSET_LINK_TYPES, assetLinkTypeLabel, groupAssetLinkRows, isLinkCandidate, previewRows, RELATIONSHIP_PREVIEW, toAssetLinkRows } from '../../../../../admin/components/assets/asset/asset-links';
import { type AssetLinkOutput } from '../../../../../utils/api-types';

const links: AssetLinkOutput[] = [
  {
    asset_link_source_id: 'host',
    asset_link_source_name: 'srv-01',
    asset_link_target_id: 'identity',
    asset_link_target_name: 'alice@corp',
    asset_link_type: 'LOGGED_ON',
  },
  {
    asset_link_source_id: 'app',
    asset_link_source_name: 'billing',
    asset_link_target_id: 'host',
    asset_link_target_name: 'srv-01',
    asset_link_type: 'RUNS_ON',
  },
  // Incomplete rows (a deleted end) are dropped rather than rendered half empty.
  {
    asset_link_source_id: 'host',
    asset_link_type: 'USES',
  },
];

describe('asset-links', () => {
  it('maps every backend link type to a label', () => {
    expect(ASSET_LINK_TYPES.map(type => type.value)).toEqual(['CONNECTS_TO', 'DEPENDS_ON', 'HOSTS', 'LOGGED_ON', 'RUNS_ON', 'USES']);
    expect(assetLinkTypeLabel('HOSTS')).toBe('Hosts');
    expect(assetLinkTypeLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(assetLinkTypeLabel(undefined)).toBe('-');
  });

  it('gives every link type a one-line meaning distinct from its label', () => {
    ASSET_LINK_TYPES.forEach((type) => {
      expect(type.description.length).toBeGreaterThan(0);
      expect(type.description).not.toBe(type.label);
    });
    expect(new Set(ASSET_LINK_TYPES.map(type => type.description)).size).toBe(ASSET_LINK_TYPES.length);
  });

  it('builds outgoing and incoming rows seen from the asset', () => {
    const rows = toAssetLinkRows(links, 'host');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({
      outgoing: true,
      otherId: 'identity',
      sourceId: 'host',
      targetId: 'identity',
      type: 'LOGGED_ON',
    });
    expect(rows[1]).toMatchObject({
      outgoing: false,
      otherId: 'app',
      sourceId: 'app',
      targetId: 'host',
      sourceName: 'billing',
      otherName: 'billing',
    });
    expect(rows[0].otherName).toBe('alice@corp');
    expect(new Set(rows.map(row => row.key)).size).toBe(2);
  });

  it('groups the overview card by type and direction, outgoing first, names sorted', () => {
    const many: AssetLinkOutput[] = [
      ...links,
      {
        asset_link_source_id: 'host',
        asset_link_source_name: 'srv-01',
        asset_link_target_id: 'bob',
        asset_link_target_name: 'bob@corp',
        asset_link_type: 'LOGGED_ON',
      },
      {
        asset_link_source_id: 'host',
        asset_link_source_name: 'srv-01',
        asset_link_target_id: 'dc',
        asset_link_target_name: 'dc-01',
        asset_link_type: 'CONNECTS_TO',
      },
    ];
    const groups = groupAssetLinkRows(toAssetLinkRows(many, 'host'));
    expect(groups.map(group => [group.type, group.outgoing])).toEqual([
      ['CONNECTS_TO', true],
      ['LOGGED_ON', true],
      ['RUNS_ON', false],
    ]);
    expect(groups[1].rows.map(row => row.otherName)).toEqual(['alice@corp', 'bob@corp']);
  });

  it('shows a few linked assets per line and counts the rest behind +N more', () => {
    const rows = Array.from({ length: RELATIONSHIP_PREVIEW + 3 }, (_, index) => ({
      key: `k${index}`,
      outgoing: true,
      sourceId: 'host',
      sourceName: 'srv-01',
      targetId: `t${index}`,
      targetName: `t${index}`,
      otherId: `t${index}`,
      otherName: `t${index}`,
      type: 'USES' as const,
    }));
    expect(previewRows(rows, false)).toMatchObject({ hidden: 3 });
    expect(previewRows(rows, false).shown).toHaveLength(RELATIONSHIP_PREVIEW);
    expect(previewRows(rows, true)).toMatchObject({ hidden: 0 });
    expect(previewRows(rows, true).shown).toHaveLength(RELATIONSHIP_PREVIEW + 3);
    expect(previewRows(rows.slice(0, 2), false)).toMatchObject({ hidden: 0 });
  });

  it('offers neither the asset itself nor an existing link of the same type', () => {
    const rows = toAssetLinkRows(links, 'host');
    expect(isLinkCandidate(rows, 'host', 'host', 'USES')).toBe(false);
    expect(isLinkCandidate(rows, 'host', 'identity', 'LOGGED_ON')).toBe(false);
    expect(isLinkCandidate(rows, 'host', 'identity', 'USES')).toBe(true);
    // An incoming link does not prevent the reverse outgoing one.
    expect(isLinkCandidate(rows, 'host', 'app', 'RUNS_ON')).toBe(true);
  });
});
