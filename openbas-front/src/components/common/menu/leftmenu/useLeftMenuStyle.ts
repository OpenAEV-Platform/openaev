import { useTheme } from '@mui/material/styles';
import type { CSSProperties } from 'react';

const useLeftMenuStyle: () => {
  listItemIcon: CSSProperties;
  listItemText: CSSProperties;
} = () => {
  const theme = useTheme();

  return ({
    listItemIcon: { minWidth: 20 },
    listItemText: {
      paddingLeft: theme.spacing(1),
      fontWeight: theme.typography.h2.fontWeight,
      fontSize: 14,
    },
  });
};

export default useLeftMenuStyle;
