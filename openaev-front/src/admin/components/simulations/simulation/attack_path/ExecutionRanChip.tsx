import { alpha, Chip, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { getStatusIconComponent } from '../../../../../utils/statusIcons';
import { getStatusColor } from '../../../../../utils/statusUtils';

interface Props {
  /** Backend status key (agent trace status OR inject-level status). */
  status?: string;
  /** Translated, display-ready label (status is never conveyed by colour alone). */
  label: string;
  /** Optional explanatory tooltip, already translated. */
  tooltip?: string;
}

// Compact, alpha-tinted "did it run" chip for network/inject-level executions. It is styled to be
// pixel-identical to the agent-based TraceStatusChip beside it (same height, tint language, uppercase
// label + status icon) so the Executions column reads as one aligned table. It replaces the old 150px
// solid-fill ItemStatus, whose fixed width, float and different tint made the status column ragged and
// off design-system in the attack-path asset overview.
const ExecutionRanChip: FunctionComponent<Props> = ({ status, label, tooltip }) => {
  const theme = useTheme();
  const color = getStatusColor(theme, status);
  const StatusIcon = getStatusIconComponent(status);

  const chip = (
    <Chip
      size="medium"
      label={label}
      icon={<StatusIcon sx={{ fontSize: theme.typography.caption.fontSize }} />}
      sx={{
        'maxWidth': '100%',
        'backgroundColor': alpha(color, 0.08),
        'color': color,
        'fontSize': theme.typography.caption.fontSize,
        'fontWeight': theme.typography.fontWeightBold,
        'textTransform': 'uppercase',
        'borderRadius': Number(theme.shape.borderRadius) / 2,
        'height': theme.spacing(3),
        '& .MuiChip-icon': { color: 'inherit' },
      }}
    />
  );

  return tooltip ? <Tooltip title={tooltip}>{chip}</Tooltip> : chip;
};

export default ExecutionRanChip;
