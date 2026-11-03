import { type QueueItem } from './useApprovalQueue';

/**
 * Views of the review pages, after GitHub Projects: each view is a tab holding a saved filter
 * (base set + search + source + type) and a "slice by" field; built-in views per role (To review
 * for approvers, My changes for authors), plus views a person adds with "+ New view". DESIGN DEMO:
 * saved views live in this browser only; the product would store them per user on the server.
 * Beyond a few views, only the pinned ones are tabs and the rest sit under "More views".
 */
export type ReviewSource = 'all' | 'people' | 'collectors';
export type ReviewKind = 'all' | 'new' | 'version';
export type SliceField = 'author' | 'domain' | 'source' | 'kind';

export interface ReviewFilters {
  search: string;
  source: ReviewSource;
  kind: ReviewKind;
  sliceField: SliceField;
  /** Value picked in the slice panel; null shows every value. */
  sliceValue: string | null;
}

export interface ReviewView {
  id: string;
  label: string;
  /** Built-in base the filters apply to. */
  base: string;
  filters: ReviewFilters;
  saved?: boolean;
}

export interface ReviewContext {
  me?: string;
  status: (item: QueueItem) => string | undefined;
  fromCollector: (item: QueueItem) => boolean;
  isLaterVersion: (item: QueueItem) => boolean;
  author: (item: QueueItem) => string;
  domain: (item: QueueItem) => string;
}

export const NO_FILTERS: ReviewFilters = {
  search: '',
  source: 'all',
  kind: 'all',
  sliceField: 'author',
  sliceValue: null,
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

// Authors only see their own changes: slicing them by who submitted would show one value.
const view = (id: string, label: string, sliceField: SliceField = 'author'): ReviewView => ({
  id,
  label,
  base: id,
  filters: {
    ...NO_FILTERS,
    sliceField,
  },
});

export const builtInViews = (mode: 'to-review' | 'my-changes'): ReviewView[] => (mode === 'to-review'
  ? [view('needs-review', 'Needs your review'), view('from-collectors', 'From collectors'), view('rejected', 'Rejected'), view('approved', 'Recently approved')]
  : [view('waiting', 'Waiting for review', 'domain'), view('needs-changes', 'Needs your changes', 'domain'), view('all-mine', 'All my changes', 'domain')]);

export const sliceValueOf = (item: QueueItem, field: SliceField, context: ReviewContext): string => {
  if (field === 'source') return context.fromCollector(item) ? 'collectors' : 'people';
  if (field === 'kind') return context.isLaterVersion(item) ? 'version' : 'new';
  if (field === 'domain') return context.domain(item);
  return context.author(item);
};

/** Everything but the slice: what the slice panel counts. */
export const matchesFilters = (item: QueueItem, view: ReviewView, filters: ReviewFilters, context: ReviewContext, name: string) => {
  const base = REVIEW_BASES[view.base];
  if (!base || !base(item, context)) return false;
  if (filters.source === 'people' && context.fromCollector(item)) return false;
  if (filters.source === 'collectors' && !context.fromCollector(item)) return false;
  if (filters.kind === 'new' && context.isLaterVersion(item)) return false;
  if (filters.kind === 'version' && !context.isLaterVersion(item)) return false;
  return !filters.search || name.toLowerCase().includes(filters.search.toLowerCase());
};

export const matches = (item: QueueItem, view: ReviewView, filters: ReviewFilters, context: ReviewContext, name: string) => matchesFilters(item, view, filters, context, name)
  && (filters.sliceValue === null || sliceValueOf(item, filters.sliceField, context) === filters.sliceValue);

export const sameFilters = (a: ReviewFilters, b: ReviewFilters) => a.search === b.search && a.source === b.source && a.kind === b.kind
  && a.sliceField === b.sliceField && a.sliceValue === b.sliceValue;

const storageKey = (mode: string, me?: string) => `threat-arsenal:review-views:${mode}:${me ?? 'anonymous'}`;
const pinnedKey = (mode: string, me?: string) => `threat-arsenal:review-views-pinned:${mode}:${me ?? 'anonymous'}`;

/** Up to this many views, all are tabs; beyond, only the pinned ones (at most MAX_PINNED_VIEWS). */
export const ALL_VIEWS_AS_TABS = 4;
// The DS Customize Panel's pin limit (RULE-09).
export const MAX_PINNED_VIEWS = 5;

/** How a person arranged the views: order, shown or hidden, tab or "More views" (DS Customize Panel). */
export interface ViewLayoutItem {
  id: string;
  visible: boolean;
  pinned: boolean;
}

/** The stored layout fitted to the views that exist: gone ones dropped, new ones added at the end. */
export const fitLayout = (stored: ViewLayoutItem[] | null, views: ReviewView[]): ViewLayoutItem[] => {
  const ids = new Set(views.map(view => view.id));
  const kept = (stored ?? []).filter(item => ids.has(item.id));
  let pinnedCount = kept.filter(item => item.pinned).length;
  const added = views.filter(view => !kept.some(item => item.id === view.id)).map((view) => {
    // A view starts as a tab while there is room (built-in ones first), otherwise under "More views".
    const pinned = pinnedCount < MAX_PINNED_VIEWS;
    if (pinned) pinnedCount += 1;
    return {
      id: view.id,
      visible: true,
      pinned,
    };
  });
  return [...kept, ...added];
};

export const readViewLayout = (mode: string, me?: string): ViewLayoutItem[] | null => {
  try {
    const raw = window.localStorage.getItem(pinnedKey(mode, me));
    const parsed = raw ? JSON.parse(raw) as unknown : null;
    // Pins saved before the layout existed were a list of ids.
    return Array.isArray(parsed) && parsed.every(item => typeof item === 'object') ? parsed as ViewLayoutItem[] : null;
  } catch {
    return null;
  }
};

export const writeViewLayout = (mode: string, me: string | undefined, layout: ViewLayoutItem[]) => {
  try {
    window.localStorage.setItem(pinnedKey(mode, me), JSON.stringify(layout));
  } catch {
    // Storage unavailable: the layout lasts for this page only.
  }
};

export const readSavedViews = (mode: string, me?: string): ReviewView[] => {
  try {
    const raw = window.localStorage.getItem(storageKey(mode, me));
    // Views saved before the slice existed get the default slice.
    return raw
      ? (JSON.parse(raw) as ReviewView[]).map(v => ({
          ...v,
          filters: {
            ...NO_FILTERS,
            ...v.filters,
            // Authors never slice by author.
            ...(mode === 'my-changes' && (v.filters?.sliceField ?? 'author') === 'author' ? { sliceField: 'domain' as SliceField } : {}),
          },
        }))
      : [];
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
