import { useTheme } from '@mui/material/styles';
import { type CSSProperties } from 'react';

const useBodyItemsStyles: () => {
  bodyItems: CSSProperties;
  bodyItem: CSSProperties;
} = () => {
  const theme = useTheme();

  return ({
    bodyItems: {
      display: 'flex',
      flexWrap: 'nowrap',
      maxWidth: '100%',
    },
    bodyItem: {
      height: 20,
      fontSize: theme.typography.body2.fontSize,
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      paddingRight: theme.spacing(1),
    },
  });
};

export default useBodyItemsStyles;
