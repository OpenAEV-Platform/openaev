import { Box } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { computeStatusStyle } from '../../../../../utils/statusUtils';

interface Props {
  label: string;
  status: string | undefined;
}

// Shared alpha-tinted status pill (same visual language as TraceStatusChip)
// so we do not restyle the shared ItemStatus component.
const StatusPill: FunctionComponent<Props> = ({ label, status }) => {
  const statusColor = computeStatusStyle(status).color;
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        paddingInline: 1,
        paddingBlock: 0.25,
        borderRadius: 1,
        backgroundColor: alpha(statusColor, 0.08),
        color: statusColor,
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: '0.04em',
        textTransform: 'uppercase',
        whiteSpace: 'nowrap',
      }}
    >
      {label}
    </Box>
  );
};

export default StatusPill;
