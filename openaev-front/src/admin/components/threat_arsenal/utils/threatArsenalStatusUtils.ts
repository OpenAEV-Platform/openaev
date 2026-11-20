import { type ChipSeverity } from '@filigran/design-system';
import { type Theme } from '@mui/material/styles';

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
