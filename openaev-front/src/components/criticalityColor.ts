import { type CSSProperties } from 'react';

// Single source of truth for the asset-criticality palette, so criticality reads the SAME everywhere it is
// shown — the ItemCriticality chip, the attack-path chokepoint bars, etc. Aligns with the app severity
// palette: green = low risk, escalating to red for the most critical assets (UNKNOWN is a neutral grey).
const CRITICALITY_STYLE: Record<string, CSSProperties> = {
  LOW: {
    backgroundColor: 'rgba(76, 175, 80, 0.08)',
    color: '#4caf50',
  },
  MEDIUM: {
    backgroundColor: 'rgba(92, 123, 245, 0.08)',
    color: '#5c7bf5',
  },
  HIGH: {
    backgroundColor: 'rgba(255, 152, 0, 0.08)',
    color: '#ff9800',
  },
  VERY_HIGH: {
    backgroundColor: 'rgba(244, 67, 54, 0.08)',
    color: '#f44336',
  },
  UNKNOWN: {
    backgroundColor: 'rgba(96, 125, 139, 0.08)',
    color: '#607d8b',
    fontStyle: 'italic',
  },
};

// The full chip style (background + accent) for a criticality; unknown/absent falls back to neutral grey.
export const criticalityStyle = (criticality: string | undefined | null): CSSProperties =>
  CRITICALITY_STYLE[criticality ?? 'UNKNOWN'] ?? CRITICALITY_STYLE.UNKNOWN;

// The accent colour alone, for callers that colour their own elements (e.g. the chokepoint exposure bars).
export const criticalityColor = (criticality: string | undefined | null): string =>
  (criticalityStyle(criticality).color as string);
