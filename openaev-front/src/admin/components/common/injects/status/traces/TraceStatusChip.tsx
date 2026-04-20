import { HelpOutlineOutlined } from '@mui/icons-material';
import { alpha, Chip, Tooltip, type TooltipProps, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { getStatusLabel, getStatusTooltip } from '../../../../../../utils/statusLabels';
import { getStatusColor } from '../../../../../../utils/statusUtils';

// -- STATUS TOOLTIP --

interface StatusTooltipProps {
  title: string;
  description: string;
  children: TooltipProps['children'];
}

const StatusTooltip: FunctionComponent<StatusTooltipProps> = ({ title, description, children }) => {
  const theme = useTheme();

  return (
    <Tooltip
      arrow
      title={(
        <div style={{ padding: theme.spacing(0.5) }}>
          <Typography variant="subtitle2" sx={{ fontWeight: theme.typography.fontWeightBold }}>
            {title}
          </Typography>
          <Typography variant="body2">
            {description}
          </Typography>
        </div>
      )}
      slotProps={{
        tooltip: {
          sx: {
            backgroundColor: theme.palette.background.paper,
            border: `1px solid ${theme.palette.divider}`,
            maxWidth: 400,
          },
        },
        arrow: {
          sx: {
            'color': theme.palette.background.paper,
            '&::before': { border: `1px solid ${theme.palette.divider}` },
          },
        },
      }}
    >
      {children}
    </Tooltip>
  );
};

// -- TRACE STATUS CHIP --

interface TraceStatusChipProps { status: string }

const TraceStatusChip: FunctionComponent<TraceStatusChipProps> = ({ status }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const statusColor = getStatusColor(theme, status);
  const label = t(getStatusLabel(status));
  const tooltip = getStatusTooltip(status);

  const chip = (
    <Chip
      size="medium"
      label={label}
      icon={tooltip ? <HelpOutlineOutlined sx={{ fontSize: theme.typography.caption.fontSize }} /> : undefined}
      sx={{
        'backgroundColor': alpha(statusColor, 0.08),
        'color': statusColor,
        'fontSize': theme.typography.caption.fontSize,
        'fontWeight': theme.typography.fontWeightBold,
        'textTransform': 'uppercase',
        'borderRadius': Number(theme.shape.borderRadius) / 2,
        'height': theme.spacing(3),
        '& .MuiChip-icon': { color: 'inherit' },
      }}
    />
  );

  if (!tooltip) return chip;

  return (
    <StatusTooltip title={label} description={t(tooltip)}>
      {chip}
    </StatusTooltip>
  );
};

export default TraceStatusChip;
