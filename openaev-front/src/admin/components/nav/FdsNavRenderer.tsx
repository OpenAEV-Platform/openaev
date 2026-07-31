/* eslint-disable i18next/no-literal-string */
// -----------------------------------------------------------------------------
// LOCAL-ONLY DEMO WIRING — NOT FOR COMMIT.
//
// Drop-in replacement for `LeftMenu` (see LeftBar.tsx) that renders the exact
// same `entries` / `bottomEntries` (real LeftMenuEntries[], built from real
// `ability.can()` permission checks and real routes in LeftBar.tsx) through
// @filigran/design-system's Navbar / NavbarItem / NavbarSubmenu instead of the
// current MUI Drawer/MenuList. Only the renderer changes — the permission
// filtering (`entry.userRight` / `item.userRight`) is preserved verbatim from
// LeftMenu.tsx, and every path/label/icon comes straight from the real data
// LeftBar.tsx already computes. No fake menu structure of its own.
//
// Ships its own scoped button reset (`.fds-navrenderer-scope`) so it renders
// correctly wherever it is mounted — this is intentional: @filigran/design-
// system ships without Tailwind preflight (see tokens/index.css in the
// design-system repo), and openaev-front has no preflight of its own. Keeping
// the reset inside this component (rather than relying on a host page to add
// one) means it applies equally in the isolated preview harness AND in the
// real admin/Index.tsx tree, wherever this file is temporarily wired in.
// -----------------------------------------------------------------------------

import '@filigran/design-system/dist/index.css';

import { Navbar, NavbarItem, NavbarSeparator, NavbarSubmenu, NavbarSubmenuItem } from '@filigran/design-system';
import { Box, GlobalStyles } from '@mui/material';
import { Fragment, type FunctionComponent, type ReactNode, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { hasHref, type LeftMenuEntries, type LeftMenuItem } from '../../../components/common/menu/leftmenu/leftmenu-model';
import useThemeModeClass from '../../../utils/hooks/useThemeModeClass';

export interface FdsNavRendererProps {
  entries: LeftMenuEntries[];
  bottomEntries: LeftMenuEntries[];
  headerElement?: (navOpen: boolean) => ReactNode;
  logoHeader?: (navOpen: boolean) => ReactNode;
}

// Scoped ONLY to `.fds-navrenderer-scope` (never app-wide) — same minimal
// native-<button>-chrome fix already used and documented in the FDS spike
// (src/spike/FdsNavbarSpike.tsx). Real fix ownership (openaev-front adopting
// a preflight vs. the package shipping one) is still an open question,
// reported and not decided here.
const scopedButtonReset = (
  <GlobalStyles
    styles={{
      '.fds-navrenderer-scope button': {
        appearance: 'none',
        WebkitAppearance: 'none',
        backgroundColor: 'transparent',
        backgroundImage: 'none',
        border: 0,
        margin: 0,
        padding: 0,
        font: 'inherit',
        color: 'inherit',
        cursor: 'pointer',
      },
    }}
  />
);

const FdsNavRenderer: FunctionComponent<FdsNavRendererProps> = ({ entries, bottomEntries }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [container, setContainer] = useState<HTMLElement | null>(null);
  // Bridges the ambient MUI theme mode onto this container as a real
  // `.dark`/`.light` class — imported and called as-is, per its own
  // docstring contract. Never modified.
  useThemeModeClass(container);

  const isActive = (path: string) => location.pathname === path || location.pathname.startsWith(`${path}/`);

  const renderLeaf = (item: LeftMenuItem) => (
    <NavbarItem
      key={item.label}
      icon={item.icon()}
      aria-current={isActive(item.path) ? 'page' : undefined}
      onClick={() => navigate(item.path)}
    >
      {item.label}
    </NavbarItem>
  );

  const renderGroup = (item: LeftMenuItem) => (
    <NavbarSubmenu
      key={item.label}
      label={item.label}
      icon={item.icon()}
      defaultOpen={(item.subItems ?? []).some(sub => isActive(sub.link))}
    >
      {(item.subItems ?? []).filter(sub => sub.userRight).map(sub => (
        <NavbarSubmenuItem
          key={sub.label}
          icon={sub.icon?.()}
          href={sub.link}
          aria-current={isActive(sub.link) ? 'page' : undefined}
          onClick={(event) => {
            event.preventDefault();
            navigate(sub.link);
          }}
        >
          {sub.label}
        </NavbarSubmenuItem>
      ))}
    </NavbarSubmenu>
  );

  const renderEntries = (list: LeftMenuEntries[]) => list
    .filter(entry => entry.userRight)
    .map((entry, idx) => (
      <Fragment key={idx}>
        {idx !== 0 && <NavbarSeparator />}
        {entry.items.filter(item => item.userRight).map(item => (hasHref(item) ? renderGroup(item) : renderLeaf(item)))}
      </Fragment>
    ));

  const hasBottomEntries = bottomEntries.some(entry => entry.userRight);

  return (
    <>
      {scopedButtonReset}
      <Box ref={setContainer} className="fds-navrenderer-scope" sx={{ height: '100%' }}>
        <Navbar
          aria-label="OpenAEV navigation"
          footer={hasBottomEntries ? <>{renderEntries(bottomEntries)}</> : undefined}
        >
          {renderEntries(entries)}
        </Navbar>
      </Box>
    </>
  );
};

export default FdsNavRenderer;
