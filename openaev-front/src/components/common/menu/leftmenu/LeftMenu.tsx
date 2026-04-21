import { Divider, Drawer, MenuList, Toolbar } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent } from 'react';

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
}> = ({ entries = [], bottomEntries = [] }) => {
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
        },
      }}
    >
      <Toolbar />
      <div style={{ marginTop: bannerHeightNumber }}>
        {entries.filter(entry => entry.userRight).map((entry, idxList) => {
          return (
            <Fragment key={idxList}>
              {entry.items.some(item => item.userRight) && idxList !== 0 && <Divider />}
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
        {!isWhitemarkEnable && (
          <MenuItemLogo
            navOpen={state.navOpen}
            onClick={() => window.open('https://filigran.io/', '_blank')}
          />
        )}
        <MenuItemToggle
          navOpen={state.navOpen}
          onClick={helpers.handleToggleDrawer}
        />
      </MenuList>
    </Drawer>
  );
};

export default LeftMenu;
