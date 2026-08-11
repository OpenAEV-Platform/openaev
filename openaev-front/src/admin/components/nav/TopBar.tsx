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
import { NAV_COLLAPSED_WIDTH, NAV_OPEN_WIDTH, TOP_BAR_SEARCH_MAX_WIDTH, TOP_BAR_SEARCH_MIN_WIDTH } from '../../../components/common/menu/navbar/navbarConstants';
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

// Re-exported from the navbar module so the rail, its in-flow spacer and this bar's offset cannot drift apart.
export const OPEN_BAR_WIDTH = NAV_OPEN_WIDTH;
export const SMALL_BAR_WIDTH = NAV_COLLAPSED_WIDTH;

/** Admin top bar on the design system `Header` — see fds-migration/IMPLEMENTATION-LOG.md (Header adoption). */
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

  // Radix anchors the Menu to its trigger, so only the open state is needed.
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
  // Controlled, so an external change to the `search` URL parameter is reflected in the field.
  const [searchValue, setSearchValue] = useState(search ?? '');
  useEffect(() => setSearchValue(search ?? ''), [search]);

  const gradientStart = theme.palette.background.gradient?.start ?? theme.palette.background.default;
  const gradientEnd = theme.palette.background.gradient?.end ?? theme.palette.background.default;

  return (
    // Radix tooltips need a provider in scope; scoped to this bar, not the whole app.
    <TooltipProvider delayDuration={200}>
      <Header
        // FDS-WORKAROUND #20: bar positioned product-side, `fullWidth={false}` with it — remove when the library positions the bar — see fds-migration/LIBRARY-FEEDBACK.md
        fullWidth={false}
        style={{
          'position': 'fixed',
          'top': bannerHeightNumber,
          'left': navOpen ? OPEN_BAR_WIDTH : SMALL_BAR_WIDTH,
          'right': 0,
          'zIndex': theme.zIndex.appBar,
          // FDS-WORKAROUND #17: re-declare the assembled gradient, stops opaque — remove when the library exposes a background hook — see fds-migration/LIBRARY-FEEDBACK.md
          '--gradient-default': `linear-gradient(90deg, ${gradientStart} 0%, ${gradientEnd} 100%)`,
        } as CSSProperties}
      >
        <HeaderGroup
          // `grow` caps at 400px, below this bar's 500px ceiling — see fds-migration/LIBRARY-FEEDBACK.md #18
          grow="unbounded"
          // Search window declared on the group the product owns — see fds-migration/LIBRARY-FEEDBACK.md #18
          style={{
            minWidth: TOP_BAR_SEARCH_MIN_WIDTH,
            maxWidth: TOP_BAR_SEARCH_MAX_WIDTH,
          }}
        >
          <SearchField
            aria-label={t('Search the platform')}
            placeholder={`${t('Search the platform')}...`}
            // Fills the 200-500px window declared on the group above; a `style` here would size the inner input instead.
            fullWidth={true}
            value={searchValue}
            onChange={event => setSearchValue(event.target.value)}
            onSubmit={onFullTextSearch}
            onClear={() => setSearchValue('')}
          />
        </HeaderGroup>
        <HeaderGroup>
          {/* Same availability predicate the XTM One buttons apply, so the divider never renders as an orphan. */}
          {isXtmOneAvailable(settings) && (
            <>
              <AskArianeButton />
              <CtemCommandCenterButton />
              {/* FDS-WORKAROUND #22: separator from the library's border token — remove when it ships a divider — see fds-migration/LIBRARY-FEEDBACK.md */}
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
          {/* OpenCTI-aligned pair: triggers alarm, then the notifications bell. */}
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
          {/* MenuTrigger + asChild around an IconButton is the library's canonical pairing. */}
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
