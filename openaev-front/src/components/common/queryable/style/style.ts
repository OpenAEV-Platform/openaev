import { useTheme } from '@mui/material/styles';
import { type CSSProperties } from 'react';

import { FDS } from '../../../fds-tokens.generated';

const useBodyItemsStyles: () => {
  bodyItems: CSSProperties;
  bodyItem: CSSProperties;
} = () => {
  const theme = useTheme();

  return ({
    bodyItems: {
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'nowrap',
      maxWidth: '100%',
    },
    bodyItem: {
      minHeight: 20,
      fontSize: FDS.scalars['--text-3'],
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      paddingRight: theme.spacing(1),
    },
  });
};

export default useBodyItemsStyles;
