import { useSyncExternalStore } from 'react';

import { type QueueItem } from './useApprovalQueue';

/**
 * The review tab of the Threat Arsenal (To review for approvers, My changes for authors) and the
 * custom views a person saves from it. Its side filters are the Active tab's (domains, platform,
 * status, approval) plus what only a review needs: new action or new version, where it comes from,
 * who submitted it. A custom view is a saved set of those filters (and the search). Active, Available
 * and the review tab are always the first tabs, for everyone; a person shows up to three custom views
 * as tabs next to them (design system Customize Panel), the others sit under "Custom views".
 * DESIGN DEMO: custom views live in this browser only; the product would store them per user.
 */
export type ReviewMode = 'to-review' | 'my-changes';
export type FacetKey = 'domain' | 'platform' | 'status' | 'approval' | 'kind' | 'source' | 'author';

export interface ReviewFilters {
  search: string;
  /** Ticked values per side filter; a filter with nothing ticked keeps everything. */
  facets: Partial<Record<FacetKey, string[]>>;
}

export interface ReviewView {
  id: string;
  label: string;
  filters: ReviewFilters;
}

export interface ReviewContext {
  me?: string;
  status: (item: QueueItem) => string | undefined;
  fromCollector: (item: QueueItem) => boolean;
  isLaterVersion: (item: QueueItem) => boolean;
  author: (item: QueueItem) => string;
  domain: (item: QueueItem) => string;
}

/** Custom views shown as tabs next to Active, Available and the review tab: six tabs at most. */
export const MAX_VIEW_TABS = 3;

/** What the review tab lists when it opens: what waits for a decision. */
export const defaultFilters = (): ReviewFilters => ({
  search: '',
  facets: { approval: ['PENDING'] },
});

const mine = (item: QueueItem, context: ReviewContext) => !!context.me && item.full?.action_last_modified_by === context.me;

/** Before any filter: approvers review what others submitted, authors follow their own changes. */
export const inScope = (item: QueueItem, mode: ReviewMode, context: ReviewContext) => (mode === 'to-review' ? !mine(item, context) : mine(item, context));

/** The values of an item for one side filter (an action can run on several platforms). */
export const facetValues = (item: QueueItem, key: FacetKey, context: ReviewContext): string[] => {
  const payload = item.action.action_payload;
  switch (key) {
    case 'domain': return [context.domain(item)];
    case 'platform': return (item.action.action_platforms ?? []) as string[];
    case 'status': {
      const status = payload?.payload_revoked ? 'REVOKED' : payload?.payload_status;
      return status ? [status] : [];
    }
    case 'approval': {
      const status = context.status(item);
      return status ? [status] : [];
    }
    case 'kind': return [context.isLaterVersion(item) ? 'version' : 'new'];
    case 'source': return [context.fromCollector(item) ? 'collectors' : 'people'];
    default: return [context.author(item)];
  }
};

const passes = (item: QueueItem, filters: ReviewFilters, context: ReviewContext, except?: FacetKey) => (Object.entries(filters.facets) as [FacetKey, string[]][])
  .every(([key, ticked]) => key === except || ticked.length === 0 || facetValues(item, key, context).some(value => ticked.includes(value)));

const searched = (filters: ReviewFilters, name: string) => !filters.search || name.toLowerCase().includes(filters.search.toLowerCase());

export const matches = (item: QueueItem, filters: ReviewFilters, context: ReviewContext, name: string) => passes(item, filters, context) && searched(filters, name);

/** Counts of one side filter: what the other filters and the search keep (usual faceting). */
export const facetCounts = (items: QueueItem[], key: FacetKey, filters: ReviewFilters, context: ReviewContext, name: (item: QueueItem) => string) => {
  const counts: Record<string, number> = {};
  items
    .filter(item => passes(item, filters, context, key) && searched(filters, name(item)))
    .forEach(item => facetValues(item, key, context).forEach((value) => {
      counts[value] = (counts[value] ?? 0) + 1;
    }));
  return counts;
};

const sorted = (values?: string[]) => [...(values ?? [])].sort().join('|');

export const sameFilters = (a: ReviewFilters, b: ReviewFilters) => a.search === b.search
  && [...new Set([...Object.keys(a.facets), ...Object.keys(b.facets)])]
    .every(key => sorted(a.facets[key as FacetKey]) === sorted(b.facets[key as FacetKey]));

// ---- Storage: the person's custom views and how they arranged them, shared by the tab bar and the page.

/** Order, shown or hidden, tab or under "Custom views" (design system Customize Panel). */
export interface ViewLayoutItem {
  id: string;
  visible: boolean;
  pinned: boolean;
}

interface Stored {
  views: ReviewView[];
  layout: ViewLayoutItem[];
}

const viewsKey = (mode: ReviewMode, me?: string) => `threat-arsenal:review-views:${mode}:${me ?? 'anonymous'}`;
const layoutKey = (mode: ReviewMode, me?: string) => `threat-arsenal:review-views-pinned:${mode}:${me ?? 'anonymous'}`;

