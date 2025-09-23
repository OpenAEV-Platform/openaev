import { AccountCircleOutlined, AppsOutlined, ImportantDevicesOutlined } from '@mui/icons-material';
import { AppBar, Badge, Box, Grid, IconButton, Menu, MenuItem, Popover, Toolbar, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useContext, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { logout } from '../../../actions/Application';
import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import SearchInput from '../../../components/SearchFilter';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import obasDark from '../../../static/images/xtm/obas_dark.png';
import obasLight from '../../../static/images/xtm/obas_light.png';
import octiDark from '../../../static/images/xtm/octi_dark.png';
import octiLight from '../../../static/images/xtm/octi_light.png';
import xtmhubDark from '../../../static/images/xtm/xtm_hub_dark.png';
import xtmhubLight from '../../../static/images/xtm/xtm_hub_light.png';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import { AbilityContext } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';

const useStyles = makeStyles()(theme => ({
  appBar: {
    width: '100%',
    zIndex: theme.zIndex.drawer + 1,
    background: 0,
    backgroundColor: theme.palette.background.nav,
    paddingTop: theme.spacing(0.2),
    borderLeft: 0,
    borderRight: 0,
    borderTop: 0,
    color: theme.palette.text?.primary,
  },
  logoContainer: { margin: '2px 0 0 10px' },
  logo: {
    cursor: 'pointer',
    height: 35,
    marginRight: 3,
  },
  logoCollapsed: {
    cursor: 'pointer',
    height: 35,
    marginRight: 4,
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
  barRightContainer: {
    float: 'left',
    height: '100%',
    paddingTop: 12,
  },
  subtitle: {
    color: theme.palette.text?.secondary,
    fontSize: '15px',
    marginBottom: 20,
  },
  xtmItem: {
    'display': 'block',
    'color': theme.palette.text?.primary,
    'textAlign': 'center',
    'padding': '15px 0 10px 0',
    'borderRadius': 4,
    '&:hover': { backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)' },
  },
  xtmItemCurrent: {
    display: 'block',
    color: theme.palette.text?.primary,
    textAlign: 'center',
    cursor: 'default',
    padding: '15px 0 10px 0',
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
    borderRadius: 4,
  },
  product: {
    margin: '5px auto 0 auto',
    textAlign: 'center',
    fontSize: 15,
  },
}));

const TopBar: FunctionComponent = () => {
  // Standard hooks
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const ability = useContext(AbilityContext);
  const isHubRegistrationEnabled = isFeatureEnabled('OPENAEV_REGISTRATION');

  const [xtmOpen, setXtmOpen] = useState<{
    open: boolean;
    anchorEl: HTMLButtonElement | null;
  }>({
    open: false,
    anchorEl: null,
  });
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
  const handleOpenXtm = (
    event: ReactMouseEvent<HTMLButtonElement, MouseEvent>,
  ) => {
    event.preventDefault();
    setXtmOpen({
      open: true,
      anchorEl: event.currentTarget,
    });
  };
  const handleCloseXtm = () => {
    setXtmOpen({
      open: false,
      anchorEl: null,
    });
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

  // Full Text search
  const onFullTextSearch = (search?: string) => {
    if (search) {
      navigate(`/admin/fulltextsearch?search=${search}`);
    }
  };

  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  const xtmhubBadgeImg = (
    <img
      style={{
        width: '100%',
        paddingRight: theme.spacing(2),
        paddingLeft: theme.spacing(2),
      }}
      src={theme.palette.mode === 'dark' ? xtmhubDark : xtmhubLight}
      alt="XTM Hub"
    />
  );
  const shouldXtmHubRedirectToSite = !isHubRegistrationEnabled
    || settings.xtm_hub_registration_status === 'registered'
    || !ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS);

  return (
    <AppBar
      position="fixed"
      className={classes.appBar}
      variant="outlined"
      elevation={0}
    >
      <Toolbar style={{
        marginTop: bannerHeightNumber,
        paddingLeft: 0,
      }}
      >
        <div className={classes.logoContainer}>
          <Link to="/admin">
            <img
              src={navOpen ? theme.logo : theme.logo_collapsed}
              alt="logo"
              className={navOpen ? classes.logo : classes.logoCollapsed}
            />
          </Link>
        </div>
        <div className={classes.menuContainer} style={{ marginLeft: navOpen ? 20 : 30 }}>
          <SearchInput
            variant="topBar"
            placeholder={`${t('Search the platform')}...`}
            fullWidth={true}
            onSubmit={onFullTextSearch}
            keyword={search}
          />
        </div>
        <div className={classes.barRight}>
          <div className={classes.barRightContainer}>
            { settings.platform_license?.license_type === 'nfr' && <ItemBoolean variant="large" label="EE DEV LICENSE" status={false} /> }
            <Tooltip title={t('Install simulation agents')}>
              <IconButton
                size="medium"
                aria-haspopup="true"
                component={Link}
                to="/admin/agents"
                color={location.pathname === '/admin/agents' ? 'primary' : 'inherit'}
              >
                <ImportantDevicesOutlined fontSize="medium" />
              </IconButton>
            </Tooltip>
            <IconButton
              color="inherit"
              size="medium"
              aria-owns={xtmOpen.open ? 'menu-appbar' : undefined}
              aria-haspopup="true"
              id="xtm-menu-button"
              onClick={handleOpenXtm}
            >
              <AppsOutlined fontSize="medium" />
            </IconButton>
            <Popover
              anchorEl={xtmOpen.anchorEl}
              open={xtmOpen.open}
              onClose={handleCloseXtm}
              anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'center',
              }}
              transformOrigin={{
                vertical: 'top',
                horizontal: 'center',
              }}
            >
              <Box sx={{
                width: '300px',
                padding: '15px',
                textAlign: 'center',
              }}
              >
                <div className={classes.subtitle}>{t('Filigran eXtended Threat Management')}</div>
                <Grid container spacing={3}>
                  <Grid size={12}>
                    <Tooltip title="XTM Hub">
                      { shouldXtmHubRedirectToSite ? (
                        <a
                          className={classes.xtmItem}
                          href={settings.xtm_hub_enable && settings.xtm_hub_url ? settings.xtm_hub_url : 'https://hub.filigran.io'}
                          target="_blank"
                          rel="noreferrer"
                          onClick={handleCloseXtm}
                        >
                          <Badge variant="dot" color={!isHubRegistrationEnabled || settings.xtm_hub_registration_status === 'registered' ? 'success' : 'warning'}>
                            {xtmhubBadgeImg}
                          </Badge>
                        </a>
                      ) : (
                        <Link className={classes.xtmItem} to="/admin/settings/experience" onClick={handleCloseXtm}>
                          <Badge variant="dot" color="warning">
                            {xtmhubBadgeImg}
                          </Badge>
                        </Link>
                      )}
                    </Tooltip>
                  </Grid>
                  <Grid size={6}>
                    <Tooltip title={settings.xtm_opencti_enable && settings.xtm_opencti_url ? t('Platform connected') : t('Get OpenCTI now')}>
                      <a
                        className={classes.xtmItem}
                        href={settings.xtm_opencti_enable && settings.xtm_opencti_url ? settings.xtm_opencti_url : 'https://filigran.io'}
                        target="_blank"
                        rel="noreferrer"
                        onClick={handleCloseXtm}
                      >
                        <Badge variant="dot" color={settings.xtm_opencti_enable && settings.xtm_opencti_url ? 'success' : 'warning'}>
                          <img style={{ width: 40 }} src={theme.palette.mode === 'dark' ? octiDark : octiLight} alt="OCTI" />
                        </Badge>
                        <div className={classes.product}>{t('OpenCTI')}</div>
                      </a>
                    </Tooltip>
                  </Grid>
                  <Grid size={6}>
                    <Tooltip title={t('Current platform')}>
                      <a className={classes.xtmItemCurrent}>
                        <Badge variant="dot" color="success">
                          <img style={{ width: 40 }} src={theme.palette.mode === 'dark' ? obasDark : obasLight} alt="OBAS" />
                        </Badge>
                        <div className={classes.product}>{t('OpenBAS')}</div>
                      </a>
                    </Tooltip>
                  </Grid>
                </Grid>
              </Box>
            </Popover>
            <IconButton
              aria-label="account-menu"
              onClick={handleOpenMenu}
              size="medium"
              color={
                location.pathname === '/admin/profile' ? 'primary' : 'inherit'
              }
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
          </div>
        </div>
      </Toolbar>
    </AppBar>
  );
};

export default TopBar;
