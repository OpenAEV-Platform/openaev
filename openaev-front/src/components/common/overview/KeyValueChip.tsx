import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

interface Props {
  label: string;
  value: string;
}

/**
 * Shared compact "LABEL value" chip for overview layouts.
 * Extracted from ThreatArsenalActionOverview.
 */
const KeyValueChip: FunctionComponent<Props> = ({ label, value }) => {
  const theme = useTheme();
  return (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'baseline',
        gap: 0.5,
        paddingBlock: 0.25,
        paddingInline: 1,
        borderRadius: 1,
        border: `1px solid ${theme.palette.divider}`,
        backgroundColor: alpha(theme.palette.background.paper, 0.4),
      }}
    >
      <Typography
        variant="caption"
        sx={{
          color: 'text.secondary',
          fontSize: 11,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        {label}
      </Typography>
      <Typography sx={{
        fontSize: 12,
        fontWeight: 600,
      }}
      >
        {value}
      </Typography>
    </Box>
  );
};

export default KeyValueChip;
