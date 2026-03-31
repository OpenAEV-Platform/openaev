import type { Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import type { SearchPaginationInput, Tag, TagCreateInput, TagUpdateInput } from '../../utils/api-types';
import { buildTenantApiUri } from '../../utils/tenant-url-helper';
import { arrayOfTags, tag } from './tag-schema';

const TAG_PATH = '/tags';

// -- CREATE --

export const addTag = (data: TagCreateInput) => (dispatch: Dispatch) => {
  return postReferential(tag, buildTenantApiUri(TAG_PATH), data)(dispatch);
};

// -- READ --

export const fetchTags = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfTags, buildTenantApiUri(TAG_PATH))(dispatch);
};

// -- SEARCH --

export const searchTags = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(buildTenantApiUri(`${TAG_PATH}/search`), searchPaginationInput);
};

// -- UPDATE --

export const updateTag = (tagId: Tag['tag_id'], data: TagUpdateInput) => (dispatch: Dispatch) => {
  return putReferential(tag, buildTenantApiUri(`${TAG_PATH}/${tagId}`), data)(dispatch);
};

// -- DELETE --

export const deleteTag = (tagId: Tag['tag_id']) => (dispatch: Dispatch) => {
  return delReferential(buildTenantApiUri(`${TAG_PATH}/${tagId}`), 'tags', tagId)(dispatch);
};

// -- OPTIONS --

export const searchTagAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(buildTenantApiUri(`${TAG_PATH}/options`), { params });
};

export const searchTagByIdAsOption = (ids: string[]) => {
  return simplePostCall(buildTenantApiUri(`${TAG_PATH}/options`), ids);
};
