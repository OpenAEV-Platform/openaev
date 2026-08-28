import { type Theme } from '@mui/material';

/**
 * Derives semantic colors from series names: defensive successes render green,
 * misses render red, pending states grey. Returns null when no series name
 * carries a semantic meaning, so callers can fall back to default palettes.
 */
const AMBER = '#ff9800';
const ORANGE = '#ff7043';

const seriesSemanticColors = (theme: Theme, names: (string | undefined)[]): string[] | null => {
  let matched = false;
  const colors = names.map((name, index) => {
    const upper = (name ?? '').toUpperCase();
    // Misses: keep detection vs prevention distinguishable (red vs amber).
    if (upper.includes('UNDETECTED') || upper.includes('NOT DETECTED')) {
      matched = true;
      return theme.palette.error.main;
    }
    if (upper.includes('UNPREVENTED') || upper.includes('NOT PREVENTED')) {
      matched = true;
      return AMBER;
    }
    if (upper.includes('NOT ') || upper.includes('FAILED') || upper.includes('MISSED')) {
      matched = true;
      return theme.palette.error.main;
    }
    // Wins: detection (secondary/teal) vs prevention (green) stay distinct too.
    if (upper.includes('DETECTED')) {
      matched = true;
      return theme.palette.secondary.main;
    }
    if (upper.includes('PREVENTED') || upper.includes('SUCCESS')) {
      matched = true;
      return theme.palette.success.main;
    }
    if (upper.includes('PENDING')) {
      matched = true;
      return theme.palette.text.disabled;
    }
    return [theme.palette.primary.main, theme.palette.secondary.main, AMBER, ORANGE][index % 4];
  });
  return matched ? colors : null;
};

export default seriesSemanticColors;
