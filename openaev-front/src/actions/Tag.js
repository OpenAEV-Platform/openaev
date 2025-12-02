import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import { arrayOfTags, tag } from './schemas';

export const fetchTags = dispatch => getReferential(arrayOfTags, '/api/tags')(dispatch);

export const searchTags = (searchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = '/api/tags/search';
  return simplePostCall(uri, data);
};

export const fetchTag = tagId => dispatch => getReferential(tag, `/api/tags/${tagId}`)(dispatch);

export const addTag = data => dispatch => postReferential(tag, '/api/tags', data)(dispatch);

export const updateTag = (userId, data) => dispatch => putReferential(tag, `/api/tags/${userId}`, data)(dispatch);

export const deleteTag = tagId => dispatch => delReferential(`/api/tags/${tagId}`, 'tags', tagId)(dispatch);
