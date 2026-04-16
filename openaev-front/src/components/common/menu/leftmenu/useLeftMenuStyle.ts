import type { SxProps, Theme } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

const useLeftMenuStyle = () => {
  const theme = useTheme();

  const iconSx: SxProps<Theme> = {
    'minWidth': '0px !important',
    'mr': 1,
    'opacity': 0.5,
    'color': theme.palette.text.tertiary,
    '& svg': { fontSize: '16px !important' },
  };

  const iconSelectedSx: SxProps<Theme> = {
    ...iconSx,
    color: theme.palette.text.light,
  };

  const getMenuItemSx = (selected: boolean): SxProps<Theme> => ({
    'px': 1,
    'pr': 1,
    'py': 0,
    'height': '36px',
    'textOverflow': 'ellipsis',
    'borderLeft': selected
      ? `2px solid ${theme.palette.primary.main}`
      : '2px solid transparent',
    'backgroundColor': selected
      ? alpha(theme.palette.primary.main, 0.1)
      : 'transparent',
    'display': 'flex',
    'alignItems': 'center',
    '&:hover': {
      backgroundColor: selected
        ? theme.palette.action?.selected
        : theme.palette.leftBar.hover,
    },
  });

  const textColor = theme.palette.leftBar.text;

  return {
    iconSx,
    iconSelectedSx,
    getMenuItemSx,
    textColor,
  };
};

export default useLeftMenuStyle;
