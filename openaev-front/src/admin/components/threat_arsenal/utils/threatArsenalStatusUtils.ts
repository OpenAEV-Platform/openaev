import { type ChipSeverity } from '@filigran/design-system';
import { type Theme } from '@mui/material/styles';

import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

export type PayloadStatus = 'VERIFIED' | 'UNVERIFIED' | 'DEPRECATED' | 'REVOKED';

// From the theme, so both modes get their own shade.
const STATUS_COLOR: Record<PayloadStatus, (theme: Theme) => string> = {
  VERIFIED: theme => theme.palette.success.main,
  UNVERIFIED: theme => theme.palette.warning.main,
  DEPRECATED: theme => theme.palette.text.disabled,
  REVOKED: theme => theme.palette.error.main,
};

export const getStatusColor = (theme: Theme, status: PayloadStatus | string | undefined | null): string => (
  STATUS_COLOR[status as PayloadStatus] ?? STATUS_COLOR.DEPRECATED
)(theme);

export const STATUS_LABEL_MAP: Record<PayloadStatus, string> = {
  VERIFIED: 'Verified',
  UNVERIFIED: 'Unverified',
  DEPRECATED: 'Deprecated',
  REVOKED: 'Revoked',
};

export const STATUS_CHIP_SEVERITY: Record<PayloadStatus, ChipSeverity> = {
  VERIFIED: 'low',
  UNVERIFIED: 'medium',
  DEPRECATED: 'neutral',
  REVOKED: 'critical',
};

export const getStatusLabel = (status: PayloadStatus | string | undefined | null): string | null => {
  if (!status) return null;
  return STATUS_LABEL_MAP[status as PayloadStatus] ?? null;
};

/** A revoked payload shows as revoked, whatever status it has. */
export const isShownRevoked = (action: Pick<ThreatArsenalAction, 'action_payload_revoked'>) => isFeatureEnabled('CATALOG') && !!action.action_payload_revoked;

export const getActionStatus = (
  action: Pick<ThreatArsenalAction, 'action_payload' | 'action_payload_revoked'>,
  payloadStatus = action.action_payload?.payload_status,
): PayloadStatus | undefined => (isShownRevoked(action) ? 'REVOKED' : payloadStatus);

export const getPayloadSourceLabel = (action: Pick<ThreatArsenalAction, 'action_payload_managed'>) => (action.action_payload_managed ? 'Filigran' : 'Local');
