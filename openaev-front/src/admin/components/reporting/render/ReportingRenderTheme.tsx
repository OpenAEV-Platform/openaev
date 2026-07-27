import { createTheme, ThemeProvider } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useMemo } from 'react';

import { type LoggedHelper } from '../../../../actions/helper';
import { scaleFactor } from '../../../../components/AppThemeProvider';
import themeDark from '../../../../components/ThemeDark';
import themeLight from '../../../../components/ThemeLight';
import { useHelper } from '../../../../store';
import { type PlatformSettings, type ReportingBranding, type TenantSettingsOutput } from '../../../../utils/api-types';

/**
 * Scoped MUI theme for the standalone report render page.
 *
 * The base palette is the exact platform theme (same builders and
 * tenant/platform theme colors as AppThemeProvider) so an unbranded report
 * looks like the platform; the report branding then overrides mode and the six
 * optional colors. The provider is SCOPED: it only affects the render subtree,
 * never the admin chrome.
 */

interface Props {
  branding?: ReportingBranding;
  children: ReactNode;
}

const ReportingRenderTheme: FunctionComponent<Props> = ({ branding, children }) => {
  const { settings, tenantSettings }: {
    settings: PlatformSettings;
    tenantSettings: TenantSettingsOutput;
  } = useHelper((helper: LoggedHelper) => ({
    settings: helper.getPlatformSettings(),
    tenantSettings: helper.getTenantSettings(),
  }));

  // Branding mode wins; otherwise follow the platform default (reports are a
  // platform artifact, not a per-user preference).
  const platformMode = tenantSettings?.platform_theme || settings?.platform_theme || 'dark';
  let mode = platformMode;
  if (branding?.theme_mode === 'LIGHT') mode = 'light';
  else if (branding?.theme_mode === 'DARK') mode = 'dark';

  const platformThemeConfig = mode === 'light'
    ? tenantSettings?.platform_light_theme ?? settings?.platform_light_theme
    : tenantSettings?.platform_dark_theme ?? settings?.platform_dark_theme;

  const themeKey = [
    mode,
    branding?.primary_color,
    branding?.secondary_color,
    branding?.accent_color,
    branding?.background_color,
    branding?.paper_color,
    branding?.text_color,
    platformThemeConfig?.logo_url,
    platformThemeConfig?.background_color,
    platformThemeConfig?.paper_color,
    platformThemeConfig?.navigation_color,
    platformThemeConfig?.primary_color,
    platformThemeConfig?.secondary_color,
    platformThemeConfig?.accent_color,
  ].join('|');

  const theme = useMemo(() => {
    const build = mode === 'light' ? themeLight : themeDark;
    return createTheme({
      spacing: scaleFactor,
      ...build(
        platformThemeConfig?.logo_url,
        platformThemeConfig?.logo_url_collapsed,
        branding?.background_color ?? platformThemeConfig?.background_color,
        branding?.paper_color ?? platformThemeConfig?.paper_color,
        platformThemeConfig?.navigation_color,
        branding?.primary_color ?? platformThemeConfig?.primary_color,
        branding?.secondary_color ?? platformThemeConfig?.secondary_color,
        branding?.accent_color ?? platformThemeConfig?.accent_color,
        // The theme builders default the text color when undefined.
        branding?.text_color ?? undefined,
      ),
    });
    // Keyed on the VALUES feeding createTheme (same rationale as
    // AppThemeProvider): settings objects change identity on refetch.
  }, [themeKey]);

  return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
};

export default ReportingRenderTheme;
