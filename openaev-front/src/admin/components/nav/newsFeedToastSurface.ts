import { useTheme } from '@mui/material/styles';
import { type CSSProperties } from 'react';

import { tint } from '../../../utils/tint';

/** The translucent surface of the news feed toasts, and the card that carries one toast on it. */
export const useNewsFeedToastStyles = (): {
  surface: CSSProperties;
  card: CSSProperties;
} => {
  const theme = useTheme();
  const surface: CSSProperties = {
    backgroundColor: tint(theme.palette.primary.main, 13),
    backdropFilter: 'blur(8px)',
    border: `1px solid ${tint(theme.palette.primary.main, 27)}`,
    borderRadius: 'var(--radius-sm)',
    boxShadow: theme.shadows[4],
  };
  return {
    surface,
    card: {
      ...surface,
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(1.5),
      padding: theme.spacing(1),
      width: 450,
      maxWidth: '100%',
      boxSizing: 'border-box',
      pointerEvents: 'auto',
    },
  };
};
