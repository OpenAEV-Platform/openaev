import { Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

import colorStyles from './Color';

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
const InfoChip: React.FC<InfoChipProps> = ({ label, tone }) => {
  const theme = useTheme();

  const backgroundColor = {
    green: colorStyles.green.backgroundColor,
    red: colorStyles.red.backgroundColor,
    accent: theme.palette.background.accent,
  }[tone];
  const textColor = theme.palette.mode === 'light' ? theme.palette.common.black : theme.palette.common.white;

  return (
    <Chip
      label={label}
      sx={{
        fontSize: theme.typography.body2.fontSize,
        fontWeight: theme.typography.fontWeightBold,
        borderRadius: `${theme.shape.borderRadius}px`,
        height: theme.spacing(3),
        textTransform: 'none',
        color: textColor,
        backgroundColor,
      }}
    />
  );
};

export default InfoChip;
