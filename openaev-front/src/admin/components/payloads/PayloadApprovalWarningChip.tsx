import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import { useFormatter } from '../../../components/i18n';
import { type PayloadSimple } from '../../../utils/api-types';
import { approvalStatusSeverity } from '../threat_arsenal/approval/approvalStatusUtils';
import { payloadApprovalDisplay } from './payloadApprovalDisplay';

interface Props { approvalStatus?: PayloadSimple['payload_approval_status'] }

/**
 * Compact inline chip shown next to injects whose payload is not approved (pending or rejected),
 * for instance after its content was edited. Renders nothing for an approved payload or an inject
 * without payload, so callers can pass the status unconditionally.
 */
const PayloadApprovalWarningChip = ({ approvalStatus }: Props) => {
  const { t } = useFormatter();

  const display = payloadApprovalDisplay(approvalStatus);
  if (!display || !approvalStatus) {
    return null;
  }
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Chip
          label={t(display.label)}
          severity={approvalStatusSeverity(approvalStatus)}
          style={{ flexShrink: 0 }}
        />
      </TooltipTrigger>
      <TooltipContent>{t(display.tooltip)}</TooltipContent>
    </Tooltip>
  );
};

export default PayloadApprovalWarningChip;
