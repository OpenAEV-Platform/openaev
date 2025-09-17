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

  const isCurrentTab = location.pathname === item.path;
  return (
    <StyledTooltip title={!navOpen && t(item.label)} placement="right">
      <MenuItem
        aria-label={t(item.label)}
        component={Link}
        to={item.path}
        selected={isCurrentTab}
        dense
        sx={{
          paddingRight: '2px',
          height: 35,
        }}
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
