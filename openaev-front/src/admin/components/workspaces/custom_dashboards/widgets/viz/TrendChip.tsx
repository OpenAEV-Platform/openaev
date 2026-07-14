import { TrendingDownOutlined, TrendingFlatOutlined, TrendingUpOutlined } from '@mui/icons-material';
import { Box, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../../components/i18n';

interface Props {
  /** Signed delta over the previous interval. */
  difference: number;
  /** Optional previous value, shown in the tooltip. */
  previous?: number;
}

/**
 * Compact trend pill (inspired by the XTM One diff chip): a rounded, tinted
 * badge with a trend arrow and the signed delta. Green when improving, red when
 * declining, muted when flat.
 */
const TrendChip: FunctionComponent<Props> = ({ difference, previous }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const direction = (() => {
    if (difference > 0) return 'up';
    if (difference < 0) return 'down';
    return 'flat';
  })();

  const color = (() => {
    if (direction === 'up') return theme.palette.success.main;
    if (direction === 'down') return theme.palette.error.main;
    return theme.palette.text.disabled;
  })();

  const Icon = (() => {
    if (direction === 'up') return TrendingUpOutlined;
    if (direction === 'down') return TrendingDownOutlined;
    return TrendingFlatOutlined;
  })();

  const label = `${difference > 0 ? '+' : ''}${difference}`;

  const chip = (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        height: 22,
        paddingInline: 0.75,
        borderRadius: 1,
        backgroundColor: alpha(color, 0.14),
        border: `1px solid ${alpha(color, 0.28)}`,
        color,
        fontFamily: '"Geologica", sans-serif',
        fontSize: 12,
        fontWeight: 600,
        lineHeight: 1,
      }}
    >
      <Icon sx={{ fontSize: 14 }} />
      {label}
    </Box>
  );

  if (previous === undefined) {
    return chip;
  }

  return (
    <Tooltip title={t('was previously', { previous_number: previous })}>
      {chip}
    </Tooltip>
  );
};

export default TrendChip;
