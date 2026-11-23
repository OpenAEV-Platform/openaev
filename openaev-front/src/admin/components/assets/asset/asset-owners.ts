import { type AssetOwnerInput, type AssetOwnerOutput } from '../../../../utils/api-types';

export type AssetOwnerRole = AssetOwnerInput['asset_owner_role'];
export type AssetOwnerKind = 'person' | 'team';

// The two ownership roles, with the i18n key of the label and of its one-line meaning.
export const ASSET_OWNER_ROLES: {
  value: AssetOwnerRole;
  label: string;
  description: string;
}[] = [
  {
    value: 'OWNED_BY',
    label: 'Business owner',
    description: 'Accountable for the asset and decides on its risk',
  },
  {
    value: 'MANAGED_BY',
    label: 'Technical owner',
    description: 'Operates and patches the asset',
  },
];

export const assetOwnerRoleLabel = (role?: string): string => {
  return ASSET_OWNER_ROLES.find(r => r.value === role)?.label ?? role ?? '-';
};

// Where a link or an owner comes from, as the i18n key shown beside it; a manual entry shows nothing.
export const relationSourceLabel = (source?: string): string | null => {
  switch (source) {
    case 'AGENT':
      return 'Agent';
    case 'COLLECTOR':
      return 'Collector';
    case 'SYNC':
      return 'Sync';
    default:
      return null;
  }
};

export type AssetOwnerRow = {
  id: string;
  role: AssetOwnerRole;
  kind: AssetOwnerKind;
  name: string;
  targetId: string;
  source?: string;
};

export const toAssetOwnerRows = (owners: AssetOwnerOutput[]): AssetOwnerRow[] => {
  return owners
    .filter(owner => owner.asset_owner_id && owner.asset_owner_role && (owner.asset_owner_user_id || owner.asset_owner_team_id))
    .map((owner) => {
      const kind: AssetOwnerKind = owner.asset_owner_team_id ? 'team' : 'person';
      const targetId = (kind === 'team' ? owner.asset_owner_team_id : owner.asset_owner_user_id) as string;
      return {
        id: owner.asset_owner_id as string,
        role: owner.asset_owner_role as AssetOwnerRole,
        kind,
        name: owner.asset_owner_name ?? targetId,
        targetId,
        source: owner.asset_owner_source,
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
};

// One line per role, business owner first, roles without owner left out.
export const groupAssetOwnerRows = (rows: AssetOwnerRow[]): {
  role: AssetOwnerRole;
  rows: AssetOwnerRow[];
}[] => {
  return ASSET_OWNER_ROLES
    .map(role => ({
      role: role.value,
      rows: rows.filter(row => row.role === role.value),
    }))
    .filter(group => group.rows.length > 0);
};

// A person or team already holding the role is not offered again for it.
export const isOwnerCandidate = (rows: AssetOwnerRow[], role: AssetOwnerRole, kind: AssetOwnerKind, targetId: string): boolean => {
  return !rows.some(row => row.role === role && row.kind === kind && row.targetId === targetId);
};

export const ownerLink = (row: AssetOwnerRow): string => {
  return row.kind === 'team' ? `/admin/teams/${row.targetId}` : `/admin/persons/${row.targetId}`;
};
