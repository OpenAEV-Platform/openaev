import type { InjectTarget } from '../api-types';

export const isAssetGroups = (target: InjectTarget) => {
  return target.target_type === 'ASSETS_GROUPS';
};

export const isAgent = (target: InjectTarget) => {
  return target.target_type === 'AGENT';
};
