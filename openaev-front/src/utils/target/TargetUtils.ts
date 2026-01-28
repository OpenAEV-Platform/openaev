import type { InjectTarget } from '../api-types';

export const isAssetGroups = (target: InjectTarget) => {
  return target.target_type === 'ASSETS_GROUPS';
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
