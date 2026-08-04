import { alpha, type SxProps, type Theme } from '@mui/material/styles';

// Shared card recipe for every attack-path canvas node, matching the chaining Logic view's
// GraphActionCard / GraphTriggerCard language: paper background, hairline border, a coloured left
// accent carrying the node's meaning (verdict / kind), soft resting shadow lifting on hover, and a
// primary ring when selected. Kept in a component-free module (react-refresh friendly).

export interface CardStateOptions {
  theme: Theme;
  /** Accent colour of the left bar (verdict / chokepoint / neutral). */
  accent: string;
  selected?: boolean;
  dimmed?: boolean;
  /** Dashed border (unknown/no-findings endpoints, collapsed clusters). */
  dashed?: boolean;
}

export const buildCardSx = ({ theme, accent, selected = false, dimmed = false, dashed = false }: CardStateOptions): SxProps<Theme> => ({
  'position': 'relative',
  'width': '100%',
  'height': '100%',
  'display': 'flex',
  'alignItems': 'center',
  'gap': 1,
  'padding': theme.spacing(0.75, 1),
  'boxSizing': 'border-box',
  'borderRadius': 1,
  'cursor': 'pointer',
  'opacity': dimmed ? 0.28 : 1,
  'border': `1px ${dashed ? 'dashed' : 'solid'} ${selected ? theme.palette.primary.main : theme.palette.divider}`,
  'borderLeft': `3px solid ${selected ? theme.palette.primary.main : accent}`,
  'backgroundColor': theme.palette.background.paper,
  'boxShadow': selected
    ? `0 0 0 1px ${theme.palette.primary.main}, ${theme.shadows[4]}`
    : theme.shadows[1],
  'transition': 'opacity 0.2s ease, border-color 0.15s ease, box-shadow 0.15s ease',
  '&:hover': {
    borderColor: theme.palette.primary.main,
    // Keep the verdict accent visible under the pointer (the border shorthand would repaint all
    // four sides).
    borderLeftColor: selected ? theme.palette.primary.main : accent,
    // A crisp 1px ring around the whole card — even on all four sides regardless of the thicker
    // left accent — so hovering reads as a FULL outline, not a three-sided one.
    boxShadow: `0 0 0 1px ${theme.palette.primary.main}, ${theme.shadows[4]}`,
  },
});

/**
 * Tinted square icon box on the card's left (GraphActionCard's icon slot). Two deliberate sizes
 * only: 'medium' for the primary cards (target), 'small' for the compact finding/cluster cards.
 */
export const buildIconBoxSx = (theme: Theme, accent: string, size: 'medium' | 'small' = 'medium'): SxProps<Theme> => ({
  'display': 'flex',
  'alignItems': 'center',
  'justifyContent': 'center',
  'width': size === 'small' ? 26 : 30,
  'height': size === 'small' ? 26 : 30,
  'flexShrink': 0,
  'borderRadius': 0.75,
  'overflow': 'hidden',
  'backgroundColor': alpha(accent, theme.palette.mode === 'dark' ? 0.16 : 0.1),
  'color': accent,
  '& svg': { fontSize: size === 'small' ? 16 : 18 },
  '& img': {
    maxWidth: '100%',
    maxHeight: '100%',
    objectFit: 'contain',
  },
});

/** Uppercase kicker line (node kind), GraphActionCard's eyebrow. */
export const EYEBROW_SX = {
  color: 'text.secondary',
  fontSize: '0.5625rem',
  fontWeight: 700,
  letterSpacing: '0.06em',
  textTransform: 'uppercase',
  lineHeight: 1.2,
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
  userSelect: 'none',
} as const;

/** Card title line. */
export const TITLE_SX = {
  fontSize: '0.8125rem',
  fontWeight: 600,
  lineHeight: 1.25,
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
} as const;

/** Title line of the compact (46-50px) finding/cluster cards — one step smaller, shared. */
export const TITLE_COMPACT_SX = {
  ...TITLE_SX,
  fontSize: '0.75rem',
} as const;

/** Small caption line under the title (ip, counts...). */
export const CAPTION_SX = {
  color: 'text.secondary',
  fontSize: '0.6875rem',
  lineHeight: 1.2,
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
} as const;
