import { Box, Stack, type SxProps } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { type LoggedHelper } from '../../../actions/helper';
import logoFiligranBaselineDark from '../../../static/images/logo_filigran_baseline_dark.svg';
import logoFiligranBaselineLight from '../../../static/images/logo_filigran_baseline_light.svg';
import logoFiligranGradientDark from '../../../static/images/logo_filigran_gradient_dark.svg';
import logoFiligranGradientLight from '../../../static/images/logo_filigran_gradient_light.svg';
import logoDark from '../../../static/images/logo_text_dark.png';
import logoLight from '../../../static/images/logo_text_light.png';
import { useHelper } from '../../../store';
import { type PlatformSettings, type ThemeInput } from '../../../utils/api-types';
import { fileUri } from '../../../utils/Environment';
import { isNotEmptyField } from '../../../utils/utils';

// Login aside customization type, aligned with OpenCTI's getLoginAsideType:
// image > gradient > color > '' (default Filigran gradient + logo).
type LoginAsideType = 'image' | 'gradient' | 'color' | '';

const getLoginAsideType = (themeSettings?: ThemeInput | null): LoginAsideType => {
  if (isNotEmptyField(themeSettings?.login_aside_image)) return 'image';
  if (
    isNotEmptyField(themeSettings?.login_aside_gradient_start)
    && isNotEmptyField(themeSettings?.login_aside_gradient_end)
  ) return 'gradient';
  if (isNotEmptyField(themeSettings?.login_aside_color)) return 'color';
  return '';
};

// "Made by Filigran" baseline, bottom-left of the aside (hidden by whitemark).
const LogoBaseline = () => {
  const theme = useTheme();
  const logoBaseline = theme.palette.mode === 'dark'
    ? logoFiligranBaselineDark
    : logoFiligranBaselineLight;
  return (
    <img
      src={fileUri(logoBaseline)}
      alt="Made by Filigran logo"
      width={130}
      style={{
        userSelect: 'none',
        pointerEvents: 'none',
        position: 'absolute',
        bottom: theme.spacing(3),
        left: theme.spacing(3),
        zIndex: 2,
      }}
    />
  );
};

// Giant Filigran gradient logo bleeding off the aside's top-right corner
// (default aside decoration when no customization is set).
const LogoFiligran = () => {
  const theme = useTheme();
  const logoGradient = theme.palette.mode === 'dark'
    ? logoFiligranGradientDark
    : logoFiligranGradientLight;
  return (
    <img
      src={fileUri(logoGradient)}
      alt="Filigran Logo"
      style={{
        userSelect: 'none',
        pointerEvents: 'none',
        height: `calc(100% + ${theme.spacing(10)})`,
        position: 'absolute',
        top: theme.spacing(-5),
        right: theme.spacing(-5),
      }}
    />
  );
};

interface Props { children: ReactNode }

/**
 * Split-screen login layout, replicating OpenCTI's LoginLayout: a centered
 * form column on the left (platform logo + content) and a themable aside on
 * the right (custom image / gradient / color, or the default Filigran
 * gradient), with the "Made by Filigran" baseline unless whitemark is active.
 */
const LoginLayout: FunctionComponent<Props> = ({ children }) => {
  const theme = useTheme();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const themeSettings = theme.palette.mode === 'dark'
    ? settings.platform_dark_theme
    : settings.platform_light_theme;

  const isWhitemarkEnable = settings.platform_whitemark === 'true'
    && settings.platform_license?.license_is_validated === true;

  const loginAsideType = getLoginAsideType(themeSettings);

  const getAsideBackground = () => {
    if (loginAsideType === 'color') {
      return themeSettings?.login_aside_color;
    }
    if (loginAsideType === 'gradient') {
      return `linear-gradient(100deg, ${themeSettings?.login_aside_gradient_start} 0%, ${themeSettings?.login_aside_gradient_end} 100%)`;
    }
    if (loginAsideType === 'image') {
      return `url(${themeSettings?.login_aside_image})`;
    }
    // fallback to default (same values as OpenCTI)
    return theme.palette.mode === 'dark'
      ? 'linear-gradient(100deg, #050A14 0%, #0C1728 100%)'
      : 'linear-gradient(100deg, #EAEAED 0%, #FEFEFF 100%)';
  };

  const loginLogo = themeSettings?.logo_login_url;

  const contentSx: SxProps = {
    minWidth: 500,
    overflow: 'hidden',
    background: theme.palette.background.default,
    boxShadow: '8px 0px 9px 0px #0000002F',
    zIndex: 2,
  };

  const asideSx: SxProps = {
    background: getAsideBackground(),
    backgroundSize: loginAsideType === 'image' ? 'cover' : undefined,
    backgroundPosition: loginAsideType === 'image' ? 'center' : undefined,
    position: 'relative',
    overflow: 'hidden',
  };

  return (
    <Stack data-testid="login-page" direction="row" sx={{ height: '100vh' }}>
      <Stack
        flex={1}
        sx={contentSx}
        justifyContent="center"
        alignItems="center"
        gap={4}
      >
        <img
          src={loginLogo && loginLogo.length > 0
            ? loginLogo
            : fileUri(theme.palette.mode === 'dark' ? logoDark : logoLight)}
          alt="logo"
          width={180}
        />
        {children}
      </Stack>
      <Box flex={1} sx={asideSx}>
        {loginAsideType === '' && <LogoFiligran />}
        {!isWhitemarkEnable && <LogoBaseline />}
      </Box>
    </Stack>
  );
};

export default LoginLayout;
