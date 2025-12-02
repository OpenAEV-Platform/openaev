import { type Page } from '../components/common/queryable/Page';
import { simplePostCall } from '../utils/Action';
import { type FullTextSearchCountResult, type FullTextSearchResult, type SearchPaginationInput } from '../utils/api-types';

export const fullTextSearch = (searchTerm: string | null) => {
  const uri = '/api/fulltextsearch';
  return simplePostCall<Record<string, FullTextSearchCountResult>>(uri, { searchTerm });
};

export const fullTextSearchByClass = (clazz: string, searchPaginationInput: SearchPaginationInput) => {
  const uri = `/api/fulltextsearch/${clazz}`;
  return simplePostCall<Page<FullTextSearchResult>>(uri, searchPaginationInput);
};
