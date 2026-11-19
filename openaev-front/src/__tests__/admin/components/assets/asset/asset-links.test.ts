import { describe, expect, it } from 'vitest';

import { ASSET_LINK_TYPES, assetLinkTypeLabel, isLinkCandidate, toAssetLinkRows } from '../../../../../admin/components/assets/asset/asset-links';
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
    });
    expect(new Set(rows.map(row => row.key)).size).toBe(2);
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
