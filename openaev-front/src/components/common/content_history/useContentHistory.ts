import { useEffect, useState } from 'react';

import { type ContentChangeOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { type Page } from '../queryable/Page';
import { type PaginationHelpers } from '../queryable/pagination/PaginationHelpers';
import { useLocalPaginationState } from '../queryable/pagination/usePaginationState';

export interface ContentHistory<T extends ContentChangeOutput> {
  // The page shown, newest first; null while it loads.
  entries: T[] | null;
  pagination: PaginationHelpers;
}

/** The history of one catalogue content, one page at a time. A new `reload` value reads it again. */
const useContentHistory = <T extends ContentChangeOutput>(
  searchHistory: (id: string, input: SearchPaginationInput) => Promise<{ data: Page<T> }>,
  id: string,
  reload = 0,
): ContentHistory<T> => {
  const pagination = useLocalPaginationState();
  const { page, elementsPerPage: size, handleChangeTotalElements } = pagination;
  // The last page read, kept on screen while the next one loads; another content starts empty.
  const [loaded, setLoaded] = useState<{
    id: string;
    entries: T[];
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    searchHistory(id, {
      page,
      size,
    }).then(({ data }) => {
      if (!cancelled) {
        setLoaded({
          id,
          entries: data.content,
        });
        handleChangeTotalElements(data.totalElements);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [id, reload, page, size]);

  return {
    entries: loaded?.id === id ? loaded.entries : null,
    pagination,
  };
};

export default useContentHistory;
