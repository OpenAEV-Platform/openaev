import { ListItemIcon, MenuItem, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import logoFiligranDark from '../../../../static/images/logo_filigran_dark.png';
import logoFiligranLight from '../../../../static/images/logo_filigran_light.png';
import logoFiligranTextDark from '../../../../static/images/logo_filigran_text_dark.png';
import logoFiligranTextLight from '../../../../static/images/logo_filigran_text_light.png';
import { fileUri } from '../../../../utils/Environment';
import useLeftMenuStyle from './useLeftMenuStyle';

interface Props {
  navOpen: boolean;
  onClick: () => void;
}

const MenuItemLogo: FunctionComponent<Props> = ({
  navOpen,
  onClick,
}) => {
  // Standard hooks
  const leftMenuStyle = useLeftMenuStyle();
  const theme = useTheme();
  const { palette } = theme;
  const isDarkMode = palette.mode === 'dark';

  return (
    <MenuItem
      aria-label="Filigran logo menu item"
      dense
      onClick={onClick}
    >
      <Tooltip title="By Filigran">
        <ListItemIcon style={{ ...leftMenuStyle.listItemIcon }}>
          <img
            src={fileUri(isDarkMode ? logoFiligranDark : logoFiligranLight)}
            alt="logo"
            width={20}
          />
        </ListItemIcon>
      </Tooltip>
      {navOpen && (
        <ListItemIcon
          style={{ padding: `${theme.spacing(0.5)} 0 0 ${theme.spacing(1.3)}` }}
        >
          <img
            src={fileUri(isDarkMode ? logoFiligranTextDark : logoFiligranTextLight)}
            alt="logo"
            width={50}
          />
        </ListItemIcon>
      )}
    </MenuItem>
  );
};

export default MenuItemLogo;
