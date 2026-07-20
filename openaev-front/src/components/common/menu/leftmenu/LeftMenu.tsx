import { Divider, Drawer, MenuList } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, type ReactNode } from 'react';

import { computeBannerSettings } from '../../../../public/components/systembanners/utils';
import useAuth from '../../../../utils/hooks/useAuth';
import { hasHref, type LeftMenuEntries } from './leftmenu-model';
import MenuItemGroup from './MenuItemGroup';
import MenuItemLogo from './MenuItemLogo';
import MenuItemSingle from './MenuItemSingle';
import MenuItemToggle from './MenuItemToggle';
import useLeftMenu from './useLeftMenu';

const LeftMenu: FunctionComponent<{
  entries: LeftMenuEntries[];
  bottomEntries: LeftMenuEntries[];
  headerElement?: (navOpen: boolean) => ReactNode;
  /** Logo header rendered at the very top of the drawer (OpenCTI-style: the
      logo + product switcher live in the drawer, not in the top bar). */
  logoHeader?: (navOpen: boolean) => ReactNode;
}> = ({ entries = [], bottomEntries = [], headerElement, logoHeader }) => {
  // Standard hooks
  const theme = useTheme();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const isWhitemarkEnable = settings.platform_whitemark === 'true'
    && settings.platform_license?.license_is_validated === true;
  const { state, helpers } = useLeftMenu(entries);

  const getWidth = () => {
    return state.navOpen ? 180 : 55;
  };

  // OpenCTI-aligned drawer background: a 100deg gradient from the app background
  // to the paper tone (falls back gracefully when no gradient tokens are set).
  const gradientStart = theme.palette.background.gradient?.start ?? theme.palette.background.default;
  const gradientEnd = theme.palette.background.gradient?.end ?? theme.palette.background.paper;
  const drawerBackground = `linear-gradient(100deg, ${gradientStart} 0%, ${gradientEnd} 100%)`;
  // Subtle section separators matching OpenCTI's LeftBar (designSystem bg2).
  const separatorSx = { border: `1px solid ${theme.palette.designSystem.background.bg2}` };

  // The header element (e.g. the tenant switcher) can render nothing (single
  // tenant): only render the header MenuList + its divider when it does, so no
  // orphan divider is left above the first menu entry.
  const headerNode = headerElement?.(state.navOpen);

  return (
    <Drawer
      variant="permanent"
      sx={{
        'width': getWidth(),
        'transition': theme.transitions.create('width', {
          easing: theme.transitions.easing.easeInOut,
          duration: theme.transitions.duration.enteringScreen,
        }),
        '& .MuiDrawer-paper': {
          width: getWidth(),
          minHeight: '100vh',
          overflowX: 'hidden',
          background: drawerBackground,
          borderRight: '1px solid transparent',
        },
      }}
    >
      <div style={{ marginTop: bannerHeightNumber }}>
        {logoHeader?.(state.navOpen)}
        {headerNode && (
          <>
            <MenuList component="nav">
              {headerNode}
            </MenuList>
            <Divider sx={separatorSx} />
          </>
        )}
        {entries.filter(entry => entry.userRight).map((entry, idxList) => {
          return (
            <Fragment key={idxList}>
              {entry.items.some(item => item.userRight) && idxList !== 0 && <Divider sx={separatorSx} />}
              {entry.items.filter(entry => entry.userRight).length > 0
                && (
                  <MenuList component="nav">
                    {entry.items.filter(entry => entry.userRight).map((item) => {
                      if (hasHref(item)) {
                        return (
                          <MenuItemGroup
                            key={item.label}
                            item={item}
                            state={state}
                            helpers={helpers}
                          />
                        );
                      }
                      return (
                        <MenuItemSingle key={item.label} item={item} navOpen={state.navOpen} />
                      );
                    })}
                  </MenuList>
                )}
            </Fragment>
          );
        })}
      </div>
      <MenuList component="nav" style={{ marginTop: 'auto' }}>
        {bottomEntries.filter(entry => entry.userRight).map((entry) => {
          return (
            entry.items.filter(entry => entry.userRight).map((item) => {
              return (
                <MenuItemSingle key={item.label} item={item} navOpen={state.navOpen} />
              );
            })
          );
        })}
        <MenuItemToggle
          navOpen={state.navOpen}
          onClick={helpers.handleToggleDrawer}
        />
        {!isWhitemarkEnable && (
          <MenuItemLogo
            navOpen={state.navOpen}
            onClick={() => window.open('https://filigran.io/', '_blank')}
          />
        )}
      </MenuList>
    </Drawer>
  );
};

export default LeftMenu;
