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
