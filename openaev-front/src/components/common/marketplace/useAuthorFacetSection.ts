import { useMemo } from 'react';

import { type SearchPaginationInput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import { type AuthorOption, buildAuthorRows, useAuthorFacetFilter } from '../facets/ContractFacets';
import { type FacetSection } from '../facets/FacetFilters';
import { type FilterHelpers } from '../queryable/filter/FilterHelpers';

// Author facet section over a polymorphic author filter, with its "No author" row.
const useAuthorFacetSection = (
  filterKey: string,
  authorOptions: AuthorOption[],
  searchPaginationInput: SearchPaginationInput,
  filterHelpers: FilterHelpers,
): FacetSection => {
  const { t } = useFormatter();
  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );
  const { authorValues, noAuthorActive, toggleAuthorValue, toggleNoAuthor } = useAuthorFacetFilter(filterKey, filters, filterHelpers);
  return {
    id: 'author',
    label: t('Author'),
    rows: buildAuthorRows({
      authorOptions,
      authorValues,
      noAuthorActive,
      toggleAuthorValue,
      toggleNoAuthor,
      noAuthorLabel: t('No author'),
    }),
  };
};

export default useAuthorFacetSection;
