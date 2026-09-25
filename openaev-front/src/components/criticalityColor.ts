import { type ChipSeverity } from '@filigran/design-system';
import { type CSSProperties } from 'react';

// Single source of truth for the asset-criticality palette, so criticality reads the SAME everywhere it is
// shown — the ItemCriticality chip, the attack-path chokepoint bars, etc. Aligns with the app severity
// palette: green = low risk, escalating to red for the most critical assets (UNKNOWN is a neutral grey).
const CRITICALITY_STYLE: Record<string, CSSProperties> = {
  LOW: {
    backgroundColor: 'var(--color-feedback-success-secondary-transparency-30)',
    color: 'var(--color-feedback-success-primary)',
  },
  MEDIUM: {
    backgroundColor: 'var(--color-feedback-info-secondary-transparency-30)',
    color: 'var(--color-feedback-info-primary)',
  },
  HIGH: {
    backgroundColor: 'var(--color-feedback-warning-secondary-transparency-30)',
    color: 'var(--color-feedback-warning-primary)',
  },
  VERY_HIGH: {
    backgroundColor: 'var(--color-feedback-error-secondary-transparency-30)',
    color: 'var(--color-feedback-error-primary)',
  },
  UNKNOWN: {
    backgroundColor: 'var(--color-feedback-neutral-secondary-transparency-30)',
    color: 'var(--color-feedback-neutral-primary)',
    fontStyle: 'italic',
  },
};

// The library severity for a criticality chip (same reading as the palette above: green low → red critical, unknown neutral).
const CRITICALITY_SEVERITY: Record<string, ChipSeverity> = {
  LOW: 'low',
  MEDIUM: 'info',
  HIGH: 'high',
  VERY_HIGH: 'critical',
  UNKNOWN: 'neutral',
};
export const criticalitySeverity = (criticality: string | undefined | null): ChipSeverity =>
  CRITICALITY_SEVERITY[criticality ?? 'UNKNOWN'] ?? CRITICALITY_SEVERITY.UNKNOWN;

// The full chip style (background + accent) for a criticality; unknown/absent falls back to neutral grey.
export const criticalityStyle = (criticality: string | undefined | null): CSSProperties =>
  CRITICALITY_STYLE[criticality ?? 'UNKNOWN'] ?? CRITICALITY_STYLE.UNKNOWN;

// The accent colour alone, for callers that colour their own elements (e.g. the chokepoint exposure bars).
export const criticalityColor = (criticality: string | undefined | null): string =>
  (criticalityStyle(criticality).color as string);
