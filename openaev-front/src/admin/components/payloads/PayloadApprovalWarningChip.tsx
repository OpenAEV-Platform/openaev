import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GppMaybeOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import { type PayloadSimple } from '../../../utils/api-types';
import { statusSeverity } from '../../../utils/statusUtils';
import { payloadApprovalDisplay } from './payloadApprovalDisplay';

interface Props {
  approvalStatus?: PayloadSimple['payload_approval_status'];
  /** The payload is approved and a new version of it waits for approval (informative, never blocks). */
  pendingVersion?: boolean;
  /** Name of the action, appended to the tooltip title. */
  payloadName?: string;
  /**
   * `chip`: compact status chip ("Pending" / "Rejected"), same component and size as the other
   * status chips. `icon`: icon only, to sit next to another status without taking its room.
   */
  variant?: 'chip' | 'icon';
}

/**
 * Approval state of the payload of an inject that is not approved (pending or rejected), or approved
 * with a new version pending approval, with the full text in a tooltip. Renders nothing for an
 * approved payload with nothing pending or an inject without payload, so callers can pass the status
 * unconditionally.
 */
const PayloadApprovalWarningChip = ({ approvalStatus, pendingVersion, payloadName, variant = 'chip' }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const display = payloadApprovalDisplay(approvalStatus, pendingVersion);
  if (!display) {
    return null;
  }
  const title = payloadName ? `${t(display.label)} – ${payloadName}` : t(display.label);
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {variant === 'icon'
          ? (
              <span
                role="img"
                aria-label={title}
                style={{
                  display: 'inline-flex',
                  flexShrink: 0,
                  color: display.status === 'PAYLOAD_REJECTED' ? theme.palette.error.main : theme.palette.warning.main,
                }}
              >
                <GppMaybeOutlined fontSize="small" />
              </span>
            )
          : (
              <Chip
                label={t(display.shortLabel)}
                severity={statusSeverity(display.status)}
                aria-label={title}
                style={{
                  flexShrink: 0,
                  maxWidth: '100%',
                }}
              />
            )}
      </TooltipTrigger>
      <TooltipContent>
        <div>{title}</div>
        <div>{t(display.tooltip)}</div>
      </TooltipContent>
    </Tooltip>
  );
};

export default PayloadApprovalWarningChip;
