import { type ChipSeverity } from '@filigran/design-system';

export const stringToColour = (str: string | null | undefined, reversed = false): string => {
  if (!str) {
    return '#5d4037';
  }
  if (str === 'true') {
    if (reversed) {
      return '#bf360c';
    }
    return '#2e7d32';
  }
  if (str === 'false') {
    if (reversed) {
      return '#2e7d32';
    }
    return '#bf360c';
  }
  let hash = 0;
  for (let i = 0; i < str.length; i += 1) {
    // eslint-disable-next-line no-bitwise
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  let colour = '#';
  for (let i = 0; i < 3; i += 1) {
    // eslint-disable-next-line no-bitwise
    const value = (hash >> (i * 8)) & 0xff;
    colour += `00${value.toString(16)}`.slice(-2);
  }
  return colour;
};

export const hexToRGB = (hex: string, transp = 0.1): string => {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${transp})`;
};

export interface SeverityAndColor {
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
  color: string;
}

/**
 * The library severity a CVSS band reads as. The library Chip takes a tone,
 * not a colour — its `color` prop accepts a hex only and would reject a token
 * — so a consumer that renders a chip passes this instead of `color`.
 */
export const CVSS_SEVERITY: Record<SeverityAndColor['severity'], ChipSeverity> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'info',
  LOW: 'low',
  NONE: 'neutral',
};

// CVSS - hex colors aligned with the severity chip palette used across the app
// (see ItemSeverity / ItemCriticality) so CVSS reads the same everywhere.
export const getSeverityAndColor = (score: number | string | null | undefined): SeverityAndColor => {
  const numScore = typeof score === 'string' ? parseFloat(score) : (score ?? 0);

  if (numScore >= 9.0) {
    return {
      severity: 'CRITICAL',
      color: 'var(--color-feedback-error-primary)',
    };
  }
  if (numScore >= 7.0) {
    return {
      severity: 'HIGH',
      color: 'var(--color-feedback-warning-primary)',
    };
  }
  if (numScore >= 4.0) {
    return {
      severity: 'MEDIUM',
      color: 'var(--color-feedback-info-primary)',
    };
  }
  if (numScore > 0.0) {
    return {
      severity: 'LOW',
      color: 'var(--color-feedback-success-primary)',
    };
  }
  return {
    severity: 'NONE',
    color: '#607d8b',
  };
};

const HEX_COLOR = /^#([0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;

export const colorOrFallback = (color: string | null | undefined, fallback: string): string =>
  color?.trim().match(HEX_COLOR)?.[0] ?? fallback;
