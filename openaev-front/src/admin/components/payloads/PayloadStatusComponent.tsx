import { CancelOutlined, PendingOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { type JSX } from 'react';

import { type BasePayload } from '../../../utils/api-types';

interface Props { status?: BasePayload['payload_status'] }

const STATUS_TOOLTIPS: Record<NonNullable<BasePayload['payload_status']>, string> = {
  VERIFIED: 'Verified and tested by OpenAEV',
  UNVERIFIED: 'Unverified: Not yet tested',
  DEPRECATED: 'Deprecated: Functionality not guaranteed',
};

const PayloadStatusComponent = ({ status }: Props) => {
  const withTooltip = (icon: JSX.Element, tooltip: string) => {
    return (
      <Tooltip title={tooltip}>
        <span>{icon}</span>
      </Tooltip>
    );
  };

  switch (status) {
    case 'VERIFIED':
      return withTooltip(<VerifiedOutlined color="success" />, STATUS_TOOLTIPS.VERIFIED);
    case 'UNVERIFIED':
      return withTooltip(<PendingOutlined color="warning" />, STATUS_TOOLTIPS.UNVERIFIED);
    case 'DEPRECATED':
      return withTooltip(<CancelOutlined color="disabled" />, STATUS_TOOLTIPS.DEPRECATED);
    default:
      return <span>-</span>;
  }
};

export default PayloadStatusComponent;
