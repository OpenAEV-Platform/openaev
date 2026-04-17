import { Collapse, ListItemIcon, ListItemText, MenuItem, MenuList, Popover, useTheme } from '@mui/material';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent } from 'react';
import { Link, useLocation } from 'react-router';

import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
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
        selected={exact ? isCurrentTab : location.pathname.includes(link)}
        dense
        onClick={handleItemClick}
        sx={{ paddingLeft: navOpen ? '20px' : undefined }}
      >
        {icon && (
          <ListItemIcon style={{ ...leftMenuStyle.listItemIcon }}>
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
            slotProps={{
            primary: {
              paddingLeft: navOpen ? `${theme.spacing(1)}` : `${theme.spacing(2)}`,
              fontWeight: theme.typography.h4.fontWeight,
              fontSize: theme.typography.h4.fontSize,
              whiteSpace: 'nowrap',
                overflow: 'hidden',
              textOverflow: 'ellipsis',
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
