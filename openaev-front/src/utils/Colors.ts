import { type ChipSeverity } from '@filigran/design-system';

import { type ThemeInput } from './api-types';

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

/**
 * The color when it is a valid hex value, else undefined. Inline styles must never get an invalid
 * value: the browser rejects it and keeps the element's previous color, which a reused DOM node
 * (e.g. a filtered option list) then shows for the wrong item.
 */
export const validHexColor = (color: string | null | undefined): string | undefined =>
  color?.trim().match(HEX_COLOR)?.[0];

export const colorOrFallback = (color: string | null | undefined, fallback: string): string =>
  validHexColor(color) ?? fallback;

/** The only format the theme and marking forms accept (and the API enforces): #RRGGBB. */
export const FORM_HEX_COLOR_REGEX = /^#[0-9a-fA-F]{6}$/;

const THEME_COLOR_FIELDS = [
  'background_color',
  'paper_color',
  'navigation_color',
  'primary_color',
  'secondary_color',
  'accent_color',
  'text_color',
  'login_aside_color',
  'login_aside_gradient_start',
  'login_aside_gradient_end',
] as const;

/**
 * The theme with every colour field that is not a valid hex dropped (undefined, so the library
 * default applies). A stored value that is not a colour would otherwise make MUI's
 * alpha()/lighten()/createTheme throw and take the whole app down, settings page included.
 */
export const sanitizeThemeColors = (theme: ThemeInput | null | undefined): ThemeInput | undefined => {
  if (!theme) {
    return undefined;
  }
  const sanitized = { ...theme };
  THEME_COLOR_FIELDS.forEach((field) => {
    sanitized[field] = validHexColor(theme[field]);
  });
  return sanitized;
};
