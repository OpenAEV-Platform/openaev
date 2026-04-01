import * as qs from 'qs';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';

import { type Filter, type SearchPaginationInput } from '../../../../utils/api-types';
import { generateFilterId } from '../filter/FilterUtils';
import { buildSearchPagination, SearchPaginationInputSchema } from '../QueryableUtils';
import { type UriHelpers } from './UriHelpers';

export const retrieveFromUri = (localStorageKey: string, searchParams: URLSearchParams): SearchPaginationInput | null => {
  const encodedParams = searchParams.get('query') || '';
  if (!encodedParams) {
    return null;
  }

  let params: string;
  try {
    params = atob(encodedParams);
  } catch {
    return null;
  }
  const paramsJson = qs.parse(params, { allowEmptyArrays: true }) as unknown as SearchPaginationInput & { key?: string };
  if (Object.keys(paramsJson).length > 0 && paramsJson.key === localStorageKey) {
    const parseResult = SearchPaginationInputSchema.safeParse(paramsJson);
    if (parseResult.success) {
      const normalizedFilters: Filter[] | undefined = parseResult.data.filterGroup?.filters?.map((filter) => {
        const existingId = (filter as { id?: string }).id;
        return {
          ...filter,
          id: existingId || generateFilterId(),
        } as Filter;
      });

      const normalized = {
        ...parseResult.data,
        filterGroup: parseResult.data.filterGroup
          ? {
              ...parseResult.data.filterGroup,
              filters: normalizedFilters,
            }
          : undefined,
      };
      return buildSearchPagination(normalized as Partial<SearchPaginationInput>);
    }
    // URI validation failed - return null to use default pagination
    return null;
  }
  return null;
};

const useUriState = (localStorageKey: string, initSearchPaginationInput: SearchPaginationInput, onChange: (input: SearchPaginationInput) => void) => {
  const [searchParams, setSearchParams] = useSearchParams();

  const [input, setInput] = useState<SearchPaginationInput>(initSearchPaginationInput);

  // Use ref to store onChange to avoid triggering useEffect when callback reference changes
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const helpers: UriHelpers = {
    retrieveFromUri: () => {
      const built = retrieveFromUri(localStorageKey, searchParams);
      if (built) {
        setInput(built);
      }
    },
    updateUri: () => {
      const params = qs.stringify({
        ...initSearchPaginationInput,
        key: localStorageKey,
      }, { allowEmptyArrays: true });
      const encodedParams = btoa(params);
      setSearchParams((searchParams) => {
        searchParams.set('query', encodedParams);
        return searchParams;
      }, { replace: true });
    },
  };

  useEffect(() => {
    onChangeRef.current(input);
  }, [input]);

  return helpers;
};

export default useUriState;
