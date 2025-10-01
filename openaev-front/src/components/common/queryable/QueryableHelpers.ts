import { type FilterHelpers } from './filter/FilterHelpers';
import { type PaginationHelpers } from './pagination/PaginationHelpers';
import { type SortHelpers } from './sort/SortHelpers';
import { type TextSearchHelpers } from './textSearch/TextSearchHelpers';
import { type UriHelpers } from './uri/UriHelpers';

export interface QueryableHelpers {
  textSearchHelpers: TextSearchHelpers;
  paginationHelpers: PaginationHelpers;
  filterHelpers: FilterHelpers;
  sortHelpers: SortHelpers;
  uriHelpers?: UriHelpers;
}
