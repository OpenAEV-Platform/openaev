import { useCallback, useEffect, useRef, useState } from 'react';

import { type TextSearchHelpers } from './TextSearchHelpers';

const useTextSearchState = (initTextSearch: string = '', onChange?: (textSearch: string, page: number) => void): TextSearchHelpers => {
  const [textSearch, setTextSearch] = useState<string>(initTextSearch);

  // Use ref to store onChange to avoid triggering useEffect when callback reference changes
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const helpers: TextSearchHelpers = { handleTextSearch: useCallback((value?: string) => setTextSearch(value ?? ''), []) };

  useEffect(() => {
    onChangeRef.current?.(textSearch, 0);
  }, [textSearch]);

  return helpers;
};

export default useTextSearchState;
