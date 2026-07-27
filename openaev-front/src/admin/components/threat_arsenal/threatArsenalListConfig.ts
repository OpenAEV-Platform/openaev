import { type CSSProperties } from 'react';

import { type Header } from '../../../components/common/SortHeadersList';

// Shared column widths so the header row and the body rows line up. Percentages
// keep the list fluid, matching the standard platform list pages.
// Sortable columns use the backend sort keys (`action_labels`,
// `action_updated_at`) so the sort headers write valid sort properties.
export const THREAT_ARSENAL_LIST_INLINE_STYLES: Record<string, CSSProperties> = {
  action_labels: { width: '28%' },
  action_domains: { width: '18%' },
  action_platforms: { width: '15%' },
  action_tags: { width: '17%' },
  action_status: { width: '12%' },
  action_updated_at: { width: '10%' },
};

// Only `action_labels` and `action_updated_at` are sortable server-side
// (@Queryable(sortable = true) on the InjectorContract entity).
export const THREAT_ARSENAL_LIST_HEADERS: Header[] = [
  {
    field: 'action_labels',
    label: 'Name',
    isSortable: true,
  },
  {
    field: 'action_domains',
    label: 'Domains',
    isSortable: false,
  },
  {
    field: 'action_platforms',
    label: 'Platforms',
    isSortable: false,
  },
  {
    field: 'action_tags',
    label: 'Tags',
    isSortable: false,
  },
  {
    field: 'action_status',
    label: 'Status',
    isSortable: false,
  },
  {
    field: 'action_updated_at',
    label: 'Updated',
    isSortable: true,
  },
];

// Sort options offered by the card-view "Sort by" select. Mirrors the sortable
// columns of the list view.
export const THREAT_ARSENAL_SORT_OPTIONS = [
  {
    field: 'action_labels',
    label: 'Name',
  },
  {
    field: 'action_updated_at',
    label: 'Updated',
  },
];
