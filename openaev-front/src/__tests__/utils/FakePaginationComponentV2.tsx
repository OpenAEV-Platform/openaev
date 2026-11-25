import { type ReactElement, useEffect, useRef } from 'react';

import { type Page } from '../../components/common/queryable/Page';
import { type QueryableHelpers } from '../../components/common/queryable/QueryableHelpers';
import { type SearchPaginationInput } from '../../utils/api-types';

interface Props<T> {
  fetch: (input: SearchPaginationInput) => Promise<{ data: Page<T> }>;
  searchPaginationInput: SearchPaginationInput;
  setContent: (data: T[]) => void;
  setLoading?: (loading: boolean) => void;
  queryableHelpers: QueryableHelpers;
  reloadContentCount?: number;
  topBarButtons?: ReactElement | null;
}

// Stands for the list toolbar in page tests: it loads the pages as the real one does, with a search
// field and one chip per filter, without the schema and option calls of the real filters.
const FakePaginationComponentV2 = <T, >({ fetch, searchPaginationInput, setContent, setLoading, queryableHelpers, reloadContentCount, topBarButtons }: Props<T>) => {
  const rows = useRef<T[]>([]);
  useEffect(() => {
    setLoading?.(true);
    fetch(searchPaginationInput)
      .then(({ data }) => {
        rows.current = searchPaginationInput.page > 0 ? [...rows.current, ...data.content] : data.content;
        setContent(rows.current);
        queryableHelpers.paginationHelpers.handleChangeTotalElements(data.totalElements);
      })
      .finally(() => setLoading?.(false));
  }, [searchPaginationInput, reloadContentCount]);

  return (
    <div>
      <input aria-label="Search" onChange={event => queryableHelpers.textSearchHelpers.handleTextSearch(event.target.value)} />
      {(searchPaginationInput.filterGroup?.filters ?? []).map(filter => (
        <span key={filter.key} data-testid="filter-chip">{`${filter.key} = ${(filter.values ?? []).join(', ')}`}</span>
      ))}
      {topBarButtons}
    </div>
  );
};

export default FakePaginationComponentV2;
