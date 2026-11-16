import { v5 as uuidv5 } from 'uuid';
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

// The list state a user can change (text search, size, sort, filters), as a
// string: the page is left out (never persisted) and so are the generated
// filter ids. No value is normalized here: two states are the same list state
// only when they carry the same values.
export const listStateFingerprint = (input: Partial<SearchPaginationInput>) => {
  const state = buildSearchPagination(input);
  return JSON.stringify({
    textSearch: state.textSearch ?? '',
    size: state.size,
    sorts: state.sorts ?? [],
    filterGroup: state.filterGroup
      ? {
          ...state.filterGroup,
          filters: state.filterGroup.filters?.map(({ id: _id, ...rest }) => rest),
        }
      : null,
  });
};

// Fixed namespace of the list-defaults keys (UUID v5): never change it, every
// stored list state would be read under a new key.
const LIST_DEFAULTS_NAMESPACE = '7f3c9a52-1d4e-4b8a-9c61-2e5f8d0b7a14';

// Stable key (UUID v5 of the fingerprint) of a list's defaults. It is part of
// the list's localStorage key, so a default changed in a later version gives
// the list a new key: the change reaches every user (their saved preferences on
// that list reset once) and the entries under the old key are simply no longer
// read (#4181).
export const listDefaultsHash = (defaults: Partial<SearchPaginationInput>) => uuidv5(listStateFingerprint(defaults), LIST_DEFAULTS_NAMESPACE);

// Resolved empty page for fetchers that must short-circuit without calling the
// API - typically when scoping a list on an empty id set, where an empty
// `contains` filter would match everything instead of nothing. Keeps the list
// component (search, filters, pagination) rendered with a zero-result state.
export const buildEmptyPage = <T>(input: SearchPaginationInput): { data: Page<T> } => {
  const size = input.size ?? DEFAULT_ROWS_PER_PAGE;
  // Echo the requested page (like the backend would) so the pagination state
  // stays consistent when a caller short-circuits from a non-zero page.
  const page = input.page ?? 0;
  const sort = {
    empty: true,
    sorted: false,
    unsorted: true,
  };
  return {
    data: {
      content: [],
      empty: true,
      first: page === 0,
      last: true,
      number: page,
      numberOfElements: 0,
      pageable: {
        offset: page * size,
        pageNumber: page,
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

// Client-side page for fetchers that resolve a small, already-loaded dataset
// (e.g. a fixed scope perimeter) without a paginated API: applies the requested
// page window over the given items and echoes backend-compatible Page metadata,
// so a list component (search, filters, pagination) behaves as with a real API.
export const buildClientPage = <T>(items: T[], input: SearchPaginationInput): { data: Page<T> } => {
  const size = input.size ?? DEFAULT_ROWS_PER_PAGE;
  const page = input.page ?? 0;
  const totalElements = items.length;
  const totalPages = size > 0 ? Math.ceil(totalElements / size) : 0;
  const offset = page * size;
  const content = items.slice(offset, offset + size);
  const sort = {
    empty: true,
    sorted: false,
    unsorted: true,
  };
  return {
    data: {
      content,
      empty: totalElements === 0,
      first: page === 0,
      last: totalPages === 0 || page >= totalPages - 1,
      number: page,
      numberOfElements: content.length,
      pageable: {
        offset,
        pageNumber: page,
        pageSize: size,
        paged: true,
        sort,
        unpaged: false,
      },
      size,
      sort,
      totalElements,
      totalPages,
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
