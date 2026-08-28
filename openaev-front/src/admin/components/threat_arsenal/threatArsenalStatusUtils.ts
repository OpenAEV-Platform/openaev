import { type Theme } from '@mui/material/styles';

export type PayloadStatus = 'VERIFIED' | 'UNVERIFIED' | 'DEPRECATED';

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
    default:
      return theme.palette.text.disabled;
  }
};

export const STATUS_LABEL_MAP: Record<PayloadStatus, string> = {
  VERIFIED: 'Verified',
  UNVERIFIED: 'Unverified',
  DEPRECATED: 'Deprecated',
};

export const getStatusLabel = (status: PayloadStatus | string | undefined | null): string | null => {
  if (!status) return null;
  return STATUS_LABEL_MAP[status as PayloadStatus] ?? null;
};
