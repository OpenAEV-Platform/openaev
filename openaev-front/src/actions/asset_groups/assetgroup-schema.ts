import { schema } from 'normalizr';

export const assetGroup = new schema.Entity(
  'asset_groups',
  {},
  { idAttribute: 'asset_group_id' },
);
export const arrayOfAssetGroups = new schema.Array(assetGroup);
