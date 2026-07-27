import { Box, Chip, Tooltip, Typography } from '@mui/material';
import { FilePdfBox, LanguageHtml5 } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type ReportingGeneration } from '../../../utils/api-types';
import { computeStatusStyle } from '../../../utils/statusUtils';

/**
 * Output format rendered design-system style: a file-type icon plus the
 * uppercase label, instead of a chip (formats are facts, not statuses).
 */
export const ReportingFormatFragment: FunctionComponent<{ format?: string | null }> = ({ format }) => {
  if (!format) return <span>-</span>;
  const Icon = format.toUpperCase() === 'HTML' ? LanguageHtml5 : FilePdfBox;
  return (
    <Box sx={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 0.75,
    }}
    >
      <Icon sx={{
        fontSize: 18,
        color: 'text.secondary',
      }}
      />
      <Typography
        component="span"
        sx={{
          fontSize: 12,
          fontWeight: 500,
          textTransform: 'uppercase',
          lineHeight: 1,
        }}
      >
        {format}
      </Typography>
    </Box>
  );
};

interface StatusChipProps {
  status: ReportingGeneration['reporting_generation_status'];
  /** Extra tooltip content (e.g. the generation error); defaults to the label. */
  tooltip?: string;
}

/**
 * Compact generation status tag using the platform's plain status palette
 * (same colors as inject statuses), sized for cards and popovers. The
 * full-width list rows use the shared ItemStatus component instead.
 */
export const ReportingStatusChip: FunctionComponent<StatusChipProps> = ({ status, tooltip }) => {
  const { t } = useFormatter();
  // The generated spec marks the status optional; a generation row always has
  // one, PENDING is the neutral fallback.
  const effectiveStatus = status ?? 'PENDING';
  return (
    <Tooltip title={tooltip ?? t(effectiveStatus)}>
      <Chip
        label={t(effectiveStatus)}
        style={computeStatusStyle(effectiveStatus)}
        sx={{
          height: 20,
          fontSize: 11,
          textTransform: 'uppercase',
          borderRadius: 0.5,
          width: 90,
          flexShrink: 0,
        }}
      />
    </Tooltip>
  );
};
