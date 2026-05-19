import { CancelOutlined, PendingOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { type JSX } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type BasePayload } from '../../../utils/api-types';

interface Props { status?: BasePayload['payload_status'] }

const PayloadStatusComponent = ({ status }: Props) => {
  const { t } = useFormatter();

  const withTooltip = (icon: JSX.Element, tooltip: string) => {
    return (
      <Tooltip title={tooltip}>
        <span>{icon}</span>
      </Tooltip>
    );
  };

  switch (status) {
    case 'VERIFIED':
      return withTooltip(<VerifiedOutlined color="success" />, t('Verified and tested by OpenAEV'));
    case 'UNVERIFIED':
      return withTooltip(<PendingOutlined color="warning" />, t('Unverified: Not yet tested'));
    case 'DEPRECATED':
      return withTooltip(<CancelOutlined color="disabled" />, t('Deprecated: Functionality not guaranteed'));
    default:
      return <span>-</span>;
  }
};

export default PayloadStatusComponent;
