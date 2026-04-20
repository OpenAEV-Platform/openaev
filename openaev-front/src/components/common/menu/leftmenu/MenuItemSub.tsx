import { Collapse, ListItemIcon, ListItemText, MenuItem, MenuList, Popover, useTheme } from '@mui/material';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent } from 'react';
import { Link, useLocation } from 'react-router';

import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { useFormatter } from '../../../i18n';
import { type LeftMenuSubItem } from './leftmenu-model';
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
  const { t } = useFormatter();
  const location = useLocation();
  const theme = useTheme();
  const { iconSx, textColor } = useLeftMenuStyle();

  const { navOpen, selectedMenu, anchors } = state;
  const { handleSelectedMenuOpen, handleSelectedMenuClose } = helpers;
  const {
    isValidated: isValidatedEnterpriseEdition,
    openDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const renderMenuItem = ({ label, link, exact, icon, chip }: LeftMenuSubItem, inCollapse: boolean) => {
    const isCurrentTab = exact ? location.pathname === link : location.pathname.includes(link);
    const itemTextColor = isCurrentTab ? theme.palette.primary.main : textColor;

    const handleItemClick = (event: ReactMouseEvent<HTMLElement>) => {
      if (chip && !isValidatedEnterpriseEdition) {
        event.preventDefault();
        event.stopPropagation();
        setEEFeatureDetectedInfo(t(label));
        openDialog();
        return;
      }

      if (!inCollapse) {
        handleSelectedMenuClose();
      }
    };

    return (
      <MenuItem
        key={label}
        aria-label={t(label)}
        component={Link}
        to={link}
        selected={false}
        dense
        onClick={handleItemClick}
        sx={{
          'px': 2.5,
          'py': 1,
          '&:hover': { backgroundColor: theme.palette.leftBar.hover },
        }}
      >
        {icon && (
          <ListItemIcon sx={{
            ...iconSx,
            opacity: isCurrentTab ? 1 : 0.5,
          }}
          >
            {icon()}
          </ListItemIcon>
        )}
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            width: '100%',
          }}
        >
          <ListItemText
            primary={t(label)}
            sx={{ pt: 0.1 }}
            slotProps={{
              primary: {
                fontSize: '12px',
                color: itemTextColor,
              },
            }}
          />
          {chip}
        </span>
      </MenuItem>
    );
  };

  if (navOpen) {
    return (
      <Collapse in={selectedMenu === menu} timeout="auto" unmountOnExit>
        <MenuList
          component="nav"
          disablePadding
          sx={{ backgroundColor: theme.palette.designSystem.background.main }}
        >
          {subItems.map((item) => {
            if (!item.userRight) return null;
            return renderMenuItem(item, true);
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
      elevation={0}
      slotProps={{
        paper: {
          onMouseEnter: () => handleSelectedMenuOpen(menu),
          onMouseLeave: handleSelectedMenuClose,
          sx: {
            pointerEvents: 'auto',
            width: 180,
            backgroundColor: theme.palette.leftBar.popoverItem,
          },
        },
      }}
    >
      <MenuList component="nav" disablePadding>
        {subItems.map((entry) => {
          if (!entry.userRight) return null;
          return renderMenuItem(entry, false);
        })}
      </MenuList>
    </Popover>
  );
};

export default MenuItemSub;
