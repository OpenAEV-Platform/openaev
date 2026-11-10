import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type PayloadSimple } from '../../../utils/api-types';
import ApprovalStatusChip from './approval/ApprovalStatusChip';

interface Props {
  revoked?: boolean;
  approvalStatus?: PayloadSimple['payload_approval_status'] | null;
  /** Set where the chip stands for the whole action: a pending status then says new action or new version. */
  pendingVersion?: boolean;
}

/**
 * The status of a payload version: its approval status (only Approved can be used), or Revoked.
 * Whether Filigran verified it is a tag, not a status: see {@link PayloadVerificationTag}.
 */
const ThreatArsenalStatusChip: FunctionComponent<Props> = ({ revoked, approvalStatus, pendingVersion }) => {
  const { t } = useFormatter();
  if (revoked) {
    return <Chip label={t('Revoked')} severity="critical" />;
  }
  return <ApprovalStatusChip status={approvalStatus} pendingVersion={pendingVersion} />;
};

export default ThreatArsenalStatusChip;
