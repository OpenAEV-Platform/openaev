/* eslint-disable i18next/no-literal-string */
// -----------------------------------------------------------------------------
// TEMPORARY DESIGN-SYSTEM SPIKE — NOT PRODUCTION NAVIGATION.
//
// Isolated, throwaway visual check that @filigran/design-system's Navbar /
// NavbarItem / NavbarSubmenu / ProductSwitcher render correctly once pulled
// into openaev-front (bundled Tailwind-generated CSS + the dark/light
// bridge). Every piece of data below (menu, user, product list) is fake:
// this screen never calls any real API/GraphQL, is not linked from any real
// entry point, and does not touch any real navigation file (LeftBar, TopBar,
// leftmenu-model.ts or equivalent).
//
// Only reachable at /spike/fds-navbar (route added standalone in app.tsx,
// outside the authenticated app tree, so no login is required to view it).
// Safe to delete entirely (this file + its app.tsx route) once reviewed.
// i18n lint is disabled file-wide on purpose: this screen is not part of the
// product and is never meant to be localized.
//
// Color caveat (ProductSwitcher.rfc.md in the design-system repo): every
// color token on ProductSwitcher is reused from NavbarItem/NavbarSubmenuItem
// and explicitly flagged "to revalidate" — not Figma-confirmed for
// ProductSwitcher itself. Its colors below are not a reliable reference yet.
// -----------------------------------------------------------------------------

import '@filigran/design-system/dist/index.css';

import {
  Icon,
  Navbar,
  NavbarItem,
  NavbarSeparator,
  NavbarSubmenu,
  NavbarSubmenuItem,
  ProductSwitcher,
} from '@filigran/design-system';
import { Box, CssBaseline, FormControlLabel, GlobalStyles, Switch, Typography } from '@mui/material';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { type FunctionComponent, useMemo, useState } from 'react';

import ThemeDark from '../components/ThemeDark';
import ThemeLight from '../components/ThemeLight';
import useThemeModeClass from '../utils/hooks/useThemeModeClass';

// Fake, representative-only data — no real API/GraphQL call anywhere below.
const FAKE_PRODUCTS = [
  {
    id: 'opencti',
    label: 'OpenCTI',
    iconName: 'custom/opencti',
    href: 'https://example.com/opencti',
  },
  {
    id: 'opengrc',
    label: 'OpenGRC',
    iconName: 'custom/opengrc',
    href: 'https://example.com/opengrc',
  },
  {
    id: 'xtmhub',
    label: 'XTM Hub',
    iconName: 'custom/xtmhub',
    href: 'https://example.com/xtm-hub',
  },
  {
    id: 'xtmone',
    label: 'XTM ONE',
    iconName: 'custom/xtmone',
    href: 'https://example.com/xtm-one',
  },
] as const;

const FAKE_USER_LABEL = 'Jane Doe (Analyst)';

const SpikeProductSwitcher: FunctionComponent = () => (
  <ProductSwitcher
    logo={<Icon name="custom/openaev" size={20} />}
    label="More Filigran products"
    options={FAKE_PRODUCTS.map(product => ({
      id: product.id,
      label: product.label,
      logo: <Icon name={product.iconName} size={20} />,
      href: product.href,
    }))}
  />
);

const SpikeMenuItems: FunctionComponent = () => (
  <>
    <NavbarItem icon={<Icon name="layout-dashboard" size={16} />} aria-current="page">
      Dashboard
    </NavbarItem>
    <NavbarItem icon={<Icon name="custom/target" size={16} />}>
      Simulations
    </NavbarItem>
    <NavbarSeparator />
    <NavbarSubmenu label="Findings" icon={<Icon name="file-search" size={16} />} defaultOpen>
      <NavbarSubmenuItem href="#">Vulnerabilities</NavbarSubmenuItem>
      <NavbarSubmenuItem href="#">Exposures</NavbarSubmenuItem>
    </NavbarSubmenu>
    <NavbarItem icon={<Icon name="server" size={16} />}>
      Assets
    </NavbarItem>
    <NavbarSeparator />
    <NavbarItem icon={<Icon name="settings" size={16} />}>
      Settings
    </NavbarItem>
  </>
);

