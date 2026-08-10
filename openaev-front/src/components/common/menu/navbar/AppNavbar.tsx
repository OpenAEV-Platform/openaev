import { Navbar, NavbarItem, NavbarSeparator, NavbarSubmenu, NavbarSubmenuItem } from '@filigran/design-system';
import { Fragment, type FunctionComponent, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';

import { computeBannerSettings } from '../../../../public/components/systembanners/utils';
import useAuth from '../../../../utils/hooks/useAuth';
import { useFormatter } from '../../../i18n';
import MadeByFiligran from './MadeByFiligran';
import { hasHref, type NavMenuEntries, type NavMenuItem, type NavMenuItemWithHref, type NavMenuSubItem } from './nav-menu-model';
import { NAV_COLLAPSED_WIDTH, NAV_OPEN_WIDTH, NAV_WIDTH_TRANSITION } from './navbarConstants';
import { NavbarItemContent, NavbarSubmenuItemContent } from './NavbarRowContent';
import useNavbarState from './useNavbarState';

/**
 * Adapter between the product and the design system's `Navbar`.
 *
 * Boundary rule for everything in this folder — the library owns the widget,
 * the product owns the data, the routes and the state. A file here that exists
 * only to work around a library limitation is debt with a filed gap, not a
 * candidate for promotion to the library. The reasoning, and the verdict for
 * each file in this folder, is in fds-migration/IMPLEMENTATION-PLAYBOOK.md,
 * "What belongs to the library vs. to the product".
 */

/**
 * Highlight the exact page and any sub-route (e.g. /admin/integrations/deployed).
 * Home ('/admin') is a prefix of every route, so it stays exact-only.
 */
const isItemCurrent = (pathname: string, path: string): boolean => pathname === path
  || (path !== '/admin' && pathname.startsWith(`${path}/`));

const isSubItemCurrent = (pathname: string, subItem: NavMenuSubItem): boolean => (subItem.exact
  ? pathname === subItem.link
  : pathname.includes(subItem.link));

interface Props {
  entries: NavMenuEntries[];
  /** Rendered in the library's dedicated header slot (above the row list). */
  header?: (navOpen: boolean) => ReactNode;
  /** First row of the list itself, above the menu groups. */
  headerElement?: (navOpen: boolean) => ReactNode;
}

const AppNavbar: FunctionComponent<Props> = ({ entries = [], header, headerElement }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const isWhitemarkEnabled = settings.platform_whitemark === 'true'
    && settings.platform_license?.license_is_validated === true;
  const { navOpen, toggleNav } = useNavbarState();
  const collapsed = !navOpen;

  const renderSingle = (item: NavMenuItem) => (
    <NavbarItem
      key={item.label}
      asChild
      tooltipLabel={t(item.label)}
    >
      <Link
        to={item.path}
        aria-label={t(item.label)}
        aria-current={isItemCurrent(location.pathname, item.path) ? 'page' : undefined}
      >
        <NavbarItemContent icon={item.icon()} label={t(item.label)} collapsed={collapsed} />
      </Link>
    </NavbarItem>
  );

  const renderGroup = (item: NavMenuItemWithHref) => {
    const subItems = (item.subItems ?? []).filter(subItem => subItem.userRight);
    if (subItems.length === 0) {
      return renderSingle(item);
    }
    return (
      <NavbarSubmenu
        key={item.label}
        label={t(item.label)}
        icon={item.icon()}
        // Applied by the library only while collapsed: the flyout trigger then
        // is a real link, so Ctrl/⌘-click on a collapsed group opens the group
        // landing page in a new tab instead of doing nothing.
        to={item.path}
      >
        {subItems.map(subItem => (
          <NavbarSubmenuItem key={subItem.label} asChild>
            <Link
              to={subItem.link}
              aria-label={t(subItem.label)}
              aria-current={isSubItemCurrent(location.pathname, subItem) ? 'page' : undefined}
            >
              <NavbarSubmenuItemContent icon={subItem.icon?.()} label={t(subItem.label)} />
            </Link>
          </NavbarSubmenuItem>
        ))}
      </NavbarSubmenu>
    );
  };

  const renderItem = (item: NavMenuItem) => (hasHref(item) ? renderGroup(item) : renderSingle(item));

  const visibleEntries = entries
    .filter(entry => entry.userRight)
    .map(entry => entry.items.filter(item => item.userRight))
    .filter(items => items.length > 0);

  const headerNode = headerElement?.(navOpen);

  const railWidth = collapsed ? NAV_COLLAPSED_WIDTH : NAV_OPEN_WIDTH;

  return (
    <>
      {/* The rail is taken out of flow (see below), so a spacer holds its
          place in the shell's flex row: without it every page slides left
          and renders underneath the rail. It replays the library's own
          width transition so content and rail animate as one. */}
      <div
        data-testid="navbar-spacer"
        aria-hidden="true"
        style={{
          width: `${railWidth}px`,
          flexShrink: 0,
          transition: NAV_WIDTH_TRANSITION,
        }}
      />
      <Navbar
        aria-label={t('Main navigation')}
        className="app-navbar"
        collapsed={collapsed}
        onCollapsedChange={toggleNav}
        header={header?.(navOpen)}
        // The library always renders its collapse toggle last, below this slot.
        // Only the Filigran wordmark is pinned to the bottom; every menu row,
        // "Getting Started" included, scrolls with the list above.
        footer={!isWhitemarkEnabled ? <MadeByFiligran collapsed={collapsed} /> : undefined}
        // Fixed, never sticky - the same doctrine the Header follows. `sticky`
        // resolves against the app shell, whose height is the document's
        // fractional height (measured: 1677.59px for a 1678px document), so at
        // the end of a long scroll the rail was pushed off its own top by that
        // 0.41px remainder and visibly drifted. Fixed positioning is resolved
        // against the viewport, so no page length can move it.
        style={{
          position: 'fixed',
          top: bannerHeightNumber,
          left: 0,
          height: `calc(100dvh - ${2 * bannerHeightNumber}px)`,
        }}
      >
        {headerNode && (
          <>
            {headerNode}
            <NavbarSeparator />
          </>
        )}
        {visibleEntries.map((items, index) => (
        // Groups are positional, they carry no identity of their own; the rows
        // inside them are keyed by label.
        // eslint-disable-next-line react/no-array-index-key
          <Fragment key={index}>
            {index !== 0 && <NavbarSeparator />}
            {items.map(renderItem)}
          </Fragment>
        ))}
      </Navbar>
    </>
  );
};

export default AppNavbar;
