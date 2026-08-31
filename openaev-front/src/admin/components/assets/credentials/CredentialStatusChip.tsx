import { Tooltip } from '@mui/material';

import type { CredentialOutput } from '../../../../utils/api-types';
import AssetStatus from '../AssetStatus';

interface Props {
  status?: CredentialOutput['credential_status'];
  variant?: 'list' | 'hero';
}

type InactiveStatusWithMessage = Exclude<
  NonNullable<CredentialOutput['credential_status']>,
  'ACTIVE' | 'UNSET'
>;

const INACTIVE_STATUS_MESSAGES: Record<InactiveStatusWithMessage, string> = {
  AUTH_FAILED: 'Invalid access key or secret',
  PERMISSION_DENIED: 'Authenticated but insufficient permissions',
  TIMEOUT: 'Connection timed out after 10s, provider not responding',
  NETWORK_ERROR: 'Cannot reach provider, check network connectivity or region',
  UNSUPPORTED: 'Unsupported credential type or validation method',
  FORMAT_ERROR: 'Credential format is invalid',
  UNKNOWN: 'Unknown validation error',
};
const CredentialStatusChip = ({ status, variant = 'list' }: Props) => {
  if (!status || status === 'UNSET') {
    return '-';
  }

  if (status === 'ACTIVE') {
    return <AssetStatus variant={variant} status="Active" />;
  }

  if (status in INACTIVE_STATUS_MESSAGES) {
    const statusCode = status as InactiveStatusWithMessage;
    const tooltipTitle = `${statusCode}: ${INACTIVE_STATUS_MESSAGES[statusCode]}`;

    return (
      <Tooltip title={tooltipTitle}>
        <span>
          <AssetStatus variant={variant} status="Inactive" />
        </span>
      </Tooltip>
    );
  }

  return '-';
};

export default CredentialStatusChip;
