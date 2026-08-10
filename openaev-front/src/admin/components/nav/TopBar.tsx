import { Header, HeaderGroup } from '@filigran/design-system';
import { AccountCircleOutlined, AlarmOnOutlined, ImportantDevicesOutlined } from '@mui/icons-material';
import { Divider, IconButton, Menu, MenuItem, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, type MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';

import { logout } from '../../../actions/Application';
import { NAV_COLLAPSED_WIDTH, NAV_OPEN_WIDTH } from '../../../components/common/menu/navbar/navbarConstants';
import { readNavOpen } from '../../../components/common/menu/navbar/useNavbarState';
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
import isXtmOneAvailable from '../ariane/xtmOneAvailability';
import BulkOperationsIndicator from './BulkOperationsIndicator';
import TopBarNotifications from './TopBarNotifications';

// Navigation widths. Re-exported from the navbar module so the rail, the
// spacer that holds its place in the shell and this bar's left offset can
// never drift apart.
export const OPEN_BAR_WIDTH = NAV_OPEN_WIDTH;
export const SMALL_BAR_WIDTH = NAV_COLLAPSED_WIDTH;

/**
 * Top bar built on the design system `Header`: a fixed bar offset by the left
 * navigation, carrying the global search on the left and the action cluster
 * (AI actions, divider, platform actions, profile menu) on the right. The logo
 * and the Filigran product switcher live in the left drawer header
 * (LeftBarHeader), not here.
 *
 * Height, background gradient, 94% glass opacity, backdrop blur and the bottom
 * hairline all come from the library now; this file supplies only what the
 * library deliberately leaves to the consumer — the page positioning — and the
 * product content inside.
 *
 * Everything product-specific is passed as an inline STYLE, not a class. The
 * library ships a compiled stylesheet, not Tailwind: it contains the utilities
 * the library itself renders and nothing more, and this application has no
 * Tailwind build. A class this file invented would silently do nothing.
 */
const TopBar: FunctionComponent = () => {
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const dispatch = useAppDispatch();
  // Same resolution helper as the navigation itself: with a viewport-based
  // default, reading localStorage directly here would disagree with the
  // navigation on first mount and offset the top bar by the wrong width.
  const [navOpen, setNavOpen] = useState(readNavOpen);
  useEffect(() => {
    const sub = MESSAGING$.toggleNav.subscribe({ next: () => setNavOpen(readNavOpen()) });
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
      <Header
        // The library owns no page positioning on purpose (the offset differs
        // per product), but the doctrine is that the bar is ALWAYS fixed to
        // the top and never sticky. `fullWidth={false}` is required with an
        // offset: `w-full` is 100% of the containing block and does not
        // conflict with `left`, so the default would overflow to the right by
        // exactly the navigation width.
        fullWidth={false}
        style={{
          'position': 'fixed',
          'top': bannerHeightNumber,
          'left': navOpen ? OPEN_BAR_WIDTH : SMALL_BAR_WIDTH,
          'right': 0,
          'zIndex': theme.zIndex.appBar,
          // STEP 6b — preserve a customer-configurable colour.
          //
          // The bar's gradient follows the platform's `background_color`
          // setting (per-tenant, admin-editable): it reaches here through
          // palette.background.gradient. The library paints the bar with its
          // own `--gradient-default`, so adopting the component as-is would
          // silently repaint every customised instance with Filigran's
          // default — a functional loss, not a visual delta.
          //
          // Re-declaring the gradient ON THIS ELEMENT is what works. A
          // var() inside a custom-property declaration is substituted at
          // computed-value time on the element that declares it, so
          // overriding the two stop tokens from a wrapper would not repaint
          // a gradient already assembled at :root — but re-declaring the
          // assembled property itself does. Scoped here rather than to
          // :root so the customer's colour reaches the bar exactly as it
          // does today, and nothing else in the library moves.
          //
          // The stops are passed OPAQUE: the legacy bar faded them itself at
          // 90%, whereas the library paints its gradient layer at Figma's
          // 94%. Passing pre-faded stops would apply the transparency twice.
          '--gradient-default': `linear-gradient(90deg, ${gradientStart} 0%, ${gradientEnd} 100%)`,
        } as CSSProperties}
      >
        <HeaderGroup
          // The library's growing cluster is capped at Figma's 400px; this
          // bar's MINIMUM is 550px, so the cap is not merely tight, it is
          // below the floor. "unbounded" is the supported way to say "I
          // supply my own window" instead of fighting the cap.
          grow="unbounded"
          style={{
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
        </HeaderGroup>
        <HeaderGroup>
          {/* XTM One (agentic AI) block: only when XTM One is available
              (configured, valid URL, AI not disabled) - the exact same
              predicate the buttons apply themselves, shared so the
              divider never renders as an orphan. */}
          {isXtmOneAvailable(settings) && (
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
          <BulkOperationsIndicator />
          {/* OpenCTI-aligned pair: the triggers alarm icon right before the
              notifications bell, each leading to its own profile page. */}
          <Tooltip title={t('Triggers')}>
            <IconButton
              aria-label="triggers"
              component={Link}
              to="/admin/profile/triggers"
              sx={topBarIconButtonSx(location.pathname === '/admin/profile/triggers')}
            >
              <AlarmOnOutlined fontSize="medium" />
            </IconButton>
          </Tooltip>
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
        </HeaderGroup>
      </Header>
      {isXtmOneAvailable(settings) && isArianeChatOpen && (
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
