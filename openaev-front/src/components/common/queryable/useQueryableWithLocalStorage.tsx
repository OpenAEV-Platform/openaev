import { type Dispatch, type SetStateAction, useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { type FilterGroup, type SearchPaginationInput, type SortField } from '../../../utils/api-types';
import useFiltersState from './filter/useFiltersState';
import usePaginationState from './pagination/usePaginationState';
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
  const paginationHelpers = usePaginationState(searchPaginationInput.size, (page: number, size: number) => setSearchPaginationInput({
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

  // Transitional state to avoid re-render caused by useLocalStorage hook
  const [searchPaginationInput, setSearchPaginationInput] = useState(
    searchPaginationInputFromUri ?? searchPaginationInputFromLocalStorage,
  );

  // Flag to skip useEffect when the update originates from within this hook
  const isInternalUpdate = useRef(false);

  // Always write to both states together to keep them in sync
  const updateSearchPaginationInput = useCallback(
    (value: SetStateAction<SearchPaginationInput>) => {
      // Resolve functional updates (e.g. (prev) => newState)
      const newInput = typeof value === 'function' ? value(searchPaginationInput) : value;
      isInternalUpdate.current = true;
      setSearchPaginationInput(newInput);
      setSearchPaginationInputFromLocalStorage(newInput);
    },
    [searchPaginationInput, setSearchPaginationInputFromLocalStorage],
  );

  useEffect(() => {
    // Ignore internal updates — already handled by setSearchPaginationInputBoth
    if (isInternalUpdate.current) {
      isInternalUpdate.current = false;
      return;
    }
    // Only react to external changes (e.g. another tab updating localStorage)
    if (JSON.stringify(searchPaginationInputFromLocalStorage) !== JSON.stringify(searchPaginationInput)) {
      setSearchPaginationInput(searchPaginationInputFromLocalStorage);
    }
  }, [searchPaginationInputFromLocalStorage]);

  return buildUseQueryable(
    localStorageKey,
    initSearchPaginationInput,
    searchPaginationInput,
    updateSearchPaginationInput,
  );
};
