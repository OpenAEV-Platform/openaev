import {
  Header,
  HeaderGroup,
  IconButton,
  Menu,
  MenuContent,
  MenuItem,
  MenuTrigger,
  SearchField,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@filigran/design-system';
import { AccountCircleOutlined, AlarmOnOutlined, ImportantDevicesOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';

import { logout } from '../../../actions/Application';
import { NAV_COLLAPSED_WIDTH, NAV_OPEN_WIDTH } from '../../../components/common/menu/navbar/navbarConstants';
import { readNavOpen } from '../../../components/common/menu/navbar/useNavbarState';
import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
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
import TopBarIconLink from './TopBarIconLink';
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

  // The library's Menu is Radix-based: it anchors itself to its trigger, so
  // the anchor element MUI needed is gone and only the open state remains.
  const [menuOpen, setMenuOpen] = useState(false);
  const handleCloseMenu = () => setMenuOpen(false);

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
  // Controlled, so an external change to the URL parameter (landing on the
  // full-text page, or clearing it) is reflected in the field. The legacy
  // SearchInput did this through a `keyword` prop; the library's field is a
  // plain controlled input, so the sync is explicit here.
  const [searchValue, setSearchValue] = useState(search ?? '');
  useEffect(() => setSearchValue(search ?? ''), [search]);

  const gradientStart = theme.palette.background.gradient?.start ?? theme.palette.background.default;
  const gradientEnd = theme.palette.background.gradient?.end ?? theme.palette.background.default;

  return (
    // Radix tooltips require a provider in scope. It wraps the bar rather than
    // the whole app so the adoption stays contained to this pilot.
    <TooltipProvider delayDuration={200}>
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
          <SearchField
            aria-label={t('Search the platform')}
            placeholder={`${t('Search the platform')}...`}
            fullWidth={true}
            value={searchValue}
            onChange={event => setSearchValue(event.target.value)}
            onSubmit={onFullTextSearch}
            onClear={() => setSearchValue('')}
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
                  actions and the standard platform actions. The library ships
                  no general-purpose divider (only NavbarSeparator and the
                  menu/select separators, all bound to their own component), so
                  this is a plain rule painted with the library's own border
                  token rather than a MUI Divider. See LIBRARY-FEEDBACK.md #22. */}
              <div
                role="separator"
                aria-orientation="vertical"
                className="self-stretch w-px my-2 mx-1.5 bg-border-medium"
              />
            </>
          )}
          {settings.platform_license?.license_type === 'nfr' && (
            <ItemBoolean variant="large" label="EE DEV LICENSE" status={false} />
          )}
          <BulkOperationsIndicator />
          {/* OpenCTI-aligned pair: the triggers alarm icon right before the
              notifications bell, each leading to its own profile page. */}
          <Tooltip>
            <TooltipTrigger asChild>
              <TopBarIconLink
                aria-label="triggers"
                to="/admin/profile/triggers"
                active={location.pathname === '/admin/profile/triggers'}
                icon={<AlarmOnOutlined fontSize="medium" />}
              />
            </TooltipTrigger>
            <TooltipContent>{t('Triggers')}</TooltipContent>
          </Tooltip>
          <TopBarNotifications />
          <Tooltip>
            <TooltipTrigger asChild>
              <TopBarIconLink
                aria-label={t('Install simulation agents')}
                to="/admin/agents"
                active={location.pathname === '/admin/agents'}
                icon={<ImportantDevicesOutlined fontSize="medium" />}
              />
            </TooltipTrigger>
            <TooltipContent>{t('Install simulation agents')}</TooltipContent>
          </Tooltip>
          {/* The library documents MenuTrigger+asChild around an IconButton as
              the canonical pairing, and Radix anchors the panel to the trigger
              itself - so the anchorEl MUI required is gone. */}
          <Menu open={menuOpen} onOpenChange={setMenuOpen}>
            <MenuTrigger asChild>
              <IconButton
                priority="tertiary"
                aria-label="account-menu"
                id="profile-menu-button"
                active={location.pathname === '/admin/profile'}
                icon={<AccountCircleOutlined fontSize="medium" />}
              />
            </MenuTrigger>
            <MenuContent align="end">
              <MenuItem asChild onSelect={handleCloseMenu}>
                <Link to="/admin/profile">{t('Profile')}</Link>
              </MenuItem>
              <MenuItem aria-label="logout-item" onSelect={handleLogout}>
                {t('Logout')}
              </MenuItem>
            </MenuContent>
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
    </TooltipProvider>
  );
};

export default TopBar;
