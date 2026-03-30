import type { Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import type { SearchPaginationInput, Tag, TagCreateInput, TagUpdateInput } from '../../utils/api-types';
import { currentTenantUri } from '../../utils/current-tenant-uri';
import { arrayOfTags, tag } from './tag-schema';

const TAG_PATH = '/tags';

// -- CREATE --

export const addTag = (data: TagCreateInput) => (dispatch: Dispatch) => {
  return postReferential(tag, currentTenantUri(TAG_PATH), data)(dispatch);
};

// -- READ --

export const fetchTags = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfTags, currentTenantUri(TAG_PATH))(dispatch);
};

// -- SEARCH --

export const searchTags = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(currentTenantUri(`${TAG_PATH}/search`), searchPaginationInput);
};

// -- UPDATE --

export const updateTag = (tagId: Tag['tag_id'], data: TagUpdateInput) => (dispatch: Dispatch) => {
  return putReferential(tag, currentTenantUri(`${TAG_PATH}/${tagId}`), data)(dispatch);
};

// -- DELETE --

export const deleteTag = (tagId: Tag['tag_id']) => (dispatch: Dispatch) => {
  return delReferential(currentTenantUri(`${TAG_PATH}/${tagId}`), 'tags', tagId)(dispatch);
};

// -- OPTIONS --

export const searchTagAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(currentTenantUri(`${TAG_PATH}/options`), { params });
};

export const searchTagByIdAsOption = (ids: string[]) => {
  return simplePostCall(currentTenantUri(`${TAG_PATH}/options`), ids);
};