const read = (key: string): unknown => {
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) as unknown : null;
  } catch {
    return null;
  }
};

const write = (key: string, value: unknown) => {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage unavailable: the change lasts for this page only.
  }
};

interface LegacyFilters {
  search?: string;
  source?: string;
  kind?: string;
  sliceField?: string;
  sliceValue?: string | null;
}

// Views saved before the side filters: their built-in base and drop-downs become ticked filters.
const LEGACY_BASES: Record<string, string[]> = {
  'needs-review': ['PENDING'],
  'from-collectors': ['PENDING'],
  'rejected': ['REJECTED'],
  'approved': ['APPROVED'],
  'waiting': ['PENDING'],
  'needs-changes': ['REJECTED'],
};

const upgrade = (view: ReviewView & { base?: string }): ReviewView => {
  const legacy = view.filters as unknown as LegacyFilters & Partial<ReviewFilters>;
  if (legacy.facets) return view;
  const facets: ReviewFilters['facets'] = {};
  if (view.base && LEGACY_BASES[view.base]) facets.approval = LEGACY_BASES[view.base];
  if (view.base === 'from-collectors') facets.source = ['collectors'];
  if (legacy.source && legacy.source !== 'all') facets.source = [legacy.source];
  if (legacy.kind && legacy.kind !== 'all') facets.kind = [legacy.kind];
  if (legacy.sliceValue && legacy.sliceField) facets[legacy.sliceField as FacetKey] = [legacy.sliceValue];
  return {
    id: view.id,
    label: view.label,
    filters: {
      search: legacy.search ?? '',
      facets,
    },
  };
};

/** The stored layout fitted to the views that exist: gone ones dropped, new ones added at the end. */
const fitLayout = (stored: ViewLayoutItem[], views: ReviewView[]): ViewLayoutItem[] => {
  const ids = new Set(views.map(view => view.id));
  const kept = stored.filter(item => ids.has(item.id));
  let pinnedCount = kept.filter(item => item.pinned).length;
  const added = views.filter(view => !kept.some(item => item.id === view.id)).map((view) => {
    // A new view is a tab while there is room, otherwise it goes under "Custom views".
    const pinned = pinnedCount < MAX_VIEW_TABS;
    if (pinned) pinnedCount += 1;
    return {
      id: view.id,
      visible: true,
      pinned,
    };
  });
  return [...kept, ...added];
};

const cache = new Map<string, Stored>();
const listeners = new Set<() => void>();

const load = (mode: ReviewMode, me?: string): Stored => {
  const key = viewsKey(mode, me);
  const known = cache.get(key);
  if (known) return known;
  const rawViews = read(key);
  const views = Array.isArray(rawViews) ? (rawViews as (ReviewView & { base?: string })[]).map(upgrade) : [];
  const rawLayout = read(layoutKey(mode, me));
  const layout = fitLayout(Array.isArray(rawLayout) && rawLayout.every(item => typeof item === 'object') ? rawLayout as ViewLayoutItem[] : [], views);
  const stored = {
    views,
    layout,
  };
  cache.set(key, stored);
  return stored;
};

const save = (mode: ReviewMode, me: string | undefined, next: Stored) => {
  const stored = {
    views: next.views,
    layout: fitLayout(next.layout, next.views),
  };
  cache.set(viewsKey(mode, me), stored);
  write(viewsKey(mode, me), stored.views);
  write(layoutKey(mode, me), stored.layout);
  listeners.forEach(listener => listener());
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

/** A person's custom views for one review tab, with the actions that change them. */
export const useReviewViews = (mode: ReviewMode, me?: string) => {
  const stored = useSyncExternalStore(subscribe, () => load(mode, me));
  const byId = (id: string) => stored.views.find(view => view.id === id);
  const ordered = stored.layout
    .map(item => ({
      ...item,
      view: byId(item.id),
    }))
    .filter((item): item is ViewLayoutItem & { view: ReviewView } => !!item.view);
  return {
    views: stored.views,
    layout: stored.layout,
    /** Shown as tabs, in the person's order. */
    tabs: ordered.filter(item => item.visible && item.pinned).map(item => item.view),
    /** Under the "Custom views" tab, in the person's order. */
    more: ordered.filter(item => item.visible && !item.pinned).map(item => item.view),
    byId,
    add: (label: string, filters: ReviewFilters): ReviewView => {
      const view = {
        id: `view-${Date.now()}`,
        label,
        filters,
      };
      save(mode, me, {
        views: [...stored.views, view],
        layout: stored.layout,
      });
      return view;
    },
    update: (id: string, patch: Partial<Omit<ReviewView, 'id'>>) => save(mode, me, {
      views: stored.views.map(view => (view.id === id
        ? {
            ...view,
            ...patch,
          }
        : view)),
      layout: stored.layout,
    }),
    remove: (id: string) => save(mode, me, {
      views: stored.views.filter(view => view.id !== id),
      layout: stored.layout,
    }),
    arrange: (layout: ViewLayoutItem[]) => save(mode, me, {
      views: stored.views,
      layout,
    }),
  };
};
