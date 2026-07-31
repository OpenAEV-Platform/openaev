/* eslint-disable i18next/no-literal-string */
// -----------------------------------------------------------------------------
// LOCAL-ONLY DEMO HARNESS — NOT FOR COMMIT, NEVER PUSHED.
//
// Renders the REAL, unmodified-in-logic `LeftBar` (src/admin/components/nav/
// LeftBar.tsx) — same real menu entries, same real `ability.can()` permission
// checks, same real routes — through a temporary local edit that swaps its
// final renderer from `LeftMenu` to the new `@filigran/design-system` Navbar
// (see FdsNavRenderer.tsx). This is NOT a fake/parallel menu structure.
//
// Why this harness exists: reaching LeftBar normally requires a successful
// login against a real backend (proxied to http://localhost:8080 by
// vite.config.ts). No backend is running in this environment (`/api/me` and
// `/login` both 502 through the dev proxy), so the app's real bootstrap
// sequence (Redux-fed AppThemeProvider / auth flow) never completes and never
// reaches LeftBar through the normal authenticated tree.
//
// To verify the Navbar swap visually before the 12:00 demo without a live
// backend, this screen mounts `<LeftBar />` directly and supplies two
// STUBBED contexts it reads via plain React Context (not Redux, so no store
// wiring needed):
//   - UserContext (useAuth()) — fake `me`/`settings`, empty `userTenants`.
//   - AbilityContext (CASL) — built with the REAL `defineAbility()` function
//     from utils/permissions/ability.ts, called with `isAdmin: true`, i.e.
//     `can('manage', 'all')`. This is a genuine grant-all CASL ability
//     (real code), not a hand-rolled fake — but it is NOT a realistic,
//     per-role permission set, so item visibility below does NOT reflect
//     what any real user would actually see.
//
// STATUS: structurally real (real entries, real paths, real permission-
// filtering code path) but the underlying auth/permission DATA is simulated
// and unverified against a real backend — see the on-screen banner below.
// -----------------------------------------------------------------------------

import { Alert, AlertTitle, Box, CssBaseline } from '@mui/material';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import LeftBar from '../admin/components/nav/LeftBar';
import ThemeDark from '../components/ThemeDark';
import { type User } from '../utils/api-types';
import { UserContext, type UserContextType } from '../utils/hooks/useAuth';
import { defineAbility } from '../utils/permissions/ability';
import { AbilityContext } from '../utils/permissions/permissionsContext';

// Grant-all CASL ability, built with the REAL defineAbility() (not
// reimplemented) — see file header. Constant: no need to rebuild per render.
const stubbedAbility = defineAbility([], {}, true);

const stubbedUserContext: UserContextType = {
  me: {
    user_firstname: 'Jane',
    user_lastname: 'Doe',
  } as User,
  // Minimal, not a real PlatformSettings payload - only used so useAuth()'s
  // `if (!me || !settings) throw` guard passes. Real PlatformSettings shape
  // intentionally not reproduced here.
  settings: {} as UserContextType['settings'],
  isXTMHubAccessible: false,
  userTenants: [],
  currentUserTenant: null,
  switchUserTenant: async () => {},
  reloadUserTenants: async () => {},
};

/**
 * App default is dark (AppThemeProvider.tsx: `useState('dark')`, falls back
 * to 'dark' whenever platform/tenant/user theme settings are absent) — the
 * known Navbar-shell-always-dark gap (see PR #7068 report) is therefore not
 * expected to surface here. Fixed to dark for this harness; no toggle
 * (unlike the FdsNavbarSpike, which deliberately demonstrates both modes).
 */
const FdsLeftBarPreview: FunctionComponent = () => {
  const theme = useMemo(() => createTheme(ThemeDark()), []);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <UserContext.Provider value={stubbedUserContext}>
        <AbilityContext.Provider value={stubbedAbility}>
          <Box sx={{
            display: 'flex',
            minHeight: '100vh',
          }}
          >
            <LeftBar />
            <Box sx={{
              flex: 1,
              p: 3,
            }}
            >
              <Alert severity="warning" sx={{ mb: 2 }}>
                <AlertTitle>Stubbed auth — local demo only, never committed</AlertTitle>
                This screen renders the real LeftBar.tsx (real entries, real
                routes) through the new design-system Navbar, but the
                permission/tenant/user data around it is simulated (grant-all
                CASL ability, empty tenant list, fake user) because no backend
                is reachable in this environment. Item visibility here does
                NOT reflect real per-role permissions — not verified against
                a real backend. Clicking an item navigates the real route via
                react-router (watch the address bar) but there is no live
                page behind most routes in this harness.
              </Alert>
              <p>Click a navigation item on the left — the URL above updates via real react-router navigation.</p>
            </Box>
          </Box>
        </AbilityContext.Provider>
      </UserContext.Provider>
    </ThemeProvider>
  );
};

export default FdsLeftBarPreview;
