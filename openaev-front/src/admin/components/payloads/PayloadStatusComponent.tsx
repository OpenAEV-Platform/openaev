import { CancelOutlined, PendingOutlined, VerifiedOutlined } from '@mui/icons-material';

import { type BasePayload } from '../../../utils/api-types';

interface Props { status?: BasePayload['payload_status'] }

const PayloadStatusComponent = ({ status }: Props) => {
  switch (status) {
    case 'VERIFIED':
      return <VerifiedOutlined color="success" />;
    case 'UNVERIFIED':
      return <PendingOutlined color="warning" />;
    case 'DEPRECATED':
      return <CancelOutlined color="disabled" />;
    default:
      return <span>-</span>;
  }
};

export default PayloadStatusComponent;
