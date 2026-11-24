import { useSyncExternalStore } from 'react';

import { type Filter, type FilterGroup } from '../../../utils/api-types';
import { getCurrentTenantId } from '../../../utils/url-helper';

/**
 * Custom views of the Threat Arsenal: a person's saved filters of the Active tab. Active, Available
 * and the review tab (To review or My changes) are built in and never change; a custom view starts
 * from what Active shows (its filters and search), keeps them under a name, and opens with them in one
 * click. Its filters can still be changed: the change is saved in the view, saved as a new view, or
 * discarded. A person shows up to three custom views as tabs next to the built-in ones (design system
 * Customize Panel), the others sit under "Custom views".
 * Custom views belong to the tenant they were made in: another tenant shows its own views.
 * DESIGN DEMO: custom views live in this browser only; the product would store them on the server.
 */
export interface CustomViewFilters {
  filterGroup?: FilterGroup;
  textSearch?: string;
}

export interface CustomView {
  id: string;
  label: string;
  filters: CustomViewFilters;
}

/** Custom views shown as tabs next to Active, Available and the review tab: six tabs at most. */
export const MAX_VIEW_TABS = 3;

/** Opens a custom view on the Active tab. */
export const VIEW_PARAM = 'view';
/** Opens the "New custom view" dialog on the Active tab. */
export const NEW_VIEW_PARAM = 'new-view';

export const ACTIVE_PATH = '/admin/threat-arsenal';
export const viewPath = (id: string) => `${ACTIVE_PATH}?${VIEW_PARAM}=${encodeURIComponent(id)}`;

/** Where the list of an open view keeps its filters while they are changed. */
export const viewStorageKey = (id: string) => `threat-arsenal:view:${id}`;

// The filters as they mean something: no generated ids, values and filters in a stable order.
const normalizedFilter = ({ id: _id, ...filter }: Filter) => ({
  ...filter,
  values: [...(filter.values ?? [])].sort(),
});
const normalized = (filters: CustomViewFilters) => JSON.stringify({
  mode: filters.filterGroup?.mode ?? 'and',
  filters: (filters.filterGroup?.filters ?? [])
    .map(normalizedFilter)
    .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))),
  textSearch: filters.textSearch ?? '',
});

export const sameViewFilters = (a: CustomViewFilters, b: CustomViewFilters) => normalized(a) === normalized(b);

// ---- Storage: the person's custom views and how they arranged them, shared by the tab bar and the page.

/** Order, shown or hidden, tab or under "Custom views" (design system Customize Panel). */
export interface ViewLayoutItem {
  id: string;
  visible: boolean;
  pinned: boolean;
}

interface Stored {
  views: CustomView[];
  layout: ViewLayoutItem[];
}

// Per tenant (like the lists' filters, see useQueryableWithLocalStorage), then per person.
const viewsKey = (me?: string) => `${getCurrentTenantId()}:threat-arsenal:custom-views:${me ?? 'anonymous'}`;
const layoutKey = (me?: string) => `${getCurrentTenantId()}:threat-arsenal:custom-views-layout:${me ?? 'anonymous'}`;

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

const isView = (value: unknown): value is CustomView => {
  const view = value as CustomView | null;
  return !!view && typeof view.id === 'string' && typeof view.label === 'string' && typeof view.filters === 'object' && !!view.filters;
};

/** The stored layout fitted to the views that exist: gone ones dropped, new ones added at the end. */
const fitLayout = (stored: ViewLayoutItem[], views: CustomView[]): ViewLayoutItem[] => {
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

const load = (me?: string): Stored => {
  const key = viewsKey(me);
  const known = cache.get(key);
  if (known) return known;
  const rawViews = read(key);
  const views = Array.isArray(rawViews) ? rawViews.filter(isView) : [];
  const rawLayout = read(layoutKey(me));
  const layout = fitLayout(Array.isArray(rawLayout) && rawLayout.every(item => typeof item === 'object') ? rawLayout as ViewLayoutItem[] : [], views);
  const stored = {
    views,
    layout,
  };
  cache.set(key, stored);
  return stored;
};

const save = (me: string | undefined, next: Stored) => {
  const stored = {
    views: next.views,
    layout: fitLayout(next.layout, next.views),
  };
  cache.set(viewsKey(me), stored);
  write(viewsKey(me), stored.views);
  write(layoutKey(me), stored.layout);
  listeners.forEach(listener => listener());
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

/** Only what a view keeps: the filters (without their generated ids) and the search. */
export const toViewFilters = (input: CustomViewFilters): CustomViewFilters => ({
  filterGroup: input.filterGroup
    ? {
        mode: input.filterGroup.mode,
        filters: (input.filterGroup.filters ?? []).map(({ id: _id, ...filter }) => filter as Filter),
      }
    : undefined,
  textSearch: input.textSearch ?? '',
});

/** A person's custom views, with the actions that change them. */
export const useCustomViews = (me?: string) => {
  const stored = useSyncExternalStore(subscribe, () => load(me));
  const byId = (id: string) => stored.views.find(view => view.id === id);
  const ordered = stored.layout
    .map(item => ({
      ...item,
      view: byId(item.id),
    }))
    .filter((item): item is ViewLayoutItem & { view: CustomView } => !!item.view);
  return {
    views: stored.views,
    layout: stored.layout,
    /** Shown as tabs, in the person's order. */
    tabs: ordered.filter(item => item.visible && item.pinned).map(item => item.view),
    /** Under the "Custom views" tab, in the person's order. */
    more: ordered.filter(item => item.visible && !item.pinned).map(item => item.view),
    byId,
    add: (label: string, filters: CustomViewFilters): CustomView => {
      const view = {
        id: `view-${Date.now()}`,
        label,
        filters: toViewFilters(filters),
      };
      save(me, {
        views: [...stored.views, view],
        layout: stored.layout,
      });
      return view;
    },
    rename: (id: string, label: string) => save(me, {
      views: stored.views.map(view => (view.id === id
        ? {
            ...view,
            label,
          }
        : view)),
      layout: stored.layout,
    }),
    saveFilters: (id: string, filters: CustomViewFilters) => save(me, {
      views: stored.views.map(view => (view.id === id
        ? {
            ...view,
            filters: toViewFilters(filters),
          }
        : view)),
      layout: stored.layout,
    }),
    remove: (id: string) => save(me, {
      views: stored.views.filter(view => view.id !== id),
      layout: stored.layout,
    }),
    arrange: (layout: ViewLayoutItem[]) => save(me, {
      views: stored.views,
      layout,
    }),
  };
};
