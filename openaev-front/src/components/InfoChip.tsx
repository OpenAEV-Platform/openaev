import { Chip, type ChipSeverity } from '@filigran/design-system';
import type React from 'react';

export type InfoChipTone = 'green' | 'red' | 'accent';

interface InfoChipProps {
  label: string;
  tone: InfoChipTone;
}

/**
 * Solid, auto-width chip used to display a key/value pair (e.g. settings info rows, feature tags).
 * Unlike ItemBoolean (tinted background, colored text, fixed-width, uppercase), InfoChip renders
 * a filled background with bold text, sized to its content. Text color adapts to the theme mode
 * (black in light mode, white in dark mode) for contrast against the background.
 */
const TONE_SEVERITY: Record<InfoChipTone, ChipSeverity> = {
  green: 'low',
  red: 'critical',
  accent: 'neutral',
};

const InfoChip: React.FC<InfoChipProps> = ({ label, tone }) => (
  <Chip label={label} severity={TONE_SEVERITY[tone]} />
);

export default InfoChip;
