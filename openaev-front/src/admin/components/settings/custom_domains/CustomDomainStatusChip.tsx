import { Chip, type ChipSeverity } from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type CustomDomain } from '../../../../utils/api-types';

interface Props {
  status: CustomDomain['custom_domain_status'];
  style?: CSSProperties;
}

// Status-driven colour, aligned with the platform's severity palette: verified is a positive
// (green) state, pending is a neutral/in-progress (amber) state, failed is an error (red) state.
const STATUS_SEVERITY: Record<CustomDomain['custom_domain_status'], ChipSeverity> = {
  VERIFIED: 'low',
  PENDING: 'medium',
  FAILED: 'critical',
};

const STATUS_LABELS: Record<CustomDomain['custom_domain_status'], string> = {
  VERIFIED: 'Verified',
  PENDING: 'Pending verification',
  FAILED: 'Verification failed',
};

const CustomDomainStatusChip: FunctionComponent<Props> = ({ status, style }) => {
  const { t } = useFormatter();
  return (
    <Chip label={t(STATUS_LABELS[status])} severity={STATUS_SEVERITY[status]} style={style} />
  );
};

export default CustomDomainStatusChip;
