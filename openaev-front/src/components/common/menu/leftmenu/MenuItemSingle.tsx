import { ListItemIcon, ListItemText, MenuItem } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../i18n';
import { type LeftMenuItem } from './leftmenu-model';
import StyledTooltip from './StyledTooltip';
import useLeftMenuStyle from './useLeftMenuStyle';

interface Props {
  navOpen: boolean;
  item: LeftMenuItem;
}

const MenuItemSingle: FunctionComponent<Props> = ({ navOpen, item }) => {
  // Standard hooks
  const { t } = useFormatter();
  const location = useLocation();
  const leftMenuStyle = useLeftMenuStyle();

  // Highlight on the exact page and on any sub-route (e.g. /admin/integrations/deployed,
  // /admin/findings/:id). Home ('/admin') is a prefix of every route, so it stays exact-only.
  const isCurrentTab = location.pathname === item.path
    || (item.path !== '/admin' && location.pathname.startsWith(`${item.path}/`));

  return (
    <StyledTooltip title={navOpen ? false : t(item.label)} placement="right">
      <MenuItem
        aria-label={t(item.label)}
        component={Link}
        to={item.path}
        selected={isCurrentTab}
        dense
        sx={leftMenuStyle.menuItemSx}
      >
        <ListItemIcon style={{ ...leftMenuStyle.listItemIcon }}>
          {item.icon()}
        </ListItemIcon>
        {navOpen && (
          <ListItemText
            primary={t(item.label)}
            slotProps={{ primary: { sx: { ...leftMenuStyle.listItemText } } }}
          />
        )}
      </MenuItem>
    </StyledTooltip>
  );
};

export default MenuItemSingle;
