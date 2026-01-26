import { buttonClasses, type ThemeOptions } from '@mui/material';

import LogoCollapsed from '../static/images/logo_light.png';
import LogoText from '../static/images/logo_text_light.png';
import { hexToRGB } from '../utils/Colors';
import { fileUri } from '../utils/Environment';
import { FONT_FAMILY_CODE, type LabelColor, LabelColorDict } from './Theme';

const EE_COLOR = '#0c7e69';

export const THEME_LIGHT_DEFAULT_BACKGROUND = '#f8f8f8';
const THEME_LIGHT_DEFAULT_PRIMARY = '#001bda';
const THEME_LIGHT_DEFAULT_SECONDARY = '#0c7e69';
const THEME_LIGHT_DEFAULT_ACCENT = '#dfdfdf';
const THEME_LIGHT_DEFAULT_PAPER = '#ffffff';
const THEME_LIGHT_DEFAULT_NAV = '#ffffff';

const ThemeLight = (
  logo: string | null = null,
  logo_collapsed: string | null = null,
  background: string | null = null,
  paper: string | null = null,
  nav: string | null = null,
  primary: string | null = null,
  secondary: string | null = null,
  accent: string | null = null,
  text_color = 'rgba(0, 0, 0, 0.87)',
): ThemeOptions => ({
  logo: logo || fileUri(LogoText),
  logo_collapsed: logo_collapsed || fileUri(LogoCollapsed),
  borderRadius: 4,
  palette: {
    mode: 'light',
    common: {
      white: '#ffffff',
      black: '#000000',
      grey: '#494A50',
      lightGrey: 'rgba(0, 0, 0, 0.6)',
    },
    error: {
      main: '#f44336',
      dark: '#c62828',
    },
    warn: { main: '#ffa726' },
    dangerZone: {
      main: '#f6685e',
      light: '#fbc2be',
      dark: '#d1584f',
      contrastText: '#000000',
    },
    success: { main: '#03a847' },
    warning: { main: '#ed6c02' },
    primary: { main: primary || THEME_LIGHT_DEFAULT_PRIMARY },
    secondary: { main: secondary || THEME_LIGHT_DEFAULT_SECONDARY },
    gradient: { main: '#00f1bd' },
    border: {
      lightBackground: hexToRGB('#000000', 0.15),
      primary: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.3),
      secondary: hexToRGB(secondary || THEME_LIGHT_DEFAULT_SECONDARY, 0.3),
      pagination: hexToRGB('#000000', 0.5),
      paper: hexToRGB('#000000', 0.12),
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
      main: '#9c27b0',
      light: '#ba68c8',
      dark: '#7b1fa2',
      contrastText: '#000000',
    },
    ee: {
      main: EE_COLOR,
      background: hexToRGB(EE_COLOR, 0.2),
      lightBackground: hexToRGB(EE_COLOR, 0.08),
      contrastText: '#ffffff',
    },
    xtmhub: { main: '#00f1bd' },
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
    background: {
      default: background || THEME_LIGHT_DEFAULT_BACKGROUND,
      paper: paper || THEME_LIGHT_DEFAULT_PAPER,
      nav: nav || THEME_LIGHT_DEFAULT_NAV,
      accent: accent || '#d3eaff',
      shadow: 'rgba(0, 0, 0, .15)',
      code: accent || THEME_LIGHT_DEFAULT_ACCENT,
      paperInCard: '#f7f7f7',
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
      margin: '0 0 10px 0',
      padding: 0,
      fontWeight: 400,
      fontSize: 22,
      fontFamily: '"Geologica", sans-serif',
      color: text_color,
    },
    h2: {
      margin: '0 0 10px 0',
      padding: 0,
      fontWeight: 500,
      fontSize: 16,
      textTransform: 'uppercase',
      fontFamily: '"Geologica", sans-serif',
      color: text_color,
    },
    h3: {
      margin: '0 0 10px 0',
      padding: 0,
      color: text_color,
      fontWeight: 400,
      fontSize: 13,
      fontFamily: '"Geologica", sans-serif',
    },
    h4: {
      height: 15,
      margin: '0 0 10px 0',
      padding: 0,
      textTransform: 'uppercase',
      fontSize: 12,
      fontWeight: 500,
      color: text_color,
    },
    h5: {
      fontWeight: 400,
      fontSize: 13,
      textTransform: 'uppercase',
      marginTop: -4,
      color: text_color,
    },
    h6: {
      fontWeight: 400,
      fontSize: 18,
      color: text_color,
      fontFamily: '"Geologica", sans-serif',
    },
    subtitle2: {
      fontWeight: 400,
      fontSize: 18,
      color: text_color,
    },
  },
  components: {
    MuiAccordion: { defaultProps: { slotProps: { transition: { unmountOnExit: true } } } },
    MuiButton: {
      styleOverrides: {
        root: {
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
      },
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: { backgroundColor: 'rgba(0,0,0,0.7)' },
        arrow: { color: 'rgba(0,0,0,0.7)' },
      },
    },
    MuiFormControl: {
      defaultProps: { variant: 'standard' },
      styleOverrides: { root: { color: text_color } },
    },
    MuiTextField: {
      defaultProps: { variant: 'standard' },
      styleOverrides: { root: { color: text_color } },
    },
    MuiSelect: {
      defaultProps: { variant: 'standard' },
      styleOverrides: { root: { color: text_color } },
    },
    MuiPaper: { styleOverrides: { root: { color: text_color } } },
    MuiCssBaseline: {
      styleOverrides: {
        html: {
          scrollbarColor: `${accent || THEME_LIGHT_DEFAULT_ACCENT} ${paper || THEME_LIGHT_DEFAULT_PAPER}`,
          scrollbarWidth: 'thin',
        },
        body: {
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
            'borderBottom': '2px solid #f44336 !important',
            '&:hover': {
              border: '0 !important',
              borderBottom: '2px solid #f44336 !important',
            },
            '&:focus': {
              border: '0 !important',
              borderBottom: '2px solid #f44336 !important',
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
            borderRight: '2px solid rgba(0, 0, 0, 0.6) !important',
            borderBottom: '2px solid rgba(0, 0, 0, 0.6) !important',
          },
        },
      },
    },
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
    MuiTypography: { styleOverrides: { root: { color: text_color } } },
    MuiInputBase: { styleOverrides: { root: { color: text_color } } },
    MuiChip: { styleOverrides: { root: { color: text_color } } },
  },
});

export default ThemeLight;
