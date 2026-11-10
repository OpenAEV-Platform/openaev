import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { actionApprovalLabel, type ApprovalStatus, approvalStatusLabel, approvalStatusSeverity } from './approvalStatusUtils';

interface Props {
  status?: ApprovalStatus | null;
  /** Set where the chip stands for the whole action: a pending status then says new action or new version. */
  pendingVersion?: boolean;
}

const ApprovalStatusChip: FunctionComponent<Props> = ({ status, pendingVersion }) => {
  const { t } = useFormatter();
  if (!status) {
    return <span>-</span>;
  }
  return (
    <Chip
      label={t(pendingVersion === undefined ? approvalStatusLabel(status) : actionApprovalLabel(status, pendingVersion))}
      severity={approvalStatusSeverity(status)}
      style={{ maxWidth: '100%' }}
    />
  );
};

export default ApprovalStatusChip;
