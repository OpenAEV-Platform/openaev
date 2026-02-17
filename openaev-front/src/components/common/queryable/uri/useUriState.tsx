import * as qs from 'qs';
import * as R from 'ramda';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { z } from 'zod';

import { type SearchPaginationInput } from '../../../../utils/api-types';
import { buildSearchPagination, SearchPaginationInputSchema } from '../QueryableUtils';
import { type UriHelpers } from './UriHelpers';

export const retrieveFromUri = (localStorageKey: string, searchParams: URLSearchParams): SearchPaginationInput | null => {
  const encodedParams = searchParams.get('query') || '';
  const params = atob(encodedParams);
  const paramsJson = qs.parse(params, { allowEmptyArrays: true }) as unknown as SearchPaginationInput & { key: string };
  if (!R.isEmpty(paramsJson) && paramsJson.key === localStorageKey) {
    try {
      const parse = SearchPaginationInputSchema.parse(paramsJson);
      return buildSearchPagination(parse);
    } catch (err) {
      if (err instanceof z.ZodError) {
        // URI validation failed - return null to use default pagination
        return null;
      }
    }
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
