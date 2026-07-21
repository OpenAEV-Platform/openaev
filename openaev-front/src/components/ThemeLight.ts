import { alpha, buttonClasses, darken, lighten, type ThemeOptions } from '@mui/material';

import LogoCollapsed from '../static/images/logo_light.png';
import LogoText from '../static/images/logo_text_light.png';
import { hexToRGB } from '../utils/Colors';
import { fileUri } from '../utils/Environment';
import { FONT_FAMILY_CODE, type LabelColor, LabelColorDict } from './Theme';

// Aligned with OpenCTI's light theme (opencti-front/src/components/ThemeLight.ts):
// same default palette, typography, and component overrides, so both platforms
// share a single visual language. OpenAEV-specific tokens (labelChipMap,
// xtmhub, widgets, background.code / paperInCard) are kept on top.
const EE_COLOR = '#00BD94';

export const THEME_LIGHT_DEFAULT_BACKGROUND = '#ececf2';
const THEME_LIGHT_DEFAULT_BODY_END_GRADIENT = '#F7F7F7';
const THEME_LIGHT_DEFAULT_PRIMARY = '#0015a8';
const THEME_LIGHT_DEFAULT_SECONDARY = '#00BD94';
const THEME_LIGHT_DEFAULT_ACCENT = '#dfdfdf';
const THEME_LIGHT_DEFAULT_PAPER = '#ffffff';
const THEME_LIGHT_DEFAULT_NAV = '#ffffff';
const THEME_LIGHT_DEFAULT_TEXT = '#18191B';
export const THEME_LIGHT_DIALOG_BACKGROUND = '#FFFFFF';

const getAppBodyGradientEndColor = (background: string | null): string => {
  if (background && background !== THEME_LIGHT_DEFAULT_BACKGROUND) {
    return lighten(background, 0.05);
  }
  return THEME_LIGHT_DEFAULT_BODY_END_GRADIENT;
};

