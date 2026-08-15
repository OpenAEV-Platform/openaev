import { enUS, esES, frFR, type Localization, zhCN } from '@mui/material/locale';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';

import { type LoggedHelper } from '../actions/helper';
import { useHelper } from '../store';
import { type PlatformSettings, type TenantSettingsOutput, type User } from '../utils/api-types';
import { useFormatter } from './i18n';
import themeDark from './ThemeDark';
import themeLight from './ThemeLight';

export const scaleFactor = 8;

interface Props { children: ReactNode }

const localeMap = {
  en: enUS,
  fr: frFR,
  es: esES,
  zh: zhCN,
};

const AppThemeProvider: FunctionComponent<Props> = ({ children }) => {
  const [muiLocale, setMuiLocale] = useState<Localization>(enUS);
  const { locale } = useFormatter();
  const [theme, setTheme] = useState('dark');
  const { me, settings, tenantSettings }: {
    me: User;
    settings: PlatformSettings;
    tenantSettings: TenantSettingsOutput;
  } = useHelper((helper: LoggedHelper) => ({
    me: helper.getMe(),
    settings: helper.getPlatformSettings(),
    tenantSettings: helper.getTenantSettings(),
  }));

  useEffect(() => {
    const rawPlatformTheme = tenantSettings?.platform_theme || settings.platform_theme || 'dark';
    const rawUserTheme = me?.user_theme ?? 'default';
    const themeToSet = rawUserTheme !== 'default' ? rawUserTheme : rawPlatformTheme;
    document.body.setAttribute('data-theme', themeToSet);
    // The design system reads its light/dark tokens from a `.light` / `.dark`
    // class. It has to sit on <html>, not on a container: the library portals
    // its tooltips, submenu flyouts and dropdowns straight into <body>, so a
    // scoped class would leave those floating layers unthemed.
    document.documentElement.classList.remove('light', 'dark');
    document.documentElement.classList.add(themeToSet === 'light' ? 'light' : 'dark');
    setTheme(themeToSet);
  }, [settings, tenantSettings, me]);

  useEffect(() => {
    setMuiLocale(localeMap[locale as keyof typeof localeMap]);
  }, [locale]);

  // createTheme is expensive and a new theme object invalidates the style cache of the
  // whole subtree: only build the variant in use, and only when its inputs change.
  // The memo is keyed on the VALUES that feed createTheme, never on the settings
  // objects themselves: parameters/tenant-settings are re-fetched on page mount and on
  // SSE reconnect, and each fetch stores a new object identity even when nothing
  // changed. Rebuilding the theme for that re-rendered (blinked) the entire app and
  // refetched every dashboard widget once the fetches landed.
  const activeThemeConfig = theme === 'light'
    ? tenantSettings?.platform_light_theme ?? settings.platform_light_theme
    : tenantSettings?.platform_dark_theme ?? settings.platform_dark_theme;
  const activeThemeKey = [
    activeThemeConfig?.logo_url,
    activeThemeConfig?.logo_url_collapsed,
    activeThemeConfig?.background_color,
    activeThemeConfig?.paper_color,
    activeThemeConfig?.navigation_color,
    activeThemeConfig?.primary_color,
    activeThemeConfig?.secondary_color,
    activeThemeConfig?.accent_color,
  ].join('|');
  // A customer-configured `paper_color` must reach the design-system surfaces
  // (Paper), not only MUI's. The library's contract is explicit and measured
  // (Paper.tsx, "SURFACE COLOUR AND HOST THEMING"): a host re-declares
  // `--bg-elevation-default-layer-N`, the per-layer token. Re-declaring the
  // semantic alias `--bg-elevation-default` does NOTHING — every `.layer-N`
  // class re-declares that alias on the Paper element itself, so an inherited
  // value can never win. Paper's default elevation is 1, hence layer-1.
  // Cleared when the platform has no override, so the library's own token
  // stays in charge.
  useEffect(() => {
    const root = document.documentElement;
    if (activeThemeConfig?.paper_color) {
      root.style.setProperty('--bg-elevation-default-layer-1', activeThemeConfig.paper_color);
    } else {
      root.style.removeProperty('--bg-elevation-default-layer-1');
    }
  }, [activeThemeConfig?.paper_color]);

  const muiTheme = useMemo(() => {
    const buildTheme = theme === 'light' ? themeLight : themeDark;
    return createTheme(
      {
        spacing: scaleFactor,
        ...buildTheme(
          activeThemeConfig?.logo_url,
          activeThemeConfig?.logo_url_collapsed,
          activeThemeConfig?.background_color,
          activeThemeConfig?.paper_color,
          activeThemeConfig?.navigation_color,
          activeThemeConfig?.primary_color,
          activeThemeConfig?.secondary_color,
          activeThemeConfig?.accent_color,
        ),
      },
      muiLocale,
    );
  }, [theme, muiLocale, activeThemeKey]);
  return <ThemeProvider theme={muiTheme}>{children}</ThemeProvider>;
};

const ConnectedThemeProvider = AppThemeProvider;

export default ConnectedThemeProvider;
