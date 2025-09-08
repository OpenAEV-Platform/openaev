import { Box, Divider, Drawer, MenuList, Toolbar } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, useEffect, useState } from 'react';

import eventBus, { OPEN_ONBOARDING_PROGRESS_SIDEBAR } from '../../../../admin/components/onboarding/enventBus';
import MenuItemOnboarding from '../../../../admin/components/onboarding/MenuItemOnboarding';
import { shouldDisplayWidget } from '../../../../admin/components/onboarding/onboarding-utils';
import OnboardingProgressSidebar from '../../../../admin/components/onboarding/OnboardingProgressSidebar';
import { computeBannerSettings } from '../../../../public/components/systembanners/utils';
import useAuth from '../../../../utils/hooks/useAuth';
import { hasHref, type LeftMenuEntries } from './leftmenu-model';
import MenuItemGroup from './MenuItemGroup';
import MenuItemLogo from './MenuItemLogo';
import MenuItemSingle from './MenuItemSingle';
import MenuItemToggle from './MenuItemToggle';
import useLeftMenu from './useLeftMenu';

const LeftMenu: FunctionComponent<{ entries: LeftMenuEntries[] }> = ({ entries = [] }) => {
  // Standard hooks
  const theme = useTheme();
  const { me, settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const isWhitemarkEnable = settings.platform_whitemark === 'true'
    && settings.platform_license?.license_is_validated === true;
  const { state, helpers } = useLeftMenu(entries);

  const [openSidebar, setOpenSidebar] = useState(false);

  const handleToggleSidebar = () => setOpenSidebar(!openSidebar);
  const handleCloseSidebar = () => setOpenSidebar(false);

  const getWidth = () => {
    return state.navOpen ? 180 : 55;
  };

  useEffect(() => {
    const handler = (event: Event) => {
      const customEvent = event as CustomEvent;
      if (customEvent) setOpenSidebar(true);
    };

    eventBus.addEventListener(OPEN_ONBOARDING_PROGRESS_SIDEBAR, handler);
    return () => eventBus.removeEventListener(OPEN_ONBOARDING_PROGRESS_SIDEBAR, handler);
  }, []);

  return (
    <>
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
        <div style={{ marginTop: 'auto' }}>
          <MenuList component="nav">
            {shouldDisplayWidget(me, settings) && <MenuItemOnboarding navOpen={state.navOpen} handleToggleSidebar={handleToggleSidebar} />}
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
        </div>
      </Drawer>
      {openSidebar && (
        <Box
          sx={{
            position: 'fixed',
            bottom: 0,
            left: getWidth(),
            zIndex: 1202,
          }}
        >
          <OnboardingProgressSidebar onCloseSidebar={handleCloseSidebar} />
        </Box>
      )}
    </>
  );
};

export default LeftMenu;
