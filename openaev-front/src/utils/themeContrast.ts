import { FDS } from '../components/fds-tokens.generated';
import { type FdsThemeMode } from './hooks/useFdsThemeScope';

/** WCAG AA for normal text. A theme below it is warned about, never rejected. */
export const CONTRAST_FLOOR = 4.5;

const luminance = (hex: string): number | null => {
  const m = /^#?([\da-f]{2})([\da-f]{2})([\da-f]{2})$/i.exec(hex.trim());
  if (!m) return null;
  const [r, g, b] = m.slice(1)
    .map(c => parseInt(c, 16) / 255)
    .map(v => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

export const contrastRatio = (a: string, b: string): number | null => {
  const la = luminance(a);
  const lb = luminance(b);
  if (la === null || lb === null) return null;
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

/** An empty field still renders, so a pair is judged on what is actually painted. */
const painted = (value: string | undefined, token: string, mode: FdsThemeMode): string =>
  value || (FDS.colors[mode] as Record<string, string>)[token] || '';

export interface ThemeContrastInput {
  background?: string;
  paper?: string;
  primary?: string;
  text?: string;
}

/** The pairs a customer can break, each reported once, on the field that owns it. */
export const themeContrastWarnings = (
  values: ThemeContrastInput,
  mode: FdsThemeMode,
): Partial<Record<keyof ThemeContrastInput, number>> => {
  const background = painted(values.background, '--bg-elevation-default-layer-0', mode);
  const paper = painted(values.paper, '--bg-elevation-default-layer-1', mode);
  const primary = painted(values.primary, '--color-filigran-brand-primary', mode);
  const text = painted(values.text, '--text-default-primary', mode);

  const worst = (...pairs: (number | null)[]) => {
    const rated = pairs.filter((r): r is number => r !== null);
    return rated.length ? Math.min(...rated) : null;
  };

  const out: Partial<Record<keyof ThemeContrastInput, number>> = {};
  const brand = worst(contrastRatio(primary, background), contrastRatio(primary, paper));
  if (brand !== null && brand < CONTRAST_FLOOR) out.primary = brand;
  const onPaper = contrastRatio(text, paper);
  if (onPaper !== null && onPaper < CONTRAST_FLOOR) out.text = onPaper;
  const onBackground = contrastRatio(text, background);
  if (onBackground !== null && onBackground < CONTRAST_FLOOR) out.background = onBackground;
  return out;
};