const SpikeFooter: FunctionComponent = () => (
  <NavbarItem icon={<Icon name="circle-user" size={16} />}>{FAKE_USER_LABEL}</NavbarItem>
);

/**
 * Split out from FdsNavbarSpike so useThemeModeClass (and the useTheme() it
 * calls internally) reads the ThemeProvider set up just above it, exactly
 * the way any real FDS-consuming component is expected to use the hook per
 * its own docstring (imported and called as-is, never modified).
 */
const SpikeContent: FunctionComponent = () => {
  const [container, setContainer] = useState<HTMLElement | null>(null);
  const mode = useThemeModeClass(container);

  return (
    <Box
      ref={setContainer}
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 6,
        p: 4,
      }}
    >
      <Box sx={{
        height: 520,
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>
          {`Expanded (${mode})`}
        </Typography>
        <Navbar aria-label="Spike navigation (expanded)" header={<SpikeProductSwitcher />} footer={<SpikeFooter />}>
          <SpikeMenuItems />
        </Navbar>
      </Box>
      <Box sx={{
        height: 520,
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>
          {`Collapsed (${mode})`}
        </Typography>
        <Navbar aria-label="Spike navigation (collapsed)" header={<SpikeProductSwitcher />} footer={<SpikeFooter />} defaultCollapsed>
          <SpikeMenuItems />
        </Navbar>
      </Box>
    </Box>
  );
};

// @filigran/design-system intentionally ships without Tailwind's preflight
// reset (see tokens/index.css in the design-system repo: "A distributed
// component library must never impose a global CSS reset. Host apps ...
// include their own preflight."). openaev-front is an MUI app with no
// Tailwind preflight of its own, so plain <button> elements (used by
// NavbarItem/NavbarSubmenu/ProductSwitcher) fall back to native browser
// chrome (opaque background, default appearance) instead of the transparent/
// token-driven background the components expect, which hides their text.
// This is a real open integration question for a future non-spike
// integration (does openaev-front adopt a preflight, does the package ship
// one, ...) — NOT decided here. This GlobalStyles block is a minimal,
// intentionally scoped (".fds-spike-scope" only) button reset so THIS
// throwaway screen renders correctly for review; it is never applied
// app-wide and must not be read as a proposed real fix.
const spikeScopedButtonReset = (
  <GlobalStyles
    styles={{
      '.fds-spike-scope button': {
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

const FdsNavbarSpike: FunctionComponent = () => {
  const [mode, setMode] = useState<'dark' | 'light'>('dark');
  const theme = useMemo(
    () => createTheme(mode === 'dark' ? ThemeDark() : ThemeLight()),
    [mode],
  );

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {spikeScopedButtonReset}
      <Box className="fds-spike-scope">
        <Box sx={{
          px: 4,
          pt: 4,
        }}
        >
          <Typography variant="h6">FDS Navbar spike — temporary, not real navigation</Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              mb: 2,
              maxWidth: 720,
            }}
          >
            Visual-only check of @filigran/design-system Navbar / NavbarItem / NavbarSubmenu /
            ProductSwitcher, with fake menu, user and product data. ProductSwitcher colors are
            reused from NavbarItem and flagged &quot;to revalidate&quot; in its RFC — not a
            reliable color reference yet. Native button chrome is neutralized below via a
            reset scoped to this spike only (see comment above) — see report for details.
          </Typography>
          <FormControlLabel
            control={(
              <Switch
                checked={mode === 'light'}
                onChange={(_event, checked) => setMode(checked ? 'light' : 'dark')}
              />
            )}
            label="Light mode"
          />
        </Box>
        <SpikeContent />
      </Box>
    </ThemeProvider>
  );
};

export default FdsNavbarSpike;
