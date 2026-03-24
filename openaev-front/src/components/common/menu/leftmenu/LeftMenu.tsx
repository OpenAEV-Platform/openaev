import { Divider, Drawer, MenuList, Stack, Toolbar, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent } from 'react';

import { computeBannerSettings } from '../../../../public/components/systembanners/utils';
import logoFiligran from '../../../../static/images/logo_filigran_full.svg';
import useAuth from '../../../../utils/hooks/useAuth';
import { useFormatter } from '../../../i18n';
import { THEME_LIGHT_DEFAULT_BACKGROUND, THEME_LIGHT_DEFAULT_PAPER } from '../../../ThemeLight';
import { hasHref, type LeftMenuEntries } from './leftmenu-model';
import MenuItemGroup from './MenuItemGroup';
import MenuItemSingle from './MenuItemSingle';
import MenuItemToggle from './MenuItemToggle';
import useLeftMenu from './useLeftMenu';

const SMALL_BAR_WIDTH = 55;
const OPEN_BAR_WIDTH = 180;

const LeftMenu: FunctionComponent<{
  entries: LeftMenuEntries[];
  bottomEntries: LeftMenuEntries[];
}> = ({ entries = [], bottomEntries = [] }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const isWhitemarkEnable = settings.platform_whitemark === 'true'
    && settings.platform_license?.license_is_validated === true;
  const { state, helpers } = useLeftMenu(entries);

  const getWidth = () => {
    return state.navOpen ? OPEN_BAR_WIDTH : SMALL_BAR_WIDTH;
  };

  const isLightTheme = theme.palette.mode === 'light';
  const getBackground = () => {
    if (isLightTheme) {
      return `linear-gradient(100deg, ${THEME_LIGHT_DEFAULT_BACKGROUND} 0%, ${THEME_LIGHT_DEFAULT_PAPER} 100%)`;
    }
    const start = theme.palette.background?.gradient?.start ?? theme.palette.background?.default;
    const end = theme.palette.background?.gradient?.end ?? theme.palette.background?.secondary;
    return `linear-gradient(100deg, ${start} 0%, ${end} 100%)`;
  };

  return (
    <Drawer
      variant="permanent"
      slotProps={{
        paper: {
          sx: {
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            background: getBackground(),
            borderRight: '1px solid transparent',
          },
        },
      }}
      sx={{
        'width': getWidth(),
        'zIndex': 999,
        'top': 0,
        'height': '100vh',
        'overflow': 'hidden',
        'transition': theme.transitions.create('width', {
          easing: theme.transitions.easing.easeInOut,
          duration: theme.transitions.duration.enteringScreen,
        }),
        '& .MuiDrawer-paper': { width: getWidth() },
      }}
    >
      <Toolbar />
      <div
        aria-label="Main navigation"
        style={{
          overflow: 'auto',
          overflowX: 'hidden',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0,
          backgroundColor: 'transparent',
          marginTop: bannerHeightNumber,
        }}
      >
        {entries.filter(entry => entry.userRight).map((entry, idxList) => {
          return (
            <Fragment key={idxList}>
              {entry.items.some(item => item.userRight) && idxList !== 0 && (
                <Divider sx={{ borderColor: theme.palette.designSystem.border.border2 }} />
              )}
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
        {bottomEntries.filter(entry => entry.userRight).length > 0 && (
          <MenuList component="nav" style={{ marginTop: 'auto' }} disablePadding>
            {bottomEntries.filter(entry => entry.userRight).map((entry) => {
              return (
                entry.items.filter(entry => entry.userRight).map((item) => {
                  return (
                    <MenuItemSingle key={item.label} item={item} navOpen={state.navOpen} />
                  );
                })
              );
            })}
          </MenuList>
        )}
      </div>
      <div
        style={{
          flexShrink: 0,
          borderRight: theme.palette.mode === 'dark'
            ? '1px solid rgba(255, 255, 255, 0.12)'
            : '1px solid rgba(0, 0, 0, 0.12)',
          width: getWidth(),
        }}
      >
        <MenuList
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
        >
          <MenuItemToggle
            navOpen={state.navOpen}
            onClick={helpers.handleToggleDrawer}
          />
          {!isWhitemarkEnable && (
            <Stack
              direction="row"
              alignItems="center"
              gap={0.5}
              paddingLeft={2.5}
              marginBottom={1}
              minHeight={16}
            >
              {state.navOpen && (
                <Typography
                  component="span"
                  sx={{
                    fontFamily: 'IBM Plex Sans',
                    fontSize: '10px',
                    lineHeight: '16px',
                    opacity: 0.8,
                    color: theme.palette.text.tertiary,
                  }}
                >
                  {t('Made by')}
                </Typography>
              )}
              <img
                alt="logo"
                src={logoFiligran}
                width={state.navOpen ? 48 : 12}
                height="12"
                style={{
                  opacity: 0.8,
                  objectFit: 'cover',
                  objectPosition: 'left center',
                }}
              />
            </Stack>
          )}
        </MenuList>
      </div>
    </Drawer>
  );
};

export default LeftMenu;
