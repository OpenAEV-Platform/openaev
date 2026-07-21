import { Box, Typography } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../i18n';

interface Props {
  label: string;
  children: ReactNode;
}

/**
 * Shared labeled field (overline label + value slot) for overview layouts.
 * Extracted from ThreatArsenalActionOverview.
 */
const Field: FunctionComponent<Props> = ({ label, children }) => {
  const { t } = useFormatter();
  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 0.5,
      minWidth: 0,
    }}
    >
      <Typography
        variant="overline"
        sx={{
          color: 'text.secondary',
          fontSize: 10.5,
          letterSpacing: '0.08em',
          lineHeight: 1.2,
        }}
      >
        {t(label)}
      </Typography>
      <Box sx={{ minHeight: 24 }}>
        {children}
      </Box>
    </Box>
  );
};

export default Field;
