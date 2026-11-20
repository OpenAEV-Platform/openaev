import { type ChipSeverity } from '@filigran/design-system';
import { type Theme } from '@mui/material/styles';

import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';

export type PayloadStatus = 'VERIFIED' | 'UNVERIFIED' | 'DEPRECATED' | 'REVOKED';

// Theme-aware so cards / overview / quick filters render the same color in
// both dark and light modes. `theme.palette.warning.main` differs across
// themes (e.g. dark `#ffa726` vs light `#ed6c02`), so we defer to the theme
// instead of hard-coding hex values.
export const getStatusColor = (theme: Theme, status: PayloadStatus | string | undefined | null): string => {
  switch (status) {
    case 'VERIFIED':
      return theme.palette.success.main;
    case 'UNVERIFIED':
      return theme.palette.warning.main;
    case 'DEPRECATED':
      return theme.palette.text.disabled;
    case 'REVOKED':
      return theme.palette.error.main;
    default:
      return theme.palette.text.disabled;
  }
};

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
