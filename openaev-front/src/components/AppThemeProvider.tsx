import { enUS, esES, frFR, type Localization, zhCN } from '@mui/material/locale';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { getMeSelector, getPlatformSettingsSelector } from '../actions/selectors';
import { useSelectorHelper } from '../store';
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
  const me = useSelectorHelper(getMeSelector);
  const settings = useSelectorHelper(getPlatformSettingsSelector);

  useEffect(() => {
    const rawPlatformTheme = settings?.platform_theme ?? 'dark';
    const rawUserTheme = me?.user_theme ?? 'default';
    const themeToSet = rawUserTheme !== 'default' ? rawUserTheme : rawPlatformTheme;
    document.body.setAttribute('data-theme', themeToSet);
    setTheme(themeToSet);
  }, [settings, me]);

  useEffect(() => {
    setMuiLocale(localeMap[locale as keyof typeof localeMap]);
  }, [locale]);

  const dark = settings?.platform_dark_theme;
  let muiTheme = createTheme(
    {
      spacing: scaleFactor,
      ...themeDark(
        dark?.logo_url,
        dark?.logo_url_collapsed,
        dark?.background_color,
        dark?.paper_color,
        dark?.navigation_color,
        dark?.primary_color,
        dark?.secondary_color,
        dark?.accent_color,
      ),
    },
    muiLocale,
  );
  if (theme === 'light') {
    const light = settings?.platform_light_theme;
    muiTheme = createTheme(
      {
        spacing: scaleFactor,
        ...themeLight(
          light?.logo_url,
          light?.logo_url_collapsed,
          light?.background_color,
          light?.paper_color,
          light?.navigation_color,
          light?.primary_color,
          light?.secondary_color,
          light?.accent_color,
        ),
      },
      muiLocale,
    );
  }
  return <ThemeProvider theme={muiTheme}>{children}</ThemeProvider>;
};

const ConnectedThemeProvider = AppThemeProvider;

export default ConnectedThemeProvider;
