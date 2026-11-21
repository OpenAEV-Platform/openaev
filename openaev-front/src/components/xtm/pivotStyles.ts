// A state with no action (no match, unavailable, cannot connect) is a status: an FDS Chip on a
// focusable wrapper, the trigger of its explanation, centred in the slot of the 36 px pivot.
// Kept out of the component files so they export components only (fast refresh).
export const STATUS_TRIGGER = {
  display: 'inline-flex',
  alignItems: 'center',
  minHeight: 36,
  borderRadius: 1,
} as const;
