import { ASSET_BASE_URL, ASSET_GROUP_BASE_URL, PERSON_BASE_URL, TEAM_BASE_URL } from '../../constants/BaseUrls';
import type { InjectTarget } from '../api-types';

export const isAssetGroups = (target: InjectTarget) => {
  return target.target_type === 'ASSETS_GROUPS';
};

// Detail page a target can pivot to. Every target type backed by an entity
// with a standalone overview (endpoints, AI targets, asset groups, teams and
// persons) resolves to it; bare agents have no overview of their own (their
// endpoint id is not serialized to the client), so they return null and no
// pivot affordance is rendered.
export const getTargetOverviewUrl = (target: InjectTarget): string | null => {
  switch (target.target_type) {
    case 'ASSETS':
    case 'AI_TARGETS':
      return `${ASSET_BASE_URL}/${target.target_id}`;
    case 'ASSETS_GROUPS':
      return `${ASSET_GROUP_BASE_URL}/${target.target_id}`;
    case 'TEAMS':
      return `${TEAM_BASE_URL}/${target.target_id}`;
    case 'PLAYERS':
      return `${PERSON_BASE_URL}/${target.target_id}`;
    default:
      return null;
  }
};

// i18n key of the pivot label matching getTargetOverviewUrl; only meaningful
// for target types that resolve to an overview URL.
export const getTargetOverviewLabel = (target: InjectTarget): string => {
  switch (target.target_type) {
    case 'ASSETS_GROUPS':
      return 'Open asset group overview';
    case 'TEAMS':
      return 'Open team overview';
    case 'PLAYERS':
      return 'Open person overview';
    default:
      return 'Open asset overview';
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
