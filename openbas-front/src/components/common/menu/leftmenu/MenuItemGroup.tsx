import { ExpandLessOutlined, ExpandMoreOutlined } from '@mui/icons-material';
import { ListItemIcon, ListItemText, MenuItem } from '@mui/material';
import { type FunctionComponent } from 'react';
import { useLocation } from 'react-router';

import useDimensions from '../../../../utils/hooks/useDimensions';
import { useFormatter } from '../../../i18n';
import { type LeftMenuItemWithHref } from './leftmenu-model';
import SubMenu from './MenuItemSub';
import { type LeftMenuHelpers, type LeftMenuState } from './useLeftMenu';

interface Props {
  item: LeftMenuItemWithHref;
  state: LeftMenuState;
  helpers: LeftMenuHelpers;
}

const MenuItemGroup: FunctionComponent<Props> = ({ item, state, helpers }) => {
  // Standard hooks
  const { t } = useFormatter();
  const location = useLocation();
  const { dimension } = useDimensions();
  const isMobile = dimension.width < 768;

  const { navOpen, selectedMenu, anchors } = state;
  const { handleSelectedMenuOpen, handleSelectedMenuClose, handleSelectedMenuToggle, handleGoToPage } = helpers;

  const isCurrentTab = location.pathname === item.path;

  return (
    <>
      <MenuItem
        ref={anchors[item.href]}
        aria-haspopup="menu"
        aria-expanded={selectedMenu === item.href}
        aria-label={t(item.label)}
        selected={isCurrentTab}
        dense
        sx={{
          paddingRight: '2px',
          height: 35,
        }}
        onClick={() =>
          isMobile || navOpen
            ? handleSelectedMenuToggle(item.href)
            : handleGoToPage(item.path)}
        onMouseEnter={() => !navOpen && handleSelectedMenuOpen(item.href)}
        onMouseLeave={() => !navOpen && handleSelectedMenuClose()}
      >
        <ListItemIcon style={{ minWidth: 20 }}>{item.icon()}</ListItemIcon>
        {navOpen && (
          <>
            <ListItemText
              primary={t(item.label)}
              slotProps={{
                primary: {
                  padding: '1px 0 0 15px',
                  fontWeight: 500,
                  fontSize: 14,
                },
              }}
            />
            {selectedMenu === item.href ? (
              <ExpandLessOutlined />
            ) : (
              <ExpandMoreOutlined />
            )}
          </>
        )}
      </MenuItem>
      <SubMenu
        menu={item.href}
        subItems={item.subItems}
        state={state}
        helpers={helpers}
      />
    </>
  );
};

export default MenuItemGroup;
