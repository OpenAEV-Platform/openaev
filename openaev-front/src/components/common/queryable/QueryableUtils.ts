import { z } from 'zod';

import { type SearchPaginationInput } from '../../../utils/api-types';
import { type Page } from './Page';
import { DEFAULT_ROWS_PER_PAGE } from './pagination/usePaginationState';

export const buildSearchPagination = (searchPaginationInput: Partial<SearchPaginationInput>) => {
  return ({
    page: 0,
    size: DEFAULT_ROWS_PER_PAGE,
    ...searchPaginationInput,
  });
};

// Resolved empty page for fetchers that must short-circuit without calling the
// API - typically when scoping a list on an empty id set, where an empty
// `contains` filter would match everything instead of nothing. Keeps the list
// component (search, filters, pagination) rendered with a zero-result state.
export const buildEmptyPage = <T>(input: SearchPaginationInput): { data: Page<T> } => {
  const size = input.size ?? DEFAULT_ROWS_PER_PAGE;
  const sort = {
    empty: true,
    sorted: false,
    unsorted: true,
  };
  return {
    data: {
      content: [],
      empty: true,
      first: true,
      last: true,
      number: 0,
      numberOfElements: 0,
      pageable: {
        offset: 0,
        pageNumber: 0,
        pageSize: size,
        paged: true,
        sort,
        unpaged: false,
      },
      size,
      sort,
      totalElements: 0,
      totalPages: 0,
    },
  };
};

// -- ZOD --

const FilterSchema = z.object({
  key: z.string(),
  mode: z.enum(['and', 'or']).optional(),
  operator: z.enum([
    'eq',
    'not_eq',
    'contains',
    'not_contains',
    'starts_with',
    'not_starts_with',
    'empty',
    'not_empty',
  ]).optional(),
  values: z.array(z.string()).optional(),
});

const FilterGroupSchema = z.object({
  filters: z.array(FilterSchema).optional(),
  mode: z.enum(['and', 'or']),
});

const SortFieldSchema = z.object({
  direction: z.string().optional(),
  property: z.string().optional(),
});

export const SearchPaginationInputSchema = z.object({
  filterGroup: FilterGroupSchema.optional(),
  page: z.preprocess((val) => {
    if (typeof val === 'string') return parseInt(val, 10);
    return val;
  }, z.number().int().min(0)),
  size: z.preprocess((val) => {
    if (typeof val === 'string') return parseInt(val, 10);
    return val;
  }, z.number().int().max(1000)),
  sorts: z.array(SortFieldSchema).optional(),
  textSearch: z.string().optional(),
});
