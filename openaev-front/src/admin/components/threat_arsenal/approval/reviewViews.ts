import { type QueueItem } from './useApprovalQueue';

/**
 * Views of the review pages, after GitHub's pull request and issue dashboards: a few built-in views
 * per role (To review for approvers, My changes for authors), plus views a person saves from the
 * current search and filters. DESIGN DEMO: saved views live in this browser only; the product would
 * store them per user on the server.
 */
export type ReviewSource = 'all' | 'people' | 'collectors';
export type ReviewKind = 'all' | 'new' | 'version';

export interface ReviewFilters {
  search: string;
  source: ReviewSource;
  kind: ReviewKind;
}

export interface ReviewView {
  id: string;
  label: string;
  /** Built-in base the saved filters apply to. */
  base: string;
  filters: ReviewFilters;
  saved?: boolean;
}

export interface ReviewContext {
  me?: string;
  status: (item: QueueItem) => string | undefined;
  fromCollector: (item: QueueItem) => boolean;
  isLaterVersion: (item: QueueItem) => boolean;
}

export const NO_FILTERS: ReviewFilters = {
  search: '',
  source: 'all',
  kind: 'all',
};

type Base = (item: QueueItem, context: ReviewContext) => boolean;

const mine = (item: QueueItem, context: ReviewContext) => !!context.me && item.full?.action_last_modified_by === context.me;

// What each built-in view lists, before the search and filters.
export const REVIEW_BASES: Record<string, Base> = {
  'needs-review': (item, c) => c.status(item) === 'PENDING' && !mine(item, c),
  'from-collectors': (item, c) => c.status(item) === 'PENDING' && c.fromCollector(item),
  'rejected': (item, c) => c.status(item) === 'REJECTED',
  'approved': (item, c) => c.status(item) === 'APPROVED',
  'waiting': (item, c) => c.status(item) === 'PENDING' && mine(item, c),
  'needs-changes': (item, c) => c.status(item) === 'REJECTED' && mine(item, c),
  'all-mine': (item, c) => mine(item, c),
};

export const builtInViews = (mode: 'to-review' | 'my-changes'): ReviewView[] => (mode === 'to-review'
  ? [
      {
        id: 'needs-review',
        label: 'Needs your review',
        base: 'needs-review',
        filters: NO_FILTERS,
      },
      {
        id: 'from-collectors',
        label: 'From collectors',
        base: 'from-collectors',
        filters: NO_FILTERS,
      },
      {
        id: 'rejected',
        label: 'Rejected',
        base: 'rejected',
        filters: NO_FILTERS,
      },
      {
        id: 'approved',
        label: 'Recently approved',
        base: 'approved',
        filters: NO_FILTERS,
      },
    ]
  : [
      {
        id: 'waiting',
        label: 'Waiting for review',
        base: 'waiting',
        filters: NO_FILTERS,
      },
      {
        id: 'needs-changes',
        label: 'Needs your changes',
        base: 'needs-changes',
        filters: NO_FILTERS,
      },
      {
        id: 'all-mine',
        label: 'All my changes',
        base: 'all-mine',
        filters: NO_FILTERS,
      },
    ]);

export const matches = (item: QueueItem, view: ReviewView, filters: ReviewFilters, context: ReviewContext, name: string) => {
  const base = REVIEW_BASES[view.base];
  if (!base || !base(item, context)) return false;
  if (filters.source === 'people' && context.fromCollector(item)) return false;
  if (filters.source === 'collectors' && !context.fromCollector(item)) return false;
  if (filters.kind === 'new' && context.isLaterVersion(item)) return false;
  if (filters.kind === 'version' && !context.isLaterVersion(item)) return false;
  return !filters.search || name.toLowerCase().includes(filters.search.toLowerCase());
};

const storageKey = (mode: string, me?: string) => `threat-arsenal:review-views:${mode}:${me ?? 'anonymous'}`;

export const readSavedViews = (mode: string, me?: string): ReviewView[] => {
  try {
    const raw = window.localStorage.getItem(storageKey(mode, me));
    return raw ? (JSON.parse(raw) as ReviewView[]) : [];
  } catch {
    return [];
  }
};

export const writeSavedViews = (mode: string, me: string | undefined, views: ReviewView[]) => {
  try {
    window.localStorage.setItem(storageKey(mode, me), JSON.stringify(views));
  } catch {
    // Storage unavailable: the view lasts for this page only.
  }
};
