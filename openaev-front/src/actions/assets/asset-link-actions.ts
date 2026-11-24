import { simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type AssetLinkInput } from '../../utils/api-types';

// Asset-to-asset links (host <-> identity, service -> host, ...): every link starting or ending on
// the asset, same tenant only; the graph draws them as LINKED_TO edges.
export const fetchAssetLinks = (assetId: string) => {
  return simpleCall(`/api/assets/${assetId}/links`);
};

export const addAssetLink = (assetId: string, input: AssetLinkInput) => {
  return simplePostCall(`/api/assets/${assetId}/links`, input);
};

// A link is owned by its source asset: an incoming link is removed through the asset it starts on.
export const deleteAssetLink = (sourceAssetId: string, targetAssetId: string, linkType: AssetLinkInput['asset_link_type']) => {
  return simpleDelCall(`/api/assets/${sourceAssetId}/links/${targetAssetId}/${linkType}`);
};
