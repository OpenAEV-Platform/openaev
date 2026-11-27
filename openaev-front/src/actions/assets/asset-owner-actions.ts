import { simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type AssetOwnerInput } from '../../utils/api-types';

// Owners of an asset: a person (OpenAEV user) or a team, as business owner (OWNED_BY) or technical
// owner (MANAGED_BY). Behind the exploration graph preview feature, like the asset links.
export const fetchAssetOwners = (assetId: string) => {
  return simpleCall(`/api/assets/${assetId}/owners`);
};

export const addAssetOwner = (assetId: string, input: AssetOwnerInput) => {
  return simplePostCall(`/api/assets/${assetId}/owners`, input);
};

export const deleteAssetOwner = (assetId: string, ownerId: string) => {
  return simpleDelCall(`/api/assets/${assetId}/owners/${ownerId}`);
};
