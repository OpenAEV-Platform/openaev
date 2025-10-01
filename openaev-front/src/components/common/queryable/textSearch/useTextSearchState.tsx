import { useEffect, useState } from 'react';

import { type TextSearchHelpers } from './TextSearchHelpers';

const useTextSearchState = (initTextSearch: string = '', onChange?: (textSearch: string, page: number) => void): TextSearchHelpers => {
  const [textSearch, setTextSearch] = useState<string>(initTextSearch);
  const helpers: TextSearchHelpers = { handleTextSearch: (value?: string) => setTextSearch(value ?? '') };

  useEffect(() => {
    onChange?.(textSearch, 0);
  }, [textSearch]);

  return helpers;
};

export default useTextSearchState;
