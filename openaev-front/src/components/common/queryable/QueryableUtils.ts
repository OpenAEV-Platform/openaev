import { z } from 'zod';

import { type SearchPaginationInput } from '../../../utils/api-types';
import { ROWS_PER_PAGE_OPTIONS } from './pagination/usPaginationState';

export const buildSearchPagination = (searchPaginationInput: Partial<SearchPaginationInput>) => {
  return ({
    page: 0,
    size: ROWS_PER_PAGE_OPTIONS[0],
    ...searchPaginationInput,
  });
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
