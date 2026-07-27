import { Collapse, ListItemIcon, ListItemText, MenuItem, MenuList, Popover, useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../i18n';
import { type LeftMenuSubItem } from './leftmenu-model';
import StyledTooltip from './StyledTooltip';
import { type LeftMenuHelpers, type LeftMenuState } from './useLeftMenu';
import useLeftMenuStyle from './useLeftMenuStyle';

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
  const leftMenuStyle = useLeftMenuStyle();

  const { navOpen, selectedMenu, anchors } = state;
  const { handleSelectedMenuOpen, handleSelectedMenuClose } = helpers;

  const renderMenuItem = ({ label, link, exact, icon, chip }: LeftMenuSubItem) => {
    const isCurrentTab = location.pathname === link;
    const selected = exact ? isCurrentTab : location.pathname.includes(link);
    return (
      <MenuItem
        key={label}
        aria-label={t(label)}
        component={Link}
        to={link}
        selected={selected}
        dense
        sx={{
          'paddingLeft': navOpen ? '20px' : 2.5,
          'paddingRight': 2.5,
          // Sub-items carry no border/tint; selection is conveyed by the primary
          // label + full-opacity icon (aligned with OpenCTI). Neutralize the
          // app-wide MuiMenuItem selected inset.
          '&.Mui-selected': {
            boxShadow: 'none',
            backgroundColor: 'transparent',
          },
          '&:hover, &.Mui-selected:hover': { backgroundColor: theme.palette.leftBar?.hover },
          '& .MuiListItemIcon-root': {
            minWidth: 0,
            marginRight: 1,
            opacity: selected ? 1 : 0.5,
            color: selected ? theme.palette.primary.main : theme.palette.text.tertiary,
          },
          '& .MuiListItemIcon-root svg': { fontSize: 16 },
        }}
        onClick={() => {
          if (!navOpen) handleSelectedMenuClose();
        }}
      >
        {icon && (
          <ListItemIcon style={{ ...leftMenuStyle.listItemIcon }}>
            {icon()}
          </ListItemIcon>
        )}
        <ListItemText
          primary={t(label)}
          slotProps={{
            primary: {
              fontWeight: theme.typography.h4.fontWeight,
              fontSize: theme.typography.h4.fontSize,
              color: selected ? theme.palette.primary.main : (theme.palette.leftBar?.text ?? 'inherit'),
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            },
          }}
        />
        {chip}
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
