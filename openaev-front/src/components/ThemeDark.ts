import { alpha, darken, lighten, type ThemeOptions } from '@mui/material';
import { buttonClasses } from '@mui/material';

import LogoCollapsed from '../static/images/logo_dark.png';
import LogoText from '../static/images/logo_text_dark.png';
import { hexToRGB } from '../utils/Colors';
import { fileUri } from '../utils/Environment';
import { type LabelColor, LabelColorDict } from './Theme';

const EE_COLOR = '#00f18d';

export const THEME_DARK_DEFAULT_BACKGROUND = '#070d19';
const THEME_DARK_DEFAULT_BODY_END_GRADIENT = '#08101D';
export const THEME_DARK_DEFAULT_PRIMARY = '#0fbcff';
export const THEME_DARK_DEFAULT_SECONDARY = '#00f18d';
export const THEME_DARK_DEFAULT_ACCENT = '#0f1e38';
export const THEME_DARK_DEFAULT_PAPER = '#09101e';
export const THEME_DARK_DEFAULT_TEXT = '#F2F2F3';
const THEME_DARK_DEFAULT_NAV = '#070d19';
export const THEME_DARK_DIALOG_BACKGROUND = '#0F1D34';

const getAppBodyGradientEndColor = (background: string | null): string => {
  if (background && background !== THEME_DARK_DEFAULT_BACKGROUND) {
    return lighten(background, 0.05);
  }
  return THEME_DARK_DEFAULT_BODY_END_GRADIENT;
};

