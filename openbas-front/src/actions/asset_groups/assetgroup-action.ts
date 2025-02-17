import { Dispatch } from 'redux';

import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleCall,
  simplePostCall,
} from '../../utils/Action';
import type { AssetGroup, AssetGroupInput, SearchPaginationInput, UpdateAssetsOnAssetGroupInput } from '../../utils/api-types';
import { arrayOfAssetGroups, assetGroup } from './assetgroup-schema';

const ASSET_GROUP_URI = '/api/asset_groups';

export const addAssetGroup = (data: AssetGroupInput) => (dispatch: Dispatch) => {
  return postReferential(assetGroup, ASSET_GROUP_URI, data)(dispatch);
};

export const updateAssetGroup = (
  assetGroupId: AssetGroup['asset_group_id'],
  data: AssetGroupInput,
) => (dispatch: Dispatch) => {
  const uri = `${ASSET_GROUP_URI}/${assetGroupId}`;
  return putReferential(assetGroup, uri, data)(dispatch);
};

export const updateAssetsOnAssetGroup = (
  assetGroupId: AssetGroup['asset_group_id'],
  data: UpdateAssetsOnAssetGroupInput,
) => (dispatch: Dispatch) => {
  const uri = `${ASSET_GROUP_URI}/${assetGroupId}/assets`;
  return putReferential(assetGroup, uri, data)(dispatch);
};

export const deleteAssetGroup = (assetGroupId: AssetGroup['asset_group_id']) => (dispatch: Dispatch) => {
  const uri = `${ASSET_GROUP_URI}/${assetGroupId}`;
  return delReferential(uri, assetGroup.key, assetGroupId)(dispatch);
};

export const fetchAssetGroups = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfAssetGroups, ASSET_GROUP_URI)(dispatch);
};
export const searchAssetGroups = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${ASSET_GROUP_URI}/search`;
  return simplePostCall(uri, data);
};

export const findAssetGroups = (assetGroupIds: string[]) => {
  const data = assetGroupIds;
  const uri = `${ASSET_GROUP_URI}/find`;
  return simplePostCall(uri, data);
};

export const fetchAssetGroup = (assetGroupId: AssetGroup['asset_group_id']) => (dispatch: Dispatch) => {
  const uri = `${ASSET_GROUP_URI}/${assetGroupId}`;
  return getReferential(assetGroup, uri)(dispatch);
};

export const searchAssetGroupAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${ASSET_GROUP_URI}/options`, params);
};

export const searchAssetGroupByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${ASSET_GROUP_URI}/options`, ids);
};
