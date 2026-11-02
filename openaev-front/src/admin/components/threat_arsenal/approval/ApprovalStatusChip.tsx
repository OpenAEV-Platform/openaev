import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ApprovalStatus, approvalStatusLabel, approvalStatusSeverity } from './approvalStatusUtils';

interface Props { status?: ApprovalStatus | null }

const ApprovalStatusChip: FunctionComponent<Props> = ({ status }) => {
  const { t } = useFormatter();
  if (!status) {
    return <span>-</span>;
  }
  return (
    <Chip
      label={t(approvalStatusLabel(status))}
      severity={approvalStatusSeverity(status)}
      style={{ maxWidth: '100%' }}
    />
  );
};

export default ApprovalStatusChip;
