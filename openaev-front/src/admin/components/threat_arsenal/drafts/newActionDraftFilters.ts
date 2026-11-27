import { type Filter, type PayloadDraftOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { DRAFT_STATUS_VALUE } from '../approval/approvalStatusUtils';

const STATUS_FILTER_KEY = 'action_payload_status';
// A view option of the list (also show older versions), not a property a draft could fail.
const VIEW_ONLY_FILTER_KEYS = new Set(['action_all_versions']);
const EXCLUDING_OPERATORS = new Set(['not_eq', 'not_contains', 'not_starts_with', 'not_empty']);

/** The name typed in the Create form of a draft of a new action, or '' when none yet. */
export const newActionDraftName = (draft: PayloadDraftOutput): string => {
  const name = (draft.draft_content as { action_name?: unknown } | undefined)?.action_name;
  return typeof name === 'string' ? name.trim() : '';
};

const hasValues = (filter: Filter) => (filter.values?.length ?? 0) > 0 || filter.operator === 'empty' || filter.operator === 'not_empty';

// A draft of a new action has none of the properties the other filters look at (domain, platform,
// approval, tags...): any of them active leaves it out, the status filter aside.
const otherFiltersActive = (input: SearchPaginationInput) => (input.filterGroup?.filters ?? [])
  .some(filter => filter.key !== STATUS_FILTER_KEY && !VIEW_ONLY_FILTER_KEYS.has(filter.key) && hasValues(filter));

const statusFilterKeepsDrafts = (input: SearchPaginationInput) => {
  const status = (input.filterGroup?.filters ?? []).find(filter => filter.key === STATUS_FILTER_KEY);
  if (!status || !hasValues(status)) return true;
  const draftTicked = (status.values ?? []).includes(DRAFT_STATUS_VALUE);
  return EXCLUDING_OPERATORS.has(status.operator ?? '') ? !draftTicked : draftTicked;
};

const matchesSearch = (draft: PayloadDraftOutput, input: SearchPaginationInput) => {
  const search = (input.textSearch ?? '').trim().toLowerCase();
  return !search || newActionDraftName(draft).toLowerCase().includes(search);
};

/**
 * The drafts of new actions the Active list shows under its filters and search: all of them with no
 * filter, the ones whose name matches the search, and none once a filter they cannot match is on
 * (a status other than Draft, a domain, a platform...).
 */
export const shownNewActionDrafts = (drafts: PayloadDraftOutput[], input: SearchPaginationInput) => {
  if (otherFiltersActive(input) || !statusFilterKeepsDrafts(input)) return [];
  return drafts.filter(draft => matchesSearch(draft, input));
};

/**
 * How many drafts of new actions the Draft value of the Status filter adds to its count: the ones the
 * search keeps, whatever is ticked in the Status filter itself (like the other rows of a facet).
 */
export const newActionDraftCount = (drafts: PayloadDraftOutput[], input: SearchPaginationInput) => {
  if (otherFiltersActive(input)) return 0;
  return drafts.filter(draft => matchesSearch(draft, input)).length;
};
