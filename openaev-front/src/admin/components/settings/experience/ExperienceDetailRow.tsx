import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import type React from 'react';
import type { ReactNode } from 'react';

interface ExperienceDetailRowProps {
  label: string;
  divider?: boolean;
  children: ReactNode;
}

/**
 * Label / value row used in the Filigran Experience cards to display license and
 * connection details, with a subtle theme-aware separator.
 */
const ExperienceDetailRow: React.FC<ExperienceDetailRowProps> = ({ label, divider = true, children }) => {
  const theme = useTheme();

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: theme.spacing(2),
        padding: theme.spacing(1.25, 0),
        borderBottom: divider ? `1px solid ${alpha(theme.palette.text.primary, 0.08)}` : 'none',
      }}
    >
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      {children}
    </div>
  );
};

export default ExperienceDetailRow;
