import { enUS, esES, frFR, type Localization, zhCN } from '@mui/material/locale';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';

import { type LoggedHelper } from '../actions/helper';
import { useHelper } from '../store';
import { type PlatformSettings, type TenantSettingsOutput, type User } from '../utils/api-types';
import useFdsThemeScope, { type FdsCustomTheme, FdsThemeContext, type FdsThemeMode } from '../utils/hooks/useFdsThemeScope';
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
    activeThemeConfig?.text_color,
  ].join('|');
  // Single writer of the `.light` / `.dark` class and of every customer colour the
  // library reads: the MUI palette does not drive CSS custom properties, so a
  // colour that is not written there keeps the library's own value.
  const mode: FdsThemeMode = theme === 'light' ? 'light' : 'dark';
  const custom: FdsCustomTheme = useMemo(() => ({
    background: activeThemeConfig?.background_color,
    paper: activeThemeConfig?.paper_color,
    nav: activeThemeConfig?.navigation_color,
    primary: activeThemeConfig?.primary_color,
    secondary: activeThemeConfig?.secondary_color,
    accent: activeThemeConfig?.accent_color,
    text: activeThemeConfig?.text_color,
  }), [activeThemeKey]);
  useFdsThemeScope(mode, custom);
  const fdsTheme = useMemo(() => ({
    mode,
    custom,
  }), [mode, custom]);

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
          activeThemeConfig?.text_color || undefined,
        ),
      },
      muiLocale,
    );
  }, [theme, muiLocale, activeThemeKey]);
  return (
    <FdsThemeContext.Provider value={fdsTheme}>
      <ThemeProvider theme={muiTheme}>{children}</ThemeProvider>
    </FdsThemeContext.Provider>
  );
};

const ConnectedThemeProvider = AppThemeProvider;

export default ConnectedThemeProvider;
