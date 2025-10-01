import { simplePostCall } from '../utils/Action';
import { type SearchPaginationInput } from '../utils/api-types';

export const fullTextSearch = (searchTerm: string | null) => {
  const uri = '/api/fulltextsearch';
  return simplePostCall(uri, { searchTerm });
};

export const fullTextSearchByClass = (clazz: string, searchPaginationInput: SearchPaginationInput) => {
  const uri = `/api/fulltextsearch/${clazz}`;
  return simplePostCall(uri, searchPaginationInput);
};