const ThemeDark = (
  logo: string | null = null,
  logo_collapsed: string | null = null,
  background: string | null = null,
  paper: string | null = null,
  nav: string | null = null,
  primary: string | null = null,
  secondary: string | null = null,
  accent: string | null = null,
  text_color = THEME_DARK_DEFAULT_TEXT,
): ThemeOptions => ({
  logo: logo || fileUri(LogoText),
  logo_collapsed: logo_collapsed || fileUri(LogoCollapsed),
  borderRadius: 4,
  palette: {
    mode: 'dark',
    common: {
      white: '#ffffff',
      grey: '#95969D',
      lightGrey: '#E4E5E7',
    },
    error: {
      main: '#F14337',
      dark: '#881106',
    },
    warn: { main: '#E6700F' },
    dangerZone: {
      main: '#F44336',
      light: '#F8958C',
      dark: '#881106',
      contrastText: '#000000',
    },
    success: {
      main: '#17AB1F',
      dark: '#094E0B',
    },
    primary: {
      main: primary || THEME_DARK_DEFAULT_PRIMARY,
      light: primary ? alpha(primary, 0.08) : '#B2ECFF',
    },
    secondary: { main: secondary || THEME_DARK_DEFAULT_SECONDARY },
    gradient: { main: '#00f18d' },
    border: {
      primary: hexToRGB((primary || THEME_DARK_DEFAULT_PRIMARY), 0.3),
      secondary: '#424751',
      pagination: hexToRGB('#ffffff', 0.5),
      paper: hexToRGB('#ffffff', 0.12),
      main: '#252A35',
    },
    pagination: { main: '#ffffff' },
    chip: { main: '#ffffff' },
    ai: {
      main: '#B286FF',
      light: '#D6C2FA',
      dark: '#5E1AD5',
      contrastText: '#000000',
      background: 'rgba(28, 47, 73, 0.94)',
    },
    ee: {
      main: EE_COLOR,
      contrastText: THEME_DARK_DEFAULT_TEXT,
      background: hexToRGB(EE_COLOR, 0.2),
      lightBackground: hexToRGB(EE_COLOR, 0.08),
    },
    xtmhub: { main: '#00f1bd' },
    labelChipMap: new Map<string, LabelColor>([
      [LabelColorDict.Red, {
        backgroundColor: 'rgba(241, 67, 55, 0.2)',
        color: '#F14337',
      }],
      [LabelColorDict.Green, {
        backgroundColor: 'rgba(23, 171, 31, 0.2)',
        color: '#17AB1F',
      }],
      [LabelColorDict.Orange, {
        backgroundColor: 'rgba(230, 112, 15, 0.2)',
        color: '#E6700F',
      }],
    ]),
    widgets: {
      securityDomains: {
        colors: {
          success: 'rgb(2,129,8)',
          intermediate: 'rgb(255 216 0)',
          warning: 'rgb(245, 166, 35)',
          failed: 'rgb(220, 81, 72)',
          pending: 'rgba(248,243,243,0.74)',
          unknown: 'rgba(73,72,72,0.37)',
        },
      },
    },
    background: {
      default: background || THEME_DARK_DEFAULT_BACKGROUND,
      paper: paper || THEME_DARK_DEFAULT_PAPER,
      nav: nav || THEME_DARK_DEFAULT_NAV,
      accent: accent || THEME_DARK_DEFAULT_ACCENT,
      shadow: 'rgba(200, 200, 200, 0.15)',
      secondary: paper === THEME_DARK_DEFAULT_PAPER
        ? '#0C1524'
        : (paper ?? '#0C1524'),
      drawer: nav === THEME_DARK_DEFAULT_NAV
        ? '#0f1d34'
        : (darken(nav ?? '#0f1d34', 0.5)),
      disabled: '#363B46',
      gradient: {
        start: background || THEME_DARK_DEFAULT_BACKGROUND,
        end: getAppBodyGradientEndColor(background),
      },
      code: accent || THEME_DARK_DEFAULT_ACCENT,
      paperInCard: paper || THEME_DARK_DEFAULT_PAPER,
    },
    text: {
      secondary: THEME_DARK_DEFAULT_TEXT,
      tertiary: '#848592',
      light: '#AFB0B6',
      disabled: '#75829A',
    },
    leftBar: {
      header: { itemBackground: '#253348' },
      popoverItem: '#070D19',
      hover: '#253348',
      text: '#F2F2F3',
    },
    severity: {
      critical: '#EE3838',
      high: '#E6700F',
      medium: '#E1B823',
      low: '#16AD34',
      info: '#1565c0',
      none: '#424242',
      default: '#1C2F49',
    },
    designSystem: {
      primary: {
        main: '#0FBCFF',
        light: '#B2ECFF',
        dark: '#007399',
      },
      secondary: {
        main: '#00F1BD',
        light: '#BDFFED',
        dark: '#009474',
      },
      destructive: {
        main: '#F44336',
        light: '#F8958C',
        dark: '#881106',
      },
      ia: {
        main: '#B286FF',
        light: '#D6C2FA',
        dark: '#5E1AD5',
      },
      background: {
        main: '#070D19',
        bg1: '#0C1524',
        bg2: '#0D182A',
        bg3: '#253348',
        bg4: '#1C2F49',
        disabled: '#363B46',
      },
      border: {
        main: '#2B3447',
        border1: '#424751',
        border2: '#1C253A',
      },
      gradient: {
        background: 'linear-gradient(100.35deg, #070D19 0%, #08101d 100%)',
        ia: 'linear-gradient(90deg, #D6C2FA 0.67%, #B286FF 100.67%)',
        focus: 'linear-gradient(90deg, #0FBCFF -3.68%, #00F1BD 106.62%)',
      },
      alert: {
        info: {
          primary: '#4DCCFF',
          secondary: '#004C66',
        },
        success: {
          primary: '#17AB1F',
          secondary: '#094E0B',
          tertiary: '#75F8B9',
        },
        alert: {
          primary: '#F2BE3A',
          secondary: '#573E05',
        },
        warning: {
          primary: '#E6700F',
          secondary: '#884106',
        },
        error: {
          primary: '#F14337',
          secondary: '#881106',
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
  },
  tag: { overflowColor: primary || THEME_DARK_DEFAULT_PRIMARY },
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
  components: {
    MuiAccordion: { defaultProps: { slotProps: { transition: { unmountOnExit: true } } } },
    MuiIconButton: {
      styleOverrides: {
        root: {
          borderRadius: 4,
          padding: 6,
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          'textTransform': 'none',
          'fontFamily': '"IBM Plex Sans", sans-serif',
          'fontWeight': 600,
          'fontSize': '14px',
          'lineHeight': '21px',
          'borderRadius': 4,
          [`&.${buttonClasses.outlined}.${buttonClasses.sizeSmall}`]: { padding: '4px 9px' },
          '&.icon-outlined': {
            'borderColor': hexToRGB('#ffffff', 0.15),
            'padding': 7,
            'minWidth': 0,
            '&:hover': {
              borderColor: hexToRGB('#ffffff', 0.15),
              backgroundColor: hexToRGB('#ffffff', 0.05),
            },
          },
        },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          backgroundImage: 'none',
          backgroundColor: paper === THEME_DARK_DEFAULT_PAPER
            ? '#0F1D34'
            : (paper ?? '#0F1D34'),
          borderRadius: 4,
        },
      },
    },
    MuiDialogTitle: { defaultProps: { variant: 'h5' } },
    MuiDialogActions: {
      styleOverrides: {
        root: ({ theme }) => ({
          'gap': theme.spacing(1),
          'padding': theme.spacing(2, 3),
          'marginTop': theme.spacing(2),
          'marginLeft': 0,
          '& .MuiButton-root': { textTransform: 'none' },
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
            'border': '1px solid #2B3447',
            'color': primary,
            '&:focus-visible': {
              outline: 'none',
              boxShadow: '0 0 0 2px #BDFFED',
            },
            '&.Mui-selected': { backgroundColor: hexToRGB(primary || THEME_DARK_DEFAULT_PRIMARY, 0.25) },
            '&:hover:not(.Mui-selected)': { backgroundColor: hexToRGB(primary || THEME_DARK_DEFAULT_PRIMARY, 0.15) },
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
          '& .MuiFormLabel-root:not(.MuiInputLabel-shrink):not(.Mui-error)': { color: '#AFB0B6' },
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
          backgroundColor: paper === THEME_DARK_DEFAULT_PAPER
            ? '#0C1524'
            : (paper ?? '#0C1524'),
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { color: text_color },
        outlined: {
          background: paper === THEME_DARK_DEFAULT_PAPER
            ? '#0C1524'
            : (paper ?? '#0C1524'),
          borderColor: 'transparent',
        },
      },
    },
    MuiCssBaseline: {
      styleOverrides: {
        html: {
          scrollbarColor: `${background || THEME_DARK_DEFAULT_BACKGROUND} ${accent || THEME_DARK_DEFAULT_ACCENT}`,
          scrollbarWidth: 'thin',
          background: `linear-gradient(100deg, ${background || THEME_DARK_DEFAULT_BACKGROUND} 0%, ${getAppBodyGradientEndColor(background)} 100%)`,
          backgroundAttachment: 'fixed',
          backgroundColor: background || THEME_DARK_DEFAULT_BACKGROUND,
        },
        body: {
          'background': `linear-gradient(100deg, ${background || THEME_DARK_DEFAULT_BACKGROUND} 0%, ${getAppBodyGradientEndColor(background)} 100%)`,
          'backgroundAttachment': 'fixed',
          'scrollbarColor': `${background || THEME_DARK_DEFAULT_BACKGROUND} ${accent || THEME_DARK_DEFAULT_ACCENT}`,
          'scrollbarWidth': 'thin',
          'html': { WebkitFontSmoothing: 'auto' },
          'a': { color: primary || THEME_DARK_DEFAULT_PRIMARY },
          'input:-webkit-autofill': {
            WebkitAnimation: 'autofill 0s forwards',
            animation: 'autofill 0s forwards',
            WebkitTextFillColor: '#ffffff !important',
            caretColor: 'transparent !important',
            WebkitBoxShadow:
              '0 0 0 1000px rgba(4, 8, 17, 0.88) inset !important',
            borderTopLeftRadius: 'inherit',
            borderTopRightRadius: 'inherit',
          },
          'pre': {
            fontFamily: 'Consolas, monaco, monospace',
            color: `${text_color} !important`,
            background: `${accent || THEME_DARK_DEFAULT_ACCENT} !important`,
            borderRadius: 4,
          },
          'pre.light': {
            fontFamily: 'Consolas, monaco, monospace',
            background: `${nav || THEME_DARK_DEFAULT_NAV} !important`,
            borderRadius: 4,
          },
          'code': {
            fontFamily: 'Consolas, monaco, monospace',
            color: `${text_color} !important`,
            background: `${accent || THEME_DARK_DEFAULT_ACCENT} !important`,
            padding: 3,
            fontSize: 12,
            fontWeight: 400,
            borderRadius: 4,
          },
          '.w-md-editor': {
            'boxShadow': 'none',
            'background': 'transparent',
            'borderBottom': '1px solid rgba(255, 255, 255, 0.7) !important',
            'transition': 'borderBottom .3s',
            '&:hover': { borderBottom: '2px solid #ffffff !important' },
            '&:focus-within': { borderBottom: `2px solid ${primary || THEME_DARK_DEFAULT_PRIMARY} !important` },
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
          '.w-md-editor-preview': { boxShadow: 'inset 1px 0 0 0 rgba(255, 255, 255, 0.5)' },
          '.wmde-markdown': {
            background: 'transparent',
            fontFamily: '"IBM Plex Sans", sans-serif',
            fontSize: 13,
            color: text_color,
          },
          '.wmde-markdown tr': { background: 'transparent !important' },
          '.react-grid-placeholder': { backgroundColor: `${accent || THEME_DARK_DEFAULT_ACCENT} !important` },
          '.react_time_range__track': {
            backgroundColor: 'rgba(1, 226, 255, 0.1) !important',
            borderLeft: '1px solid #00bcd4 !important',
            borderRight: '1px solid #00bcd4 !important',
          },
          '.react_time_range__handle_marker': { backgroundColor: '#00bcd4 !important' },
          '.leaflet-container': { backgroundColor: `${paper || THEME_DARK_DEFAULT_PAPER} !important` },
          '.react-grid-item .react-resizable-handle::after': {
            borderRight: '2px solid rgba(255, 255, 255, 0.4) !important',
            borderBottom: '2px solid rgba(255, 255, 255, 0.4) !important',
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        head: { borderBottom: '1px solid rgba(255, 255, 255, 0.15)' },
        body: {
          borderTop: '1px solid rgba(255, 255, 255, 0.15)',
          borderBottom: '1px solid rgba(255, 255, 255, 0.15)',
        },
      },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: {
          '&.Mui-selected': {
            boxShadow: `2px 0 ${primary || THEME_DARK_DEFAULT_PRIMARY} inset`,
            backgroundColor: `${hexToRGB(primary || THEME_DARK_DEFAULT_PRIMARY, 0.24)}`,
          },
          '&.Mui-selected:hover': {
            boxShadow: `2px 0 ${primary || THEME_DARK_DEFAULT_PRIMARY} inset`,
            backgroundColor: `${hexToRGB(primary || THEME_DARK_DEFAULT_PRIMARY, 0.32)}`,
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
          '& .MuiFormLabel-root:not(.MuiInputLabel-shrink):not(.Mui-error)': { color: '#AFB0B6' },
          '& .MuiOutlinedInput-root': {
            'backgroundColor': paper === THEME_DARK_DEFAULT_PAPER
              ? '#0C1524'
              : (paper ?? '#0C1524'),
            '& fieldset': { borderColor: 'transparent' },
          },
        },
      },
    },
  },
});

export default ThemeDark;
