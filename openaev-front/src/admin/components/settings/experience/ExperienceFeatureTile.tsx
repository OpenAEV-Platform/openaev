import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import type React from 'react';
import type { ReactNode } from 'react';

interface ExperienceFeatureTileProps {
  icon: ReactNode;
  label: string;
  accent?: string;
}

/**
 * Icon + label tile used in the Filigran Experience cards to showcase
 * capabilities (Enterprise Edition features, XTM Hub content). Mirrors the
 * HeroStat look: a tinted rounded icon box next to the label.
 */
const ExperienceFeatureTile: React.FC<ExperienceFeatureTileProps> = ({ icon, label, accent }) => {
  const theme = useTheme();
  const color = accent ?? theme.palette.primary.main;

  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1,
      padding: 1,
      borderRadius: 1,
      border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
    }}
    >
      <Box sx={{
        'display': 'flex',
        'alignItems': 'center',
        'justifyContent': 'center',
        'width': 30,
        'height': 30,
        'borderRadius': 1,
        'flexShrink': 0,
        'color': color,
        'background': alpha(color, 0.1),
        'boxShadow': `inset 0 0 12px ${alpha(color, 0.13)}`,
        '& svg': { fontSize: 16 },
      }}
      >
        {icon}
      </Box>
      <Typography sx={{
        fontSize: 12,
        fontWeight: 500,
        lineHeight: 1.3,
        color: 'text.primary',
      }}
      >
        {label}
      </Typography>
    </Box>
  );
};

export default ExperienceFeatureTile;
