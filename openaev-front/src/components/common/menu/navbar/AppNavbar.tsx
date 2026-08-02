import { Navbar, NavbarItem, NavbarSeparator, NavbarSubmenu, NavbarSubmenuItem } from '@filigran/design-system';
import { Fragment, type FunctionComponent, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';

import { computeBannerSettings } from '../../../../public/components/systembanners/utils';
import useAuth from '../../../../utils/hooks/useAuth';
import { useFormatter } from '../../../i18n';
import MadeByFiligran from './MadeByFiligran';
import { hasHref, type NavMenuEntries, type NavMenuItem, type NavMenuItemWithHref, type NavMenuSubItem } from './nav-menu-model';
import { NavbarItemContent, NavbarSubmenuItemContent } from './NavbarRowContent';
import useNavbarState from './useNavbarState';

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
  /** Product switcher, rendered in the library's dedicated header slot. */
  header?: (navOpen: boolean) => ReactNode;
  /** Optional first row above the menu groups (the tenant switcher). */
  headerElement?: (navOpen: boolean) => ReactNode;
}

/**
 * The application's main navigation, built on the design system's `Navbar`.
 *
 * The library owns the chrome (width and its transition, the gradient panel,
 * the collapse toggle, the collapsed tooltips and flyout submenus); this
 * component only maps the product's menu model onto it and keeps the collapse
 * state in sync with the rest of the shell.
 */
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

  return (
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
      // The library's <nav> is laid out in flow inside the app shell (the
      // legacy MUI Drawer was fixed-positioned): stick it to the viewport so
      // long pages scroll under it exactly as before.
      style={{
        position: 'sticky',
        top: bannerHeightNumber,
        alignSelf: 'flex-start',
        height: `calc(100dvh - ${2 * bannerHeightNumber}px)`,
        flexShrink: 0,
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
  );
};

export default AppNavbar;
