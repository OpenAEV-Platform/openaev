import { Chip } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type CustomDomain } from '../../../../utils/api-types';

interface Props {
  status: CustomDomain['custom_domain_status'];
  style?: CSSProperties;
}

// Status-driven colour, aligned with the platform's severity palette: verified is a positive
// (green) state, pending is a neutral/in-progress (amber) state, failed is an error (red) state.
const STATUS_COLORS: Record<CustomDomain['custom_domain_status'], {
  color: string;
  background: string;
}> = {
  VERIFIED: {
    color: '#4caf50',
    background: 'rgba(76, 175, 80, 0.12)',
  },
  PENDING: {
    color: '#ff9800',
    background: 'rgba(255, 152, 0, 0.12)',
  },
  FAILED: {
    color: '#f44336',
    background: 'rgba(244, 67, 54, 0.12)',
  },
};

const STATUS_LABELS: Record<CustomDomain['custom_domain_status'], string> = {
  VERIFIED: 'Verified',
  PENDING: 'Pending verification',
  FAILED: 'Verification failed',
};

const CustomDomainStatusChip: FunctionComponent<Props> = ({ status, style }) => {
  const { t } = useFormatter();
  const palette = STATUS_COLORS[status];

  return (
    <Chip
      size="small"
      variant="outlined"
      label={t(STATUS_LABELS[status])}
      style={{
        color: palette.color,
        borderColor: palette.color,
        backgroundColor: palette.background,
        fontWeight: 600,
        borderRadius: 4,
        height: 24,
        ...style,
      }}
    />
  );
};

export default CustomDomainStatusChip;
