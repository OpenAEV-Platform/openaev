import { type CSSProperties } from 'react';

// Shared column widths so the header row and the body rows line up. Percentages
// keep the list fluid, matching the standard platform list pages.
export const THREAT_ARSENAL_LIST_INLINE_STYLES: Record<string, CSSProperties> = {
  action_name: { width: '28%' },
  action_domains: { width: '18%' },
  action_platforms: { width: '15%' },
  action_tags: { width: '17%' },
  action_status: { width: '12%' },
  action_updated: { width: '10%' },
};

export const THREAT_ARSENAL_LIST_HEADERS = [
  {
    field: 'action_name',
    label: 'Name',
  },
  {
    field: 'action_domains',
    label: 'Domains',
  },
  {
    field: 'action_platforms',
    label: 'Platforms',
  },
  {
    field: 'action_tags',
    label: 'Tags',
  },
  {
    field: 'action_status',
    label: 'Status',
  },
  {
    field: 'action_updated',
    label: 'Updated',
  },
];
