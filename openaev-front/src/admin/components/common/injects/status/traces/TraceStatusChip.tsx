import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type TooltipProps, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { getStatusIconComponent } from '../../../../../../utils/statusIcons';
import { getAgentStatusTooltip, getStatusLabel } from '../../../../../../utils/statusLabels';
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
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="inline-flex">{children}</span>
      </TooltipTrigger>
      <TooltipContent>
        <Typography variant="subtitle2" sx={{ fontWeight: theme.typography.fontWeightBold }}>
          {title}
        </Typography>
        <Typography variant="body2">
          {description}
        </Typography>
      </TooltipContent>
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

  const tooltip = getAgentStatusTooltip(status);
  const StatusIcon = getStatusIconComponent(status);

  const chip = (
    <Chip
      label={label}
      startIcon={<StatusIcon sx={{ fontSize: theme.typography.caption.fontSize }} />}
      color={statusColor}
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
