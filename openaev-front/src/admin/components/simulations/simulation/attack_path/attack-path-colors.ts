import { type Theme } from '@mui/material/styles';

// The backend colours by worst-case severity combining prevention and detection: GREEN = prevented,
// ORANGE = detected but not prevented, RED = neither. Map those onto the theme's semantic palette so
// the graph respects light/dark mode; anything without a status is neutral blue (primary).
const attackPathStatusColor = (theme: Theme, status?: string): string => {
  switch (status) {
    case 'GREEN':
      return theme.palette.success.main;
    case 'ORANGE':
      return theme.palette.warning.main;
    case 'RED':
      return theme.palette.error.main;
    default:
      return theme.palette.primary.main;
  }
};

// Text equivalent of the verdict colour, so status is never conveyed by colour alone (a11y). Returns
// an English key; wrap in t() at the call site.
export const attackPathStatusLabel = (status?: string): string => {
  switch (status) {
    case 'GREEN':
      return 'Prevented';
    case 'ORANGE':
      return 'Detected, not prevented';
    case 'RED':
      return 'Neither prevented nor detected';
    default:
      return 'No verdict';
  }
};

// Chokepoint accent — deliberately OUTSIDE the green/orange/red verdict scale and the primary blue,
// so "most exposed endpoint" never reads as a prevention/detection verdict. Violet in both themes.
export const attackPathChokepointColor = (theme: Theme): string =>
  (theme.palette.mode === 'dark' ? '#b388ff' : '#7c4dff');

// Causal / event edge accent (the "Triggered <event>" link between a finding and the action it
// triggers). Deliberately a distinct magenta — not green/orange/red (verdicts), not the primary blue
// (selection), not the violet chokepoint accent — so a causal link never reads as a prevention verdict.
export const attackPathCausalColor = (theme: Theme): string =>
  (theme.palette.mode === 'dark' ? '#ff80ab' : '#e91e63');

export default attackPathStatusColor;
