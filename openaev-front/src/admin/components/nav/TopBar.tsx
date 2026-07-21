import { AccountCircleOutlined, ImportantDevicesOutlined } from '@mui/icons-material';
import { AppBar, Divider, IconButton, Menu, MenuItem, Stack, Toolbar, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';

import { logout } from '../../../actions/Application';
import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import SearchInput from '../../../components/SearchFilter';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import AskArianeButton from '../ariane/AskArianeButton';
import AskArianePanel from '../ariane/AskArianePanel';
import CtemCommandCenterButton from '../ariane/CtemCommandCenterButton';
import { useChatbot } from '../ariane/useChatbotHooks';
import TopBarNotifications from './TopBarNotifications';

// Drawer widths shared with the left menu (OpenCTI: OPEN_BAR_WIDTH / SMALL_BAR_WIDTH).
export const OPEN_BAR_WIDTH = 180;
export const SMALL_BAR_WIDTH = 55;

/**
 * Top bar aligned with OpenCTI's TopBar: a fixed, transparent, blur-backdrop
 * AppBar offset by the left drawer, a 68px gradient Toolbar with the global
 * search on the left and the action cluster (AI actions, divider, platform
 * actions, profile menu) on the right. The logo and the Filigran product
 * switcher live in the left drawer header (LeftBarHeader), not here.
 */
const TopBar: FunctionComponent = () => {
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const dispatch = useAppDispatch();
  const [navOpen, setNavOpen] = useState(
    localStorage.getItem('navOpen') === 'true',
  );
  useEffect(() => {
    const sub = MESSAGING$.toggleNav.subscribe({ next: () => setNavOpen(localStorage.getItem('navOpen') === 'true') });
    return () => {
      sub.unsubscribe();
    };
  }, []);

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

  const {
    isOpen: isArianeChatOpen,
    mode: arianeChatMode,
    closeChat,
    setMode,
    setSidebarWidth,
    setIsResizing,
  } = useChatbot();
  const handleLogout = async () => {
    await dispatch(logout());
    window.location.href = '/';
    handleCloseMenu();
  };

  // Full Text search
  const onFullTextSearch = (search?: string) => {
    if (search) {
      navigate(`/admin/fulltextsearch?search=${encodeURIComponent(search)}`);
    }
  };
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  // Same 36px squared icon button anatomy as OpenCTI's top bar icons.
  const topBarIconButtonSx = (selected: boolean) => ({
    'width': 36,
    'height': 36,
    'borderRadius': 1,
    'color': theme.palette.primary.main,
    'backgroundColor': selected ? alpha(theme.palette.primary.main, 0.15) : 'transparent',
    '&:hover': { backgroundColor: alpha(theme.palette.primary.main, 0.15) },
  });

  const gradientStart = theme.palette.background.gradient?.start ?? theme.palette.background.default;
  const gradientEnd = theme.palette.background.gradient?.end ?? theme.palette.background.default;

  return (
    <>
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          marginLeft: navOpen ? `${OPEN_BAR_WIDTH}px` : `${SMALL_BAR_WIDTH}px`,
          width: navOpen ? `calc(100% - ${OPEN_BAR_WIDTH}px)` : `calc(100% - ${SMALL_BAR_WIDTH}px)`,
          backgroundColor: 'transparent',
          backdropFilter: 'blur(4px)',
        }}
      >
        <Toolbar
          style={{
            alignItems: 'center',
            marginTop: bannerHeightNumber,
            height: '100%',
            minHeight: 68,
            paddingLeft: theme.spacing(3),
            paddingRight: theme.spacing(3),
            display: 'flex',
            justifyContent: 'space-between',
            background: `linear-gradient(90deg, ${alpha(gradientStart, 0.9)} 0%, ${alpha(gradientEnd, 0.9)} 100%)`,
          }}
        >
          <Stack
            direction="row"
            spacing={1}
            sx={{
              minWidth: 550,
              width: '50%',
              maxWidth: 680,
            }}
          >
            <SearchInput
              variant="topBar"
              placeholder={`${t('Search the platform')}...`}
              fullWidth={true}
              onSubmit={onFullTextSearch}
              keyword={search}
            />
          </Stack>
          <div>
            <Stack direction="row" gap={1} alignItems="center">
              {settings.filigran_chatbot_ai_cgu_status !== 'disabled' && (
                <>
                  <AskArianeButton />
                  <CtemCommandCenterButton />
                  {/* Discrete full-height separator between the AI (XTM One)
                      actions and the standard platform actions. */}
                  <Divider orientation="vertical" flexItem sx={{ mx: 1.5 }} />
                </>
              )}
              {settings.platform_license?.license_type === 'nfr' && (
                <ItemBoolean variant="large" label="EE DEV LICENSE" status={false} />
              )}
              <TopBarNotifications iconButtonSx={topBarIconButtonSx} />
              <Tooltip title={t('Install simulation agents')}>
                <IconButton
                  aria-haspopup="true"
                  component={Link}
                  to="/admin/agents"
                  sx={topBarIconButtonSx(location.pathname === '/admin/agents')}
                >
                  <ImportantDevicesOutlined fontSize="medium" />
                </IconButton>
              </Tooltip>
              <IconButton
                aria-owns={menuOpen.open ? 'menu-appbar' : undefined}
                aria-haspopup="true"
                aria-label="account-menu"
                id="profile-menu-button"
                onClick={handleOpenMenu}
                sx={topBarIconButtonSx(location.pathname === '/admin/profile')}
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
      {settings.platform_xtm_one_configured && isArianeChatOpen && (
        <AskArianePanel
          mode={arianeChatMode}
          onClose={closeChat}
          onModeChange={setMode}
          onWidthChange={setSidebarWidth}
          onResizeStart={() => setIsResizing(true)}
          onResizeEnd={() => setIsResizing(false)}
        />
      )}
    </>
  );
};

export default TopBar;
