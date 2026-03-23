import { AccountCircleOutlined, ArrowDropDown, ImportantDevicesOutlined, OpenInNew } from '@mui/icons-material';
import { AppBar, Box, Divider, List, ListItemButton, ListItemIcon, Menu, MenuItem, Popover, Stack, Toolbar, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { logout } from '../../../actions/Application';
import IconButton from '../../../components/common/button/IconButton';
import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import SearchInput from '../../../components/SearchFilter';
import { THEME_DARK_DEFAULT_BACKGROUND } from '../../../components/ThemeDark';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import logoOpenCtiDark from '../../../static/images/logo_open_cti_dark.svg';
import logoOpenCtiLight from '../../../static/images/logo_open_cti_light.svg';
import logoXtmHubDark from '../../../static/images/logo_xtm_hub_dark.svg';
import logoXtmHubLight from '../../../static/images/logo_xtm_hub_light.svg';
import { MESSAGING$, XTM_HUB_DEFAULT_URL } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import { isFeatureEnabled, isNotEmptyField } from '../../../utils/utils';
import AskArianeButton from '../ariane/AskArianeButton';
import TenantSwitcher from './TenantSwitcher';

const useStyles = makeStyles()(theme => ({
  appBar: {
    width: '100%',
    zIndex: theme.zIndex.drawer - 1,
    background: 0,
    backgroundColor: theme.palette.background.nav,
    paddingTop: theme.spacing(0.2),
    borderLeft: 0,
    borderRight: 0,
    borderTop: 0,
    color: theme.palette.text?.primary,
  },
  menuContainer: {
    width: '50%',
    float: 'left',
  },
  barRight: {
    position: 'absolute',
    top: 0,
    right: 13,
    height: '100%',
  },
}));

const TopBar: FunctionComponent = () => {
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const [switcherAnchor, setSwitcherAnchor] = useState<null | HTMLElement>(null);
  const switcherOpen = Boolean(switcherAnchor);

  const [menuOpen, setMenuOpen] = useState<{
    open: boolean;
    anchorEl: HTMLButtonElement | null;
  }>({
    open: false,
    anchorEl: null,
  });
  const handleOpenMenu = (
    event: ReactMouseEvent<HTMLButtonElement, MouseEvent>,
  ) => {
    event.preventDefault();
    setMenuOpen({
      open: true,
      anchorEl: event.currentTarget,
    });
  };
  const handleCloseMenu = () => {
    setMenuOpen({
      open: false,
      anchorEl: null,
    });
  };

  const handleClickSwitcher = (event: ReactMouseEvent<HTMLElement>) => {
    event.preventDefault();
    event.stopPropagation();
    setSwitcherAnchor(switcherOpen ? null : event.currentTarget);
  };

  const dispatch = useAppDispatch();
  const [navOpen, setNavOpen] = useState(
    localStorage.getItem('navOpen') === 'true',
  );
  useEffect(() => {
    const sub = MESSAGING$.toggleNav.subscribe({ next: () => setNavOpen(localStorage.getItem('navOpen') === 'true') });
    return () => {
      sub.unsubscribe();
    };
  });
  const handleLogout = async () => {
    await dispatch(logout());
    navigate('/');
    handleCloseMenu();
  };

  const onFullTextSearch = (search?: string) => {
    if (search) {
      navigate(`/admin/fulltextsearch?search=${search}`);
    }
  };

  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  const getAppTopBarGradient = (): string => {
    const defaultGradient = `${alpha(THEME_DARK_DEFAULT_BACKGROUND, 0.9)} 0%, ${alpha(theme.palette.designSystem.background.bg1, 0.9)}`;
    if (theme.palette.background.gradient?.start && theme.palette.background.gradient?.end) {
      return `${alpha(theme.palette.background.gradient.start, 0.9)} 0%, ${alpha(theme.palette.background.gradient.end, 0.9)}`;
    }
    return defaultGradient;
  };

  const appBarGradient = getAppTopBarGradient();
  const currentLogo = navOpen ? theme.logo : theme.logo_collapsed;

  return (
    <AppBar
      position="fixed"
      className={classes.appBar}
      variant="elevation"
      elevation={1}
    >
      <Toolbar style={{
        marginTop: bannerHeightNumber,
        paddingLeft: 0,
        background: `linear-gradient(90deg, ${appBarGradient} 100%)`,
        backdropFilter: 'blur(4px)',
      }}
      >
        <Box
          component={Link}
          to="/admin"
          sx={{
            'width': navOpen ? 180 : 55,
            'padding': navOpen ? 2 : '16px 0',
            'paddingRight': navOpen ? 1 : 0,
            'flexShrink': 0,
            'display': 'flex',
            'alignItems': 'center',
            'gap': 1,
            'justifyContent': navOpen ? 'space-between' : 'center',
            '&:hover': { cursor: 'pointer' },
          }}
        >
          <img
            src={currentLogo}
            alt="logo"
            style={{
              height: 35,
              maxWidth: navOpen ? '130px' : '28px',
              objectFit: 'contain',
            }}
          />
          {navOpen && (
            <IconButton
              onClick={handleClickSwitcher}
            >
              <ArrowDropDown
                sx={{
                  transform: switcherOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                  transition: 'transform 0.2s',
                }}
              />
            </IconButton>
          )}
        </Box>
        <Popover
          open={switcherOpen}
          anchorEl={switcherAnchor}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'right',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'left',
          }}
          sx={{ transform: 'translateX(-40px)' }}
          onClose={() => setSwitcherAnchor(null)}
        >
          <List dense disablePadding sx={{ minWidth: 228 }}>
            <Tooltip title={isNotEmptyField(settings.xtm_opencti_url) ? t('Platform connected') : t('Get OpenCTI now')}>
              <span>
                <ListItemButton
                  component="a"
                  href={settings.xtm_opencti_url || 'https://filigran.io'}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={() => setSwitcherAnchor(null)}
                  sx={{
                    borderRadius: 1,
                    px: 1,
                    py: 1.5,
                    display: 'flex',
                    justifyContent: 'space-between',
                    backgroundColor: theme.palette.leftBar.header.itemBackground,
                  }}
                >
                  <ListItemIcon sx={{
                    width: 132,
                    p: 1,
                  }}
                  >
                    <Box sx={{
                      width: '100%',
                      height: '20px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                    >
                      <img
                        src={theme.palette.mode === 'dark' ? logoOpenCtiDark : logoOpenCtiLight}
                        style={{
                          width: '100%',
                          height: 'auto',
                          objectFit: 'contain',
                        }}
                        alt="OpenCTI"
                      />
                    </Box>
                  </ListItemIcon>
                  <OpenInNew style={{ fontSize: 16 }} />
                </ListItemButton>
              </span>
            </Tooltip>
            <Divider />
            <ListItemButton
              component="a"
              href={settings.xtm_hub_url || XTM_HUB_DEFAULT_URL}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => setSwitcherAnchor(null)}
              sx={{
                borderRadius: 1,
                px: 1,
                py: 1.5,
                display: 'flex',
                justifyContent: 'space-between',
                backgroundColor: theme.palette.leftBar.header.itemBackground,
              }}
            >
              <ListItemIcon sx={{
                width: 132,
                p: 1,
              }}
              >
                <Box sx={{
                  width: '100%',
                  height: '20px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
                >
                  <img
                    src={theme.palette.mode === 'dark' ? logoXtmHubDark : logoXtmHubLight}
                    style={{
                      width: '100%',
                      height: 'auto',
                      objectFit: 'contain',
                    }}
                    alt="XTM Hub"
                  />
                </Box>
              </ListItemIcon>
              <OpenInNew style={{ fontSize: 16 }} />
            </ListItemButton>
          </List>
        </Popover>
        <div className={classes.menuContainer} style={{ marginLeft: 20 }}>
          <SearchInput
            variant="topBar"
            placeholder={`${t('Search the platform')}...`}
            fullWidth={true}
            onSubmit={onFullTextSearch}
            keyword={search}
          />
        </div>
        <div className={classes.barRight}>
          <Stack
            direction="row"
            gap={1}
            alignItems="center"
            sx={{ height: '100%' }}
          >
            { settings.platform_license?.license_type === 'nfr' && <ItemBoolean variant="large" label="EE DEV LICENSE" status={false} /> }
            {isFeatureEnabled('MULTI_TENANCY') && <TenantSwitcher />}
            <AskArianeButton />
            <Tooltip title={t('Install simulation agents')}>
              <IconButton
                size="default"
                aria-haspopup="true"
                component={Link}
                to="/admin/agents"
                selected={location.pathname === '/admin/agents'}
              >
                <ImportantDevicesOutlined fontSize="medium" />
              </IconButton>
            </Tooltip>
            <IconButton
              aria-label="account-menu"
              onClick={handleOpenMenu}
              size="default"
              selected={location.pathname === '/admin/profile'}
            >
              <AccountCircleOutlined fontSize="medium" />
            </IconButton>
            <Menu
              id="menu-appbar"
              anchorEl={menuOpen.anchorEl}
              open={menuOpen.open}
              onClose={handleCloseMenu}
            >
              <MenuItem
                onClick={handleCloseMenu}
                component={Link}
                to="/admin/profile"
              >
                {t('Profile')}
              </MenuItem>
              <MenuItem aria-label="logout-item" onClick={handleLogout}>{t('Logout')}</MenuItem>
            </Menu>
          </Stack>
        </div>
      </Toolbar>
    </AppBar>
  );
};

export default TopBar;
