import * as R from 'ramda';
import { type Dispatch, type SetStateAction, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { type FilterGroup, type SearchPaginationInput, type SortField } from '../../../utils/api-types';
import useFiltersState from './filter/useFiltersState';
import usPaginationState from './pagination/usPaginationState';
import { type QueryableHelpers } from './QueryableHelpers';
import { buildSearchPagination } from './QueryableUtils';
import useSortState from './sort/useSortState';
import useTextSearchState from './textSearch/useTextSearchState';
import useUriState, { retrieveFromUri } from './uri/useUriState';

const buildUseQueryable = (
  localStorageKey: string | null,
  initSearchPaginationInput: Partial<SearchPaginationInput>,
  searchPaginationInput: SearchPaginationInput,
  setSearchPaginationInput: Dispatch<SetStateAction<SearchPaginationInput>>,
) => {
  // Text Search
  const textSearchHelpers = useTextSearchState(searchPaginationInput.textSearch, (textSearch: string, page: number) => setSearchPaginationInput({
    ...searchPaginationInput,
    textSearch,
    page,
  }));

  // Pagination
  const paginationHelpers = usPaginationState(searchPaginationInput.size, (page: number, size: number) => setSearchPaginationInput({
    ...searchPaginationInput,
    page,
    size,
  }));

  // Filters
  const [__, filterHelpers] = useFiltersState(searchPaginationInput.filterGroup, initSearchPaginationInput.filterGroup, (filterGroup: FilterGroup) => setSearchPaginationInput({
    ...searchPaginationInput,
    filterGroup,
  }));

  // Sorts
  const sortHelpers = useSortState(searchPaginationInput.sorts, (sorts: SortField[]) => setSearchPaginationInput({
    ...searchPaginationInput,
    sorts,
  }));

  // Uri
  let uriHelpers;
  if (localStorageKey) {
    uriHelpers = useUriState(localStorageKey, searchPaginationInput, (input: SearchPaginationInput) => setSearchPaginationInput(input));
  }

  const queryableHelpers: QueryableHelpers = {
    textSearchHelpers,
    paginationHelpers,
    filterHelpers,
    sortHelpers,
    uriHelpers,
  };

  return ({
    queryableHelpers,
    searchPaginationInput,
    setSearchPaginationInput,
  });
};

export const useQueryable = (initSearchPaginationInput: Partial<SearchPaginationInput>, currentSearchPaginationInput?: Partial<SearchPaginationInput>) => {
  const finalSearchPaginationInput: SearchPaginationInput = buildSearchPagination(currentSearchPaginationInput ?? initSearchPaginationInput);

  const [searchPaginationInput, setSearchPaginationInput] = useState<SearchPaginationInput>(finalSearchPaginationInput);

  return buildUseQueryable(null, initSearchPaginationInput, searchPaginationInput, setSearchPaginationInput);
};

export const useQueryableWithLocalStorage = (localStorageKey: string, initSearchPaginationInput: Partial<SearchPaginationInput>) => {
  const [searchParams] = useSearchParams();
  const finalSearchPaginationInput: SearchPaginationInput = buildSearchPagination(initSearchPaginationInput);
  const searchPaginationInputFromUri = retrieveFromUri(localStorageKey, searchParams);

  const [searchPaginationInputFromLocalStorage, setSearchPaginationInputFromLocalStorage] = useLocalStorage<SearchPaginationInput>(
    localStorageKey,
    searchPaginationInputFromUri ?? finalSearchPaginationInput,
  );
  // add a transitional state to avoid re render because of useLocalStorage hook
  const [searchPaginationInput, setSearchPaginationInput] = useState(searchPaginationInputFromUri ?? searchPaginationInputFromLocalStorage);

  useEffect(() => {
    // check deep changes between state from local storage and transitional state
    if (!R.equals(searchPaginationInputFromLocalStorage, searchPaginationInput)) {
      setSearchPaginationInput(searchPaginationInputFromLocalStorage);
    }
  }, [searchPaginationInputFromLocalStorage]);

  return buildUseQueryable(localStorageKey, initSearchPaginationInput, searchPaginationInput, setSearchPaginationInputFromLocalStorage);
};
