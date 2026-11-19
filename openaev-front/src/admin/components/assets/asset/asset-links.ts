import { type AssetLinkInput, type AssetLinkOutput } from '../../../../utils/api-types';

export type AssetLinkType = AssetLinkInput['asset_link_type'];

// The link types the backend accepts, with the i18n key of the verb read "source <verb> target"
// and the i18n key of its one-line meaning, shown under each option of the picker.
export const ASSET_LINK_TYPES: {
  value: AssetLinkType;
  label: string;
  description: string;
}[] = [
  {
    value: 'CONNECTS_TO',
    label: 'Connects to',
    description: 'Reaches the linked asset over the network',
  },
  {
    value: 'DEPENDS_ON',
    label: 'Depends on',
    description: 'Needs the linked asset to work',
  },
  {
    value: 'HOSTS',
    label: 'Hosts',
    description: 'Runs the linked asset on its own hardware or hypervisor',
  },
  {
    value: 'LOGGED_ON',
    label: 'Logged on',
    description: 'Has a user session open on the linked asset',
  },
  {
    value: 'RUNS_ON',
    label: 'Runs on',
    description: 'Is installed and executed on the linked asset',
  },
  {
    value: 'USES',
    label: 'Uses',
    description: 'Calls a service the linked asset provides',
  },
];

export const assetLinkTypeLabel = (type?: string): string => {
  return ASSET_LINK_TYPES.find(linkType => linkType.value === type)?.label ?? type ?? '-';
};

export type AssetLinkRow = {
  key: string;
  outgoing: boolean;
  sourceId: string;
  sourceName: string;
  targetId: string;
  targetName: string;
  otherId: string;
  type: AssetLinkType;
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
      return {
        key: `${sourceId}-${targetId}-${type}`,
        outgoing,
        sourceId,
        sourceName: link.asset_link_source_name ?? sourceId,
        targetId,
        targetName: link.asset_link_target_name ?? targetId,
        otherId: outgoing ? targetId : sourceId,
        type,
      };
    });
};

// The picker hides the asset itself and the assets already linked with the chosen type.
export const isLinkCandidate = (rows: AssetLinkRow[], assetId: string, candidateId: string, type: AssetLinkType): boolean => {
  if (candidateId === assetId) {
    return false;
  }
  return !rows.some(row => row.outgoing && row.targetId === candidateId && row.type === type);
};
