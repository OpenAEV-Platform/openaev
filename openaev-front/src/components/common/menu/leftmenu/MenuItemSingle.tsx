import { ListItemIcon, ListItemText, MenuItem, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../i18n';
import { type LeftMenuItem } from './leftmenu-model';
import useLeftMenuStyle from './useLeftMenuStyle';

interface Props {
  navOpen: boolean;
  item: LeftMenuItem;
}

const MenuItemSingle: FunctionComponent<Props> = ({ navOpen, item }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const { iconSx, iconSelectedSx, getMenuItemSx, textColor } = useLeftMenuStyle();

  const isCurrentTab = location.pathname === item.path;

  return (
    <Tooltip title={!navOpen ? t(item.label) : ''} placement="right">
      <MenuItem
        aria-label={t(item.label)}
        component={Link}
        to={item.path}
        selected={false}
        dense
        sx={getMenuItemSx(isCurrentTab)}
      >
        <ListItemIcon sx={isCurrentTab ? iconSelectedSx : iconSx}>
          {item.icon()}
        </ListItemIcon>
        {navOpen && (
          <ListItemText
            primary={t(item.label)}
            sx={{ pt: 0.1 }}
            slotProps={{
              primary: {
                sx: {
                  fontSize: '14px',
                  color: textColor,
                  overflow: 'hidden',
                  whiteSpace: 'nowrap',
                  textOverflow: 'ellipsis',
                },
              },
            }}
          />
        )}
      </MenuItem>
    </Tooltip>
  );
};

export default MenuItemSingle;
