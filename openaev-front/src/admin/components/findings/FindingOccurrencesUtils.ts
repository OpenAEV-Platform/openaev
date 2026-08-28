import { buildFilter } from '../../../components/common/queryable/filter/FilterUtils';
import { ASSET_BASE_URL, PLAYER_BASE_URL, TEAM_BASE_URL } from '../../../constants/BaseUrls';
import type { FilterGroup, Finding, RelatedFindingOutput, TargetSimple } from '../../../utils/api-types';
import ContractOutputElementType from './ContractOutputElementType';

// Merge the occurrence's assets, teams and persons into ONE chip cluster: a finding hits machines
// OR people depending on its nature, so dedicated columns would mostly render "-" - a single
// "Impacted targets" column adapts to whatever the occurrence actually touched.
export const occurrenceTargets = (occurrence: RelatedFindingOutput): TargetSimple[] => ([
  ...(occurrence.finding_assets || []).map(asset => ({
    target_id: asset.asset_id,
    target_name: asset.asset_name,
    target_type: 'ASSETS',
    // Category + platform drive the chip glyph (taxonomy icon, or the OS brand icon for
    // host-like endpoints) - same rendering as the asset pages.
    target_category: asset.asset_category,
    target_subtype: asset.endpoint_platform,
  })),
  ...(occurrence.finding_teams || []),
  ...(occurrence.finding_users || []),
] as TargetSimple[]);

export const occurrenceTargetLink = (target: TargetSimple): string | undefined => {
  switch (target.target_type) {
    case 'ASSETS':
    case 'ENDPOINTS':
      return `${ASSET_BASE_URL}/${target.target_id}`;
    case 'TEAMS':
      return `${TEAM_BASE_URL}/${target.target_id}`;
    case 'PLAYERS':
      return `${PLAYER_BASE_URL}/${target.target_id}`;
    default:
      return undefined;
  }
};

// Base filter shared by the list and timeline views: every occurrence of the same (type, value).
export const buildOccurrencesFilter = (finding: Pick<Finding, 'finding_type' | 'finding_value'>): FilterGroup => ({
  mode: 'and',
  filters: [
    buildFilter('finding_value', [finding.finding_value], 'eq'),
    buildFilter('finding_type', [ContractOutputElementType[finding.finding_type as keyof typeof ContractOutputElementType]], 'eq'),
  ],
});
