import { type AssetLinkInput, type AssetLinkOutput } from '../../../../utils/api-types';

export type AssetLinkType = AssetLinkInput['asset_link_type'];

// The overview card shows this many linked assets per group before "+N more".
export const RELATIONSHIP_PREVIEW = 5;

// The link types the backend accepts, with the i18n key of the verb read "source <verb> target",
// the i18n key of its inverse read from the target ("target <inverse> source") and the i18n key of
// its one-line meaning, shown under each option of the picker.
export const ASSET_LINK_TYPES: {
  value: AssetLinkType;
  label: string;
  inverseLabel: string;
  description: string;
}[] = [
  {
    value: 'CONNECTS_TO',
    label: 'Connects to',
    inverseLabel: 'Connected from',
    description: 'Reaches the linked asset over the network',
  },
  {
    value: 'DEPENDS_ON',
    label: 'Depends on',
    inverseLabel: 'Depended on by',
    description: 'Needs the linked asset to work',
  },
  {
    value: 'HOSTS',
    label: 'Hosts',
    inverseLabel: 'Hosted by',
    description: 'Runs the linked asset on its own hardware or hypervisor',
  },
  {
    value: 'LOGGED_ON',
    label: 'Logged on',
    inverseLabel: 'Has a session from',
    description: 'Has a user session open on the linked asset',
  },
  {
    value: 'RUNS_ON',
    label: 'Runs on',
    inverseLabel: 'Runs',
    description: 'Is installed and executed on the linked asset',
  },
  {
    value: 'USES',
    label: 'Uses',
    inverseLabel: 'Used by',
    description: 'Calls a service the linked asset provides',
  },
];

export const assetLinkTypeLabel = (type?: string): string => {
  return ASSET_LINK_TYPES.find(linkType => linkType.value === type)?.label ?? type ?? '-';
};

// The wording of a link as read from the asset page: the verb for an outgoing link ("this asset
// Hosts X"), its inverse for an incoming one ("this asset is Hosted by X"), so the line stays true.
export const assetLinkDirectionLabel = (type: string | undefined, outgoing: boolean): string => {
  if (outgoing) {
    return assetLinkTypeLabel(type);
  }
  return ASSET_LINK_TYPES.find(linkType => linkType.value === type)?.inverseLabel ?? type ?? '-';
};

export type AssetLinkRow = {
  key: string;
  outgoing: boolean;
  sourceId: string;
  sourceName: string;
  targetId: string;
  targetName: string;
  otherId: string;
  otherName: string;
  type: AssetLinkType;
  /** MANUAL, AGENT, COLLECTOR or SYNC: who created the link. */
  origin?: string;
  lastSeenAt?: string;
};

// One row per link as seen from the asset page: the other end is the asset to navigate to, and the
// source/target pair is what removal needs (a link is owned by its source asset).
export const toAssetLinkRows = (links: AssetLinkOutput[], assetId: string): AssetLinkRow[] => {
  return links
    .filter(link => link.asset_link_source_id && link.asset_link_target_id && link.asset_link_type)
    .map((link) => {
      const sourceId = link.asset_link_source_id as string;
      const targetId = link.asset_link_target_id as string;
      const type = link.asset_link_type as AssetLinkType;
      const outgoing = sourceId === assetId;
      const sourceName = link.asset_link_source_name ?? sourceId;
      const targetName = link.asset_link_target_name ?? targetId;
      return {
        key: `${sourceId}-${targetId}-${type}`,
        outgoing,
        sourceId,
        sourceName,
        targetId,
        targetName,
        otherId: outgoing ? targetId : sourceId,
        otherName: outgoing ? targetName : sourceName,
        type,
        origin: link.asset_link_origin,
        lastSeenAt: link.asset_link_last_seen_at,
      };
    });
};

export type AssetLinkGroup = {
  key: string;
  type: AssetLinkType;
  outgoing: boolean;
  rows: AssetLinkRow[];
};

// The read-only overview card: one line per link type and direction, outgoing before incoming,
// in the order of ASSET_LINK_TYPES, the linked assets sorted by name inside each line.
export const groupAssetLinkRows = (rows: AssetLinkRow[]): AssetLinkGroup[] => {
  const order = (type: AssetLinkType) => ASSET_LINK_TYPES.findIndex(linkType => linkType.value === type);
  const groups = new Map<string, AssetLinkGroup>();
  rows.forEach((row) => {
    const key = `${row.type}-${row.outgoing ? 'out' : 'in'}`;
    const group = groups.get(key) ?? {
      key,
      type: row.type,
      outgoing: row.outgoing,
      rows: [],
    };
    group.rows.push(row);
    groups.set(key, group);
  });
  return [...groups.values()]
    .map(group => ({
      ...group,
      rows: [...group.rows].sort((a, b) => a.otherName.localeCompare(b.otherName)),
    }))
    .sort((a, b) => {
      if (a.outgoing !== b.outgoing) {
        return a.outgoing ? -1 : 1;
      }
      return order(a.type) - order(b.type);
    });
};

// The linked assets a group line shows, and how many stay behind "+N more".
export const previewRows = (rows: AssetLinkRow[], expanded: boolean, limit = RELATIONSHIP_PREVIEW): {
  shown: AssetLinkRow[];
  hidden: number;
} => {
  if (expanded || rows.length <= limit) {
    return {
      shown: rows,
      hidden: 0,
    };
  }
  return {
    shown: rows.slice(0, limit),
    hidden: rows.length - limit,
  };
};

// The picker hides the asset itself and the assets already linked with the chosen type.
export const isLinkCandidate = (rows: AssetLinkRow[], assetId: string, candidateId: string, type: AssetLinkType): boolean => {
  if (candidateId === assetId) {
    return false;
  }
  return !rows.some(row => row.outgoing && row.targetId === candidateId && row.type === type);
};
