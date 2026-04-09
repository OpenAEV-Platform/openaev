import { simplePostCall } from '../utils/Action';
import { type FullTextSearchCountResult, type PageFullTextSearchResult, type SearchPaginationInput } from '../utils/api-types';

export const fullTextSearch = (searchTerm: string | null) => {
  const uri = '/api/fulltextsearch';
  return simplePostCall<FullTextSearchCountResult[]>(uri, { searchTerm });
};

export const fullTextSearchByClass = (clazz: string, searchPaginationInput: SearchPaginationInput) => {
  const uri = `/api/fulltextsearch/${clazz}`;
  return simplePostCall<PageFullTextSearchResult>(uri, searchPaginationInput);
};
