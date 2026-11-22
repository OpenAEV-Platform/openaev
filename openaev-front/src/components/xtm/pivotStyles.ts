// A state with no action (no match, unavailable, cannot connect) is a status: an FDS Chip on a
// focusable wrapper, the trigger of its explanation, centred in the slot of the 36 px pivot.
// Kept out of the component files so they export components only (fast refresh).
// The OpenAEV global :focus reset removes the outline: the wrapper draws the focus ring of the FDS
// Button (2 px gap, 2 px ring, token filigran-brand-primary) so a keyboard user sees it.
export const STATUS_TRIGGER = {
  'display': 'inline-flex',
  'alignItems': 'center',
  'minHeight': 36,
  'borderRadius': 1,
  '&:focus-visible': {
    outlineWidth: '2px',
    outlineStyle: 'solid',
    outlineColor: 'var(--color-filigran-brand-primary)',
    outlineOffset: '2px',
  },
} as const;
