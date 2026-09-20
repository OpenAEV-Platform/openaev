import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { getStatusIconComponent } from '../../../../../utils/statusIcons';
import { statusSeverity } from '../../../../../utils/statusUtils';

interface Props {
  /** Backend status key (agent trace status OR inject-level status). */
  status?: string;
  /** Translated, display-ready label (status is never conveyed by colour alone). */
  label: string;
  /** Optional explanatory tooltip, already translated. */
  tooltip?: string;
}

// Compact, severity-toned "did it run" chip for network/inject-level executions. It is styled to be
// pixel-identical to the agent-based TraceStatusChip beside it (same height, tint language, uppercase
// label + status icon) so the Executions column reads as one aligned table. It replaces the old 150px
// solid-fill ItemStatus, whose fixed width, float and different tint made the status column ragged and
// off design-system in the attack-path asset overview.
const ExecutionRanChip: FunctionComponent<Props> = ({ status, label, tooltip }) => {
  const theme = useTheme();
  const StatusIcon = getStatusIconComponent(status);

  const chip = (
    <Chip
      label={label}
      startIcon={<StatusIcon sx={{ fontSize: theme.typography.caption.fontSize }} />}
      severity={statusSeverity(status)}
      style={{ maxWidth: '100%' }}
    />
  );

  return tooltip
    ? (
        <Tooltip>
          <TooltipTrigger asChild>{chip}</TooltipTrigger>
          <TooltipContent>{tooltip}</TooltipContent>
        </Tooltip>
      )
    : chip;
};

export default ExecutionRanChip;