const ThemeLight = (
  logo: string | null = null,
  logo_collapsed: string | null = null,
  background: string | null = null,
  paper: string | null = null,
  nav: string | null = null,
  primary: string | null = null,
  secondary: string | null = null,
  accent: string | null = null,
  text_color = THEME_LIGHT_DEFAULT_TEXT,
): ThemeOptions => ({
  logo: logo || fileUri(LogoText),
  logo_collapsed: logo_collapsed || fileUri(LogoCollapsed),
  borderRadius: 4,
  // OpenCTI-aligned top bar height (68px): every toolbar spacer in the app
  // follows it through theme.mixins.toolbar.
  mixins: { toolbar: { minHeight: 68 } },
  palette: {
    mode: 'light',
    common: {
      white: '#ffffff',
      black: '#000000',
      grey: '#494A50',
      lightGrey: '#AFB0B6',
    },
    error: {
      main: '#F14337',
      dark: '#881106',
    },
    warn: { main: '#E6700F' },
    dangerZone: {
      main: '#E51E10',
      light: '#F8958C',
      dark: '#881106',
      contrastText: '#000000',
    },
    success: {
      main: '#1CA55E',
      dark: '#0D7E39',
    },
    warning: { main: '#ed6c02' },
    primary: {
      main: primary || THEME_LIGHT_DEFAULT_PRIMARY,
      light: primary ? alpha(primary, 0.08) : '#7587FF',
    },
    secondary: { main: secondary || THEME_LIGHT_DEFAULT_SECONDARY },
    gradient: { main: '#00BD94' },
    border: {
      lightBackground: hexToRGB('#000000', 0.15),
      primary: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.3),
      secondary: '#C2C2C2',
      pagination: hexToRGB('#000000', 0.5),
      paper: hexToRGB('#000000', 0.12),
      main: '#D2D2D2',
    },
    pagination: { main: '#000000' },
    chip: { main: '#000000' },
    labelChipMap: new Map<string, LabelColor>([
      [
        LabelColorDict.Red, {
          backgroundColor: 'rgba(244, 67, 54, 0.08)',
          color: '#f44336',
        }], [
        LabelColorDict.Green, {
          backgroundColor: 'rgba(76, 175, 80, 0.08)',
          color: '#4caf50',
        }], [
        LabelColorDict.Orange, {
          backgroundColor: 'rgba(246,177,27,0.08)',
          color: '#f19710',
        }],
    ]),
    ai: {
      main: '#5E1AD5',
      light: '#D6C2FA',
      dark: '#3C108C',
      contrastText: '#000000',
      background: 'rgba(221, 225, 254, 0.94)',
    },
    ee: {
      main: EE_COLOR,
      background: hexToRGB(EE_COLOR, 0.2),
      lightBackground: hexToRGB(EE_COLOR, 0.08),
      contrastText: '#F2F2F3',
    },
    xtmhub: { main: '#00f1bd' },
    background: {
      default: background || THEME_LIGHT_DEFAULT_BACKGROUND,
      paper: paper || THEME_LIGHT_DEFAULT_PAPER,
      nav: nav || THEME_LIGHT_DEFAULT_NAV,
      accent: accent || THEME_LIGHT_DEFAULT_ACCENT,
      shadow: alpha('#000000', 0.15),
      // the only way for now to know if we should apply the paper color or not
      secondary: paper === THEME_LIGHT_DEFAULT_PAPER
        ? '#FFFFFF'
        : (paper ?? '#FFFFFF'),
      drawer: nav === THEME_LIGHT_DEFAULT_NAV
        ? '#FFFFFF'
        : (darken(nav ?? '#FFFFFF', 0.5)),
      disabled: '#DFDFDF',
      gradient: {
        start: background || THEME_LIGHT_DEFAULT_BACKGROUND,
        end: getAppBodyGradientEndColor(background),
      },
      code: accent || THEME_LIGHT_DEFAULT_ACCENT,
      paperInCard: '#f7f7f7',
    },
    // NOTE: unlike OpenCTI we deliberately keep MUI's muted text.secondary:
    // OpenAEV components use `text.secondary` pervasively for muted labels,
    // while OpenCTI reserves muting for `text.tertiary`.
    text: {
      tertiary: '#717172',
      light: '#494A50',
      disabled: '#6E7788',
    },
    leftBar: {
      header: { itemBackground: '#ECECF2' },
      popoverItem: '#ECECF2',
      hover: '#0015A81A',
      text: '#18191B',
    },
    severity: {
      critical: '#EE3838',
      high: '#E6700F',
      medium: '#E1B823',
      low: '#16AD34',
      info: '#1565c0',
      none: '#424242',
      default: '#DDE1FE',
    },
    designSystem: {
      primary: {
        main: '#0015A8',
        light: '#7587FF',
        dark: '#000842',
      },
      secondary: {
        main: '#00BD94',
        light: '#74E9CA',
        dark: '#0A8268',
      },
      destructive: {
        main: '#E51E10',
        light: '#F8958C',
        dark: '#881106',
      },
      ia: {
        main: '#5E1AD5',
        light: '#D6C2FA',
        dark: '#3C108C',
      },
      background: {
        main: '#ECECF2',
        bg1: '#F7F7F7',
        bg2: '#FFFFFF',
        bg3: '#E4E4E4',
        bg4: '#DDE1FE',
        disabled: '#DFDFDF',
      },
      border: {
        main: '#D2D2D2',
        border1: '#C2C2C2',
        border2: '#999797',
      },
      gradient: {
        background: 'linear-gradient(100.35deg, #ECECF2 0%, #F7F7F7 100%)',
        ia: 'linear-gradient(90deg, #3C108C 0.67%, #5E1AD5 100.67%)',
        focus: 'linear-gradient(90deg, #0015A8 -3.68%, #00BD94 106.62%)',
      },
      alert: {
        info: {
          primary: '#00719E',
          secondary: '#2AB3E0',
        },
        success: {
          primary: '#1CA55E',
          secondary: '#4CD990',
          tertiary: '#0D7E39',
        },
        alert: {
          primary: '#F2BE3A',
          secondary: '#F6CE6A',
        },
        warning: {
          primary: '#E6700F',
          secondary: '#F8C08C',
        },
        error: {
          primary: '#F14337',
          secondary: '#F8958C',
        },
      },
      tertiary: {
        grey: {
          400: '#95969D',
          700: '#494A50',
          800: '#313235',
        },
        blue: {
          500: '#0099CC',
          900: '#003242',
        },
        darkBlue: {
          300: '#7587FF',
          500: '#0F2DFF',
        },
        turquoise: {
          600: '#00BD94',
          800: '#005744',
        },
        green: {
          400: '#41E149',
          600: '#17AB1F',
          800: '#094E0B',
        },
        red: {
          100: '#FBCBC5',
          200: '#F8958C',
          400: '#F14337',
          500: '#E51E10',
          600: '#B8180A',
          700: '#881106',
        },
        orange: {
          400: '#F2933A',
          500: '#E6700F',
        },
        yellow: { 400: '#F2BE3A' },
      },
    },
    widgets: {
      securityDomains: {
        colors: {
          success: 'rgb(2,129,8)',
          intermediate: 'rgb(255 216 0)',
          warning: 'rgb(245, 166, 35)',
          failed: 'rgb(220, 81, 72)',
          pending: 'rgba(248,243,243,0.37)',
          unknown: 'rgba(73,72,72,0.37)',
        },
      },
    },
  },
  tag: { overflowColor: primary || THEME_LIGHT_DEFAULT_PRIMARY },
  typography: {
    fontFamily: '"IBM Plex Sans", sans-serif',
    body2: {
      fontSize: '0.8rem',
      lineHeight: '1.2rem',
      color: text_color,
    },
    body1: {
      fontSize: '0.9rem',
      color: text_color,
    },
    overline: {
      fontWeight: 500,
      color: text_color,
    },
    h1: {
      'margin': '0 0 10px 0',
      'padding': 0,
      'fontWeight': 400,
      'fontSize': 22,
      'fontFamily': '"Geologica", sans-serif',
      'color': text_color,
      'textTransform': 'lowercase',
      '&::first-letter': { textTransform: 'uppercase' },
    },
    h2: {
      'margin': '0 0 10px 0',
      'padding': 0,
      'fontWeight': 500,
      'fontSize': 16,
      'fontFamily': '"Geologica", sans-serif',
      'color': text_color,
      'textTransform': 'lowercase',
      '&::first-letter': { textTransform: 'uppercase' },
    },
    h3: {
      'margin': '0 0 10px 0',
      'padding': 0,
      'fontWeight': 400,
      'fontSize': 13,
      'fontFamily': '"Geologica", sans-serif',
      'color': text_color,
      'textTransform': 'lowercase',
      '&::first-letter': { textTransform: 'uppercase' },
    },
    h4: {
      'height': 15,
      'margin': '0 0 10px 0',
      'padding': 0,
      'fontSize': 12,
      'fontWeight': 500,
      'color': text_color,
      'textTransform': 'lowercase',
      '&::first-letter': { textTransform: 'uppercase' },
    },
    h5: {
      'fontWeight': 700,
      'fontSize': 16,
      'color': text_color,
      'fontFamily': '"Geologica", sans-serif',
      'textTransform': 'lowercase',
      '&::first-letter': { textTransform: 'uppercase' },
    },
    h6: {
      'fontWeight': 600,
      'fontSize': 14,
      'color': text_color,
      'fontFamily': '"Geologica", sans-serif',
      'textTransform': 'lowercase',
      '&::first-letter': { textTransform: 'uppercase' },
    },
    subtitle2: {
      'fontWeight': 400,
      'fontSize': 18,
      'color': text_color,
      'textTransform': 'lowercase',
      '&::first-letter': { textTransform: 'uppercase' },
    },
  },
  button: {
    sizes: {
      default: {
        height: '36px',
        padding: '8px 16px',
        minWidth: '36px',
        width: '36px',
        fontSize: '14px',
        fontWeight: 600,
        lineHeight: '21px',
        iconSize: '16px',
      },
      small: {
        height: '26px',
        padding: '4px 12px',
        minWidth: '26px',
        width: '26px',
        fontSize: '13px',
        fontWeight: 600,
        lineHeight: '21px',
        iconSize: '14px',
      },
    },
  },
  components: {
    MuiAccordion: { defaultProps: { slotProps: { transition: { unmountOnExit: true } } } },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          // Sentence-case buttons everywhere (aligned with OpenCTI), instead of
          // MUI's default ALL-CAPS. Labels render exactly as written.
          // Weight 600 matches OpenCTI's design-system button typography.
          'textTransform': 'none',
          'fontWeight': 600,
          [`&.${buttonClasses.outlined}.${buttonClasses.sizeSmall}`]: { padding: '4px 9px' },
          '&.icon-outlined': {
            'borderColor': hexToRGB('#000000', 0.15),
            'padding': 7,
            'minWidth': 0,
            '&:hover': {
              borderColor: hexToRGB('#000000', 0.15),
              backgroundColor: hexToRGB('#000000', 0.05),
            },
          },
        },
        // Outlined primary (used by every Cancel/dismiss button) mirrors OpenCTI's
        // "secondary" design-system button: neutral grey border + primary-colored
        // label, not a bright primary-colored border.
        outlinedPrimary: ({ theme }) => ({
          'borderColor': theme.palette.border.main,
          '&:hover': {
            borderColor: theme.palette.border.main,
            backgroundColor: alpha(theme.palette.primary.main, 0.15),
          },
        }),
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          backgroundImage: 'none',
          backgroundColor: paper === THEME_LIGHT_DEFAULT_PAPER
            ? THEME_LIGHT_DIALOG_BACKGROUND
            : (paper ?? THEME_LIGHT_DIALOG_BACKGROUND),
          borderRadius: 4,
        },
      },
    },
    MuiDialogTitle: { defaultProps: { variant: 'h5' } },
    MuiDialogActions: {
      styleOverrides: {
        root: ({ theme }) => ({
          // Aligned with OpenCTI: even gap between buttons, generous top gap from
          // the content, and matching right/bottom padding so buttons never sit
          // flush against the dialog edge.
          'gap': theme.spacing(1),
          'padding': theme.spacing(0, 3, 3, 3),
          'marginTop': theme.spacing(3),
          'marginLeft': 0,
          '& .MuiButton-root': { textTransform: 'none' },
          // Override the default margin-left
          '& > :not(style) ~ :not(style)': { marginLeft: 0 },
        }),
      },
    },
    MuiToggleButtonGroup: {
      defaultProps: { size: 'small' },
      styleOverrides: {
        root: {
          'height': 36,
          '& .MuiTouchRipple-root': { display: 'none' },
          '& .MuiToggleButton-root': {
            'border': '1px solid #D2D2D2',
            'color': primary,
            '&:focus-visible': {
              outline: 'none',
              boxShadow: '0 0 0 2px #74E9CA',
            },
            '&.Mui-selected': { backgroundColor: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.25) },
            '&:hover:not(.Mui-selected)': { backgroundColor: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.15) },
          },
        },
      },
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: { backgroundColor: 'rgba(0,0,0,0.7)' },
        arrow: { color: 'rgba(0,0,0,0.7)' },
        popper: {
          'textTransform': 'lowercase',
          '&::first-letter': { textTransform: 'uppercase' },
        },
      },
    },
    MuiFormControl: {
      defaultProps: { variant: 'standard' },
      styleOverrides: { root: { color: text_color } },
    },
    MuiTextField: {
      defaultProps: { variant: 'standard' },
      styleOverrides: {
        root: {
          'color': text_color,
          // Shrink = when at the top of the input in small size.
          '& .MuiFormLabel-root:not(.MuiInputLabel-shrink):not(.Mui-error)': { color: '#494A50' },
        },
      },
    },
    MuiSelect: {
      defaultProps: { variant: 'standard' },
      styleOverrides: {
        root: {
          'color': text_color,
          '& fieldset': { border: 'none' },
        },
        outlined: {
          backgroundColor: paper === THEME_LIGHT_DEFAULT_PAPER
            ? '#FFFFFF'
            : (paper ?? '#FFFFFF'),
        },
      },
    },
    MuiPaper: { styleOverrides: { root: { color: text_color } } },
    // Design-system icon buttons are squared (4px radius) - never MUI's
    // default circle/oval ripple.
    MuiIconButton: { styleOverrides: { root: { borderRadius: 4 } } },
    MuiCssBaseline: {
      styleOverrides: {
        html: {
          scrollbarColor: `${accent || THEME_LIGHT_DEFAULT_ACCENT} ${paper || THEME_LIGHT_DEFAULT_PAPER}`,
          scrollbarWidth: 'thin',
          background: `linear-gradient(100deg, ${background || THEME_LIGHT_DEFAULT_BACKGROUND} 0%, ${getAppBodyGradientEndColor(background)} 100%)`,
          backgroundAttachment: 'fixed',
          backgroundColor: background || THEME_LIGHT_DEFAULT_BACKGROUND,
        },
        body: {
          'background': `linear-gradient(100deg, ${background || THEME_LIGHT_DEFAULT_BACKGROUND} 0%, ${getAppBodyGradientEndColor(background)} 100%)`,
          'backgroundAttachment': 'fixed',
          'scrollbarColor': `${accent || THEME_LIGHT_DEFAULT_ACCENT} ${paper || THEME_LIGHT_DEFAULT_PAPER}`,
          'scrollbarWidth': 'thin',
          'html': { WebkitFontSmoothing: 'auto' },
          'a': { color: primary || THEME_LIGHT_DEFAULT_PRIMARY },
          'input:-webkit-autofill': {
            WebkitAnimation: 'autofill 0s forwards',
            animation: 'autofill 0s forwards',
            WebkitTextFillColor: '#000000 !important',
            caretColor: 'transparent !important',
            WebkitBoxShadow:
              '0 0 0 1000px rgba(4, 8, 17, 0.88) inset !important',
            borderTopLeftRadius: 'inherit',
            borderTopRightRadius: 'inherit',
          },
          'pre': {
            fontFamily: FONT_FAMILY_CODE,
            color: `${text_color} !important`,
            background: `${accent || THEME_LIGHT_DEFAULT_ACCENT} !important`,
            borderRadius: 4,
          },
          'pre.light': {
            fontFamily: FONT_FAMILY_CODE,
            background: `${nav || THEME_LIGHT_DEFAULT_NAV} !important`,
            borderRadius: 4,
          },
          'code': {
            fontFamily: FONT_FAMILY_CODE,
            color: `${text_color} !important`,
            background: `${accent || THEME_LIGHT_DEFAULT_ACCENT} !important`,
            padding: 3,
            fontSize: 12,
            fontWeight: 400,
            borderRadius: 4,
          },
          '.w-md-editor': {
            'boxShadow': 'none',
            'background': 'transparent',
            'borderBottom': '1px solid rgba(0, 0, 0, 0.87) !important',
            'transition': 'borderBottom .3s',
            '&:hover': { borderBottom: '2px solid #000000 !important' },
            '&:focus-within': { borderBottom: `2px solid ${primary || THEME_LIGHT_DEFAULT_PRIMARY} !important` },
          },
          '.error .w-md-editor': {
            'border': '0 !important',
            'borderBottom': '2px solid #F14337 !important',
            '&:hover': {
              border: '0 !important',
              borderBottom: '2px solid #F14337 !important',
            },
            '&:focus': {
              border: '0 !important',
              borderBottom: '2px solid #F14337 !important',
            },
          },
          '.w-md-editor-toolbar': {
            border: '0 !important',
            backgroundColor: 'transparent !important',
            color: `${text_color} !important`,
          },
          '.w-md-editor-toolbar li button': { color: `${text_color} !important` },
          '.w-md-editor-text textarea': {
            fontFamily: '"IBM Plex Sans", sans-serif',
            fontSize: 13,
            color: text_color,
          },
          '.w-md-editor-preview': { boxShadow: 'inset 1px 0 0 0 rgba(0, 0, 0, 0.2)' },
          '.wmde-markdown': {
            background: 'transparent',
            fontFamily: '"IBM Plex Sans", sans-serif',
            fontSize: 13,
            color: text_color,
          },
          '.wmde-markdown tr': { background: 'transparent !important' },
          '.react-grid-placeholder': { backgroundColor: `${accent || THEME_LIGHT_DEFAULT_ACCENT} !important` },
          '.react_time_range__track': {
            backgroundColor: 'rgba(1, 226, 255, 0.1) !important',
            borderLeft: '1px solid #00bcd4 !important',
            borderRight: '1px solid #00bcd4 !important',
          },
          '.react_time_range__handle_marker': { backgroundColor: '#00bcd4 !important' },
          '.leaflet-container': { backgroundColor: `${paper || THEME_LIGHT_DEFAULT_PAPER} !important` },
          '.react-grid-item .react-resizable-handle::after': {
            borderRight: '2px solid #AFB0B6 !important',
            borderBottom: '2px solid #AFB0B6 !important',
          },
        },
      },
    },
    // OpenCTI's light theme reuses white table borders here (invisible on a
    // light paper); we keep readable dark borders instead - intentional
    // deviation until upstream fixes it.
    MuiTableCell: {
      styleOverrides: {
        head: { borderBottom: '1px solid rgba(0, 0, 0, 0.15)' },
        body: {
          borderTop: '1px solid rgba(0, 0, 0, 0.15)',
          borderBottom: '1px solid rgba(0, 0, 0, 0.15)',
        },
      },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: {
          ':hover': { backgroundColor: 'rgba(0,0,0,0.04)' },
          '&.Mui-selected': {
            boxShadow: `2px 0 ${primary || THEME_LIGHT_DEFAULT_PRIMARY} inset`,
            backgroundColor: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.12),
          },
          '&.Mui-selected:hover': {
            boxShadow: `2px 0 ${primary || THEME_LIGHT_DEFAULT_PRIMARY} inset`,
            backgroundColor: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.16),
          },
        },
      },
    },
    MuiTypography: {
      styleOverrides: {
        root: {
          color: text_color,
          textTransform: 'none',
        },
      },
    },
    MuiInputBase: { styleOverrides: { root: { color: text_color } } },
    MuiChip: {
      styleOverrides: {
        root: {
          'color': text_color,
          'textTransform': 'lowercase',
          '&::first-letter': { textTransform: 'uppercase' },
        },
        label: {
          'textTransform': 'lowercase',
          '&::first-letter': { textTransform: 'uppercase' },
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          'textTransform': 'lowercase',
          'display': 'inline-block',
          '&::first-letter': { textTransform: 'uppercase' },
        },
      },
    },
    MuiFab: { styleOverrides: { root: { textTransform: 'none' } } },
    MuiAutocomplete: {
      styleOverrides: {
        root: {
          // Shrink = when at the top of the input in small size.
          '& .MuiFormLabel-root:not(.MuiInputLabel-shrink):not(.Mui-error)': { color: '#494A50' },
          '& .MuiOutlinedInput-root': {
            // the only way for now to know if we should apply the paper color or not
            'backgroundColor': paper === THEME_LIGHT_DEFAULT_PAPER
              ? '#FFFFFF'
              : (paper ?? '#FFFFFF'),
            '& fieldset': { borderColor: 'transparent' },
          },
        },
      },
    },
  },
});

export default ThemeLight;
