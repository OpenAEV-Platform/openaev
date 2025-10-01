import * as qs from 'qs';
import * as R from 'ramda';
import { useEffect, useState } from 'react';
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
        // eslint-disable-next-line no-console
        console.log(`Validation error: the uri has not a valid format ${err.issues}`);
        return null;
      }
    }
  }
  return null;
};

const useUriState = (localStorageKey: string, initSearchPaginationInput: SearchPaginationInput, onChange: (input: SearchPaginationInput) => void) => {
  const [searchParams, setSearchParams] = useSearchParams();

  const [input, setInput] = useState<SearchPaginationInput>(initSearchPaginationInput);

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
    onChange(input);
  }, [input]);

  return helpers;
};

export default useUriState;
