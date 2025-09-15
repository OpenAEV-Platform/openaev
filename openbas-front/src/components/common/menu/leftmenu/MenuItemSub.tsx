import { Collapse, ListItemIcon, ListItemText, MenuItem, MenuList, Popover, useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../i18n';
import { type LeftMenuSubItem } from './leftmenu-model';
import StyledTooltip from './StyledTooltip';
import { type LeftMenuHelpers, type LeftMenuState } from './useLeftMenu';

interface Props {
  menu: string;
  subItems: LeftMenuSubItem[] | undefined;
  state: LeftMenuState;
  helpers: LeftMenuHelpers;
}

const MenuItemSub: FunctionComponent<Props> = ({
  menu,
  subItems = [],
  state,
  helpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const location = useLocation();
  const theme = useTheme();

  const { navOpen, selectedMenu, anchors } = state;
  const { handleSelectedMenuOpen, handleSelectedMenuClose } = helpers;

  const renderMenuItem = ({ label, link, exact, icon }: LeftMenuSubItem) => {
    const isCurrentTab = location.pathname === link;
    return (
      <MenuItem
        key={label}
        aria-label={t(label)}
        component={Link}
        to={link}
        selected={exact ? isCurrentTab : location.pathname.includes(link)}
        dense
        sx={{ paddingLeft: navOpen ? '20px' : undefined }}
        onClick={!navOpen ? handleSelectedMenuClose : undefined}
      >
        {icon && (
          <ListItemIcon style={{ minWidth: 20 }}>
            {icon()}
          </ListItemIcon>
        )}
        <ListItemText
          primary={t(label)}
          slotProps={{
            primary: {
              padding: navOpen ? '1px 0 0 10px' : '1px 0 0 15px',
              fontWeight: theme.typography.h4.fontWeight,
              fontSize: theme.typography.h4.fontSize,
            },
          }}
        />
      </MenuItem>
    );
  };

  if (navOpen) {
    return (
      <Collapse in={selectedMenu === menu} timeout="auto" unmountOnExit>
        <MenuList component="nav" disablePadding>
          {subItems.map((items) => {
            if (!items.userRight) return null;
            return (
              <StyledTooltip key={items.label} title={t(items.label)} placement="right">
                {renderMenuItem(items)}
              </StyledTooltip>
            );
          })}
        </MenuList>
      </Collapse>
    );
  }

  return (
    <Popover
      sx={{ pointerEvents: 'none' }}
      open={selectedMenu === menu}
      anchorEl={anchors[menu]?.current}
      anchorOrigin={{
        vertical: 'top',
        horizontal: 'right',
      }}
      transformOrigin={{
        vertical: 'top',
        horizontal: 'left',
      }}
      onClose={handleSelectedMenuClose}
      disableRestoreFocus
      disableScrollLock
      slotProps={{
        paper: {
          elevation: 1,
          onMouseEnter: () => handleSelectedMenuOpen(menu),
          onMouseLeave: handleSelectedMenuClose,
          sx: { pointerEvents: 'auto' },
        },
      }}
    >
      <MenuList component="nav">
        {subItems.map((entry) => {
          if (!entry.userRight) return null;
          return renderMenuItem(entry);
        })}
      </MenuList>
    </Popover>
  );
};

export default MenuItemSub;
