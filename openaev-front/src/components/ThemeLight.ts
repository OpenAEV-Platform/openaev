import { type ThemeOptions } from '@mui/material';

import LogoCollapsed from '../static/images/logo_light.png';
import LogoText from '../static/images/logo_text_light.png';
import { hexToRGB } from '../utils/Colors';
import { fileUri } from '../utils/Environment';
import { type LabelColor, LabelColorDict } from './Theme';

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
): ThemeOptions => ({
  logo: logo || fileUri(LogoText),
  logo_collapsed: logo_collapsed || fileUri(LogoCollapsed),
  borderRadius: 4,
  palette: {
    mode: 'light',
    common: { white: '#ffffff' },
    error: {
      main: '#f44336',
      dark: '#c62828',
    },
    success: { main: '#03a847' },
    warning: { main: '#ed6c02' },
    primary: { main: primary || THEME_LIGHT_DEFAULT_PRIMARY },
    secondary: { main: secondary || THEME_LIGHT_DEFAULT_SECONDARY },
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
    background: {
      default: background || THEME_LIGHT_DEFAULT_BACKGROUND,
      paper: paper || THEME_LIGHT_DEFAULT_PAPER,
      nav: nav || THEME_LIGHT_DEFAULT_NAV,
      accent: accent || '#d3eaff',
      shadow: 'rgba(0, 0, 0, .05)',
      code: accent || THEME_LIGHT_DEFAULT_ACCENT,
      paperInCard: '#f7f7f7',
    },
  },
  typography: {
    fontFamily: '"IBM Plex Sans", sans-serif',
    body2: { fontSize: '0.8rem' },
    body1: { fontSize: '0.9rem' },
    overline: { fontWeight: 500 },
    h1: {
      margin: '0 0 10px 0',
      padding: 0,
      fontWeight: 400,
      fontSize: 22,
      fontFamily: '"Geologica", sans-serif',
    },
    h2: {
      margin: '0 0 10px 0',
      padding: 0,
      fontWeight: 500,
      fontSize: 16,
      textTransform: 'uppercase',
      fontFamily: '"Geologica", sans-serif',
    },
    h3: {
      margin: '0 0 10px 0',
      padding: 0,
      color: '#757575',
      fontWeight: 400,
      fontSize: 13,
      fontFamily: '"Geologica", sans-serif',
    },
    h4: {
      margin: '0 0 10px 0',
      padding: 0,
      textTransform: 'uppercase',
      fontSize: 12,
      fontWeight: 500,
      color: '#505050',
    },
    h5: {
      fontWeight: 400,
      fontSize: 13,
      textTransform: 'uppercase',
      marginTop: -4,
    },
    h6: {
      fontWeight: 400,
      fontSize: 18,
      color: primary || THEME_LIGHT_DEFAULT_PRIMARY,
      fontFamily: '"Geologica", sans-serif',
    },
    subtitle2: {
      fontWeight: 400,
      fontSize: 18,
      color: 'rgba(0, 0, 0, 0.87)',
    },
  },
  components: {
    MuiAccordion: { defaultProps: { TransitionProps: { unmountOnExit: true } } },
    MuiTooltip: {
      styleOverrides: {
        tooltip: { backgroundColor: 'rgba(0,0,0,0.7)' },
        arrow: { color: 'rgba(0,0,0,0.7)' },
      },
    },
    MuiFormControl: { defaultProps: { variant: 'standard' } },
    MuiTextField: { defaultProps: { variant: 'standard' } },
    MuiSelect: { defaultProps: { variant: 'standard' } },
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
            fontFamily: 'Consolas, monaco, monospace',
            color: '#000000 !important',
            background: `${accent || THEME_LIGHT_DEFAULT_ACCENT} !important`,
          },
          'code': {
            fontFamily: 'Consolas, monaco, monospace',
            color: '#000000 !important',
            background: `${accent || THEME_LIGHT_DEFAULT_ACCENT} !important`,
            padding: 3,
            fontSize: 12,
            fontWeight: 400,
          },
          '.w-md-editor': {
            'boxShadow': 'none',
            'background': 'transparent',
            'borderBottom': '1px solid rgba(0, 0, 0, 0.87) !important',
            'transition': 'borderBottom .3s',
            '&:hover': { borderBottom: '2px solid #000000 !important' },
            '&:focus-within': { borderBottom: `2px solid #${primary || THEME_LIGHT_DEFAULT_PRIMARY} !important` },
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
            color: '#000000 !important',
          },
          '.w-md-editor-toolbar li button': { color: '#000000 !important' },
          '.w-md-editor-text textarea': {
            fontFamily: '"IBM Plex Sans", sans-serif',
            fontSize: 13,
            color: '#000000',
          },
          '.w-md-editor-preview': { boxShadow: 'inset 1px 0 0 0 rgba(0, 0, 0, 0.2)' },
          '.wmde-markdown': {
            background: 'transparent',
            fontFamily: '"IBM Plex Sans", sans-serif',
            fontSize: 13,
            color: '#000000',
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
          ':hover': { backgroundColor: 'rgba(0,0,0,0.04)' },
          '&.Mui-selected': {
            boxShadow: `2px 0 ${primary || THEME_LIGHT_DEFAULT_PRIMARY} inset`,
            backgroundColor: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.08),
          },
          '&.Mui-selected:hover': {
            boxShadow: `2px 0 ${primary || THEME_LIGHT_DEFAULT_PRIMARY} inset`,
            backgroundColor: hexToRGB(primary || THEME_LIGHT_DEFAULT_PRIMARY, 0.12),
          },
        },
      },
    },
  },
});

export default ThemeLight;
