import { type Theme } from '@mui/material/styles';

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

export const STATUS_CHIP_SEVERITY: Record<PayloadStatus, 'low' | 'medium' | 'neutral' | 'high'> = {
  VERIFIED: 'low',
  UNVERIFIED: 'medium',
  DEPRECATED: 'neutral',
  REVOKED: 'high',
};

// Verified sits next to the approval chip and reads like "approved": the hint says it is about
// whether the command works, not about a second person's sign-off.
export const STATUS_HINT_MAP: Record<PayloadStatus, string> = {
  VERIFIED: 'Verified: the command is known to work. Approval is separate: it says whether a second person signed it off.',
  UNVERIFIED: 'Unverified: nobody has confirmed the command works yet. Approval is separate.',
  DEPRECATED: 'Deprecated: kept for history, no longer maintained.',
  REVOKED: 'Revoked: does not run in this tenant until the revocation is lifted.',
};

export const getStatusHint = (status: PayloadStatus | string | undefined | null): string | null => {
  if (!status) return null;
  return STATUS_HINT_MAP[status as PayloadStatus] ?? null;
};

export const getStatusLabel = (status: PayloadStatus | string | undefined | null): string | null => {
  if (!status) return null;
  return STATUS_LABEL_MAP[status as PayloadStatus] ?? null;
};
