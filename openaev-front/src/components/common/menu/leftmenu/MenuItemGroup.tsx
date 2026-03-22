import { ArrowDropDown, ArrowDropUp } from '@mui/icons-material';
import { ListItemIcon, ListItemText, MenuItem } from '@mui/material';
import { type FunctionComponent } from 'react';
import { useLocation } from 'react-router';

import useDimensions from '../../../../utils/hooks/useDimensions';
import { useFormatter } from '../../../i18n';
import { type LeftMenuItemWithHref } from './leftmenu-model';
import SubMenu from './MenuItemSub';
import { type LeftMenuHelpers, type LeftMenuState } from './useLeftMenu';
import useLeftMenuStyle from './useLeftMenuStyle';

interface Props {
  item: LeftMenuItemWithHref;
  state: LeftMenuState;
  helpers: LeftMenuHelpers;
}

const MenuItemGroup: FunctionComponent<Props> = ({ item, state, helpers }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const { iconSx, iconSelectedSx, getMenuItemSx, textColor } = useLeftMenuStyle();
  const { dimension } = useDimensions();
  const isMobile = dimension.width < 768;

  const { navOpen, selectedMenu, anchors } = state;
  const { handleSelectedMenuOpen, handleSelectedMenuClose, handleSelectedMenuToggle, handleGoToPage } = helpers;

  const isCurrentTab = navOpen ? location.pathname === item.path : location.pathname.startsWith(item.path);

  return (
    <>
      <MenuItem
        ref={anchors[item.href]}
        aria-haspopup="menu"
        aria-expanded={selectedMenu === item.href}
        aria-label={t(item.label)}
        selected={false}
        dense
        sx={getMenuItemSx(isCurrentTab)}
        onClick={() =>
          isMobile || navOpen
            ? handleSelectedMenuToggle(item.href)
            : handleGoToPage(item.path)}
        onMouseEnter={() => !navOpen && handleSelectedMenuOpen(item.href)}
        onMouseLeave={() => !navOpen && handleSelectedMenuClose()}
      >
        <ListItemIcon sx={isCurrentTab ? iconSelectedSx : iconSx}>
          {item.icon()}
        </ListItemIcon>
        {navOpen && (
          <>
            <ListItemText
              primary={t(item.label)}
              sx={{ pt: 0.1 }}
              slotProps={{
                primary: {
                  fontSize: '14px',
                  color: textColor,
                },
              }}
            />
            {selectedMenu === item.href
              ? <ArrowDropUp sx={{ fontSize: '20px' }} />
              : <ArrowDropDown sx={{ fontSize: '20px' }} />}
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
