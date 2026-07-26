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
  // All the onChange callbacks below use FUNCTIONAL updates (returning `prev`
  // untouched when nothing changed): several state hooks can propagate in the
  // same effect flush (e.g. "Clear filters" resetting both the filter group
  // and the text search), and building the next input from a shared render
  // snapshot would let the last write silently revert the others.

  // Text Search. The state hook fires once on mount with its initial value:
  // skip that no-op so it cannot clobber a page restored from the URI (a real
  // text change always restarts from the first page).
  const textSearchHelpers = useTextSearchState(searchPaginationInput.textSearch, (textSearch: string, page: number) => {
    setSearchPaginationInput(prev => (
      textSearch === (prev.textSearch ?? '')
        ? prev
        : {
            ...prev,
            textSearch,
            page,
          }
    ));
  });

  // Pagination, fully controlled by searchPaginationInput. Skip no-op updates
  // so an already-correct page cannot trigger a redundant refetch loop.
  const paginationHelpers = usePaginationState(
    searchPaginationInput.page,
    searchPaginationInput.size,
    (page: number, size: number) => {
      setSearchPaginationInput(prev => (
        page === prev.page && size === prev.size
          ? prev
          : {
              ...prev,
              page,
              size,
            }
      ));
    },
  );

  // Filters. A narrower result set can leave the current page out of range,
  // so a real filter change always restarts from the first page. The state
  // hook also fires once on mount with an id-normalized copy of its initial
  // value: that pure normalization must neither reset the page (it would
  // clobber a URI deep link) nor trigger a refetch when nothing changed.
  const stripFilterIds = (filterGroup?: FilterGroup) => JSON.stringify({
    mode: filterGroup?.mode,
    filters: filterGroup?.filters?.map(({ id: _id, ...rest }) => rest),
  });
  const [__, filterHelpers] = useFiltersState(searchPaginationInput.filterGroup, initSearchPaginationInput.filterGroup, (filterGroup: FilterGroup) => {
    setSearchPaginationInput((prev) => {
      const contentUnchanged = stripFilterIds(filterGroup) === stripFilterIds(prev.filterGroup);
      if (contentUnchanged && JSON.stringify(filterGroup) === JSON.stringify(prev.filterGroup)) {
        return prev;
      }
      return {
        ...prev,
        filterGroup,
        page: contentUnchanged ? prev.page : 0,
      };
    });
  });

  // Sorts. The state hook fires once on mount with its (normalized) initial
  // value: skip it when nothing changed so it cannot overwrite concurrent
  // updates with a stale input snapshot.
  const sortHelpers = useSortState(searchPaginationInput.sorts, (sorts: SortField[]) => {
    setSearchPaginationInput(prev => (
      JSON.stringify(sorts) === JSON.stringify(prev.sorts)
        ? prev
        : {
            ...prev,
            sorts,
          }
    ));
  });

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

// The page index is never persisted: storage keys are shared across entities
// of the same type (e.g. every simulation shares 'simulation-injects-results'),
// so a page restored from a longer list would point past the end of a shorter
// one and render a stuck empty page. Filters, sorts, size and text search
// remain persisted as cross-visit view preferences.
const sanitizeForStorage = (input: SearchPaginationInput): SearchPaginationInput => ({
  ...input,
  page: 0,
});

export const useQueryableWithLocalStorage = (localStorageKey: string, initSearchPaginationInput: Partial<SearchPaginationInput>) => {
  const [searchParams] = useSearchParams();
  const finalSearchPaginationInput: SearchPaginationInput = buildSearchPagination(initSearchPaginationInput);
  const searchPaginationInputFromUri = retrieveFromUri(localStorageKey, searchParams);

  const [searchPaginationInputFromLocalStorage, setSearchPaginationInputFromLocalStorage] = useLocalStorage<SearchPaginationInput>(
    localStorageKey,
    sanitizeForStorage(searchPaginationInputFromUri ?? finalSearchPaginationInput),
  );

  // Transitional state to avoid re-render caused by useLocalStorage hook.
  // A URI deep link keeps its explicit page; otherwise start from page 0.
  const [searchPaginationInput, setSearchPaginationInput] = useState(
    searchPaginationInputFromUri ?? sanitizeForStorage(searchPaginationInputFromLocalStorage),
  );

  // Flag to skip useEffect when the update originates from within this hook
  const isInternalUpdate = useRef(false);

  // Latest input, including updates not yet rendered. Functional updates must
  // resolve against THIS (not the render snapshot): several updates can land
  // in the same effect flush (e.g. "Clear filters" resetting both the filter
  // group and the text search) and resolving each against the same rendered
  // snapshot would lose all but the last one.
  const pendingInputRef = useRef(searchPaginationInput);
  pendingInputRef.current = searchPaginationInput;

  // Always write to both states together to keep them in sync
  const updateSearchPaginationInput = useCallback(
    (value: SetStateAction<SearchPaginationInput>) => {
      // Resolve functional updates (e.g. (prev) => newState)
      const newInput = typeof value === 'function' ? value(pendingInputRef.current) : value;
      // Functional updaters return `prev` untouched for no-op changes: skip
      // the state/storage writes entirely (mirrors React's own bail-out).
      if (newInput === pendingInputRef.current) {
        return;
      }
      pendingInputRef.current = newInput;
      isInternalUpdate.current = true;
      setSearchPaginationInput(newInput);
      setSearchPaginationInputFromLocalStorage(sanitizeForStorage(newInput));
    },
    [setSearchPaginationInputFromLocalStorage],
  );

  // On mount the stored value can legitimately differ from the adopted input
  // (URI deep link wins, page index stripped for storage): only react to
  // storage changes happening after mount (e.g. another tab).
  const isFirstRun = useRef(true);
  useEffect(() => {
    if (isFirstRun.current) {
      isFirstRun.current = false;
      return;
    }
    // Ignore internal updates — already handled by updateSearchPaginationInput
    if (isInternalUpdate.current) {
      isInternalUpdate.current = false;
      return;
    }
    // Only react to external changes (e.g. another tab updating localStorage)
    if (JSON.stringify(searchPaginationInputFromLocalStorage) !== JSON.stringify(sanitizeForStorage(searchPaginationInput))) {
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
