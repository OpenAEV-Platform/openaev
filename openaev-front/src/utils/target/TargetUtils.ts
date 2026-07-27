import { ASSET_BASE_URL, ASSET_GROUP_BASE_URL } from '../../constants/BaseUrls';
import type { InjectTarget } from '../api-types';

export const isAssetGroups = (target: InjectTarget) => {
  return target.target_type === 'ASSETS_GROUPS';
};

// Detail page a target can pivot to. Only asset-backed targets (endpoints, AI
// targets and asset groups) have a standalone overview; teams, players and bare
// agents do not, so they return null and no pivot affordance is rendered.
export const getTargetOverviewUrl = (target: InjectTarget): string | null => {
  switch (target.target_type) {
    case 'ASSETS':
    case 'AI_TARGETS':
      return `${ASSET_BASE_URL}/${target.target_id}`;
    case 'ASSETS_GROUPS':
      return `${ASSET_GROUP_BASE_URL}/${target.target_id}`;
    default:
      return null;
  }
};

export const isAssets = (target: InjectTarget) => {
  return target.target_type === 'ASSETS';
};

export const isAgent = (target: InjectTarget) => {
  return target.target_type === 'AGENT';
};

export const isAgentless = (hasAgents: boolean, hasTeams: boolean) => {
  return !hasAgents && !hasTeams;
};

export const isTeam = (target: InjectTarget) => {
  return target.target_type === 'TEAMS';
};

export const isPlayer = (target: InjectTarget) => {
  return target.target_type === 'PLAYERS';
};
