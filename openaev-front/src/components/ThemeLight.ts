import { alpha, buttonClasses, darken, lighten, type ThemeOptions } from '@mui/material';
// Type-only: declares the MUI X picker keys used in `components` below.
import type {} from '@mui/x-date-pickers/themeAugmentation';

import LogoCollapsed from '../static/images/logo_light.png';
import LogoText from '../static/images/logo_text_light.png';
import { hexToRGB } from '../utils/Colors';
import { fileUri } from '../utils/Environment';
import { FDS } from './fds-tokens.generated';
import { FONT_FAMILY_CODE, INLINE_CONTROL_HEIGHT, type LabelColor, LabelColorDict } from './Theme';

// Aligned with OpenCTI's light theme (opencti-front/src/components/ThemeLight.ts):
// same default palette, typography, and component overrides, so both platforms
// share a single visual language. OpenAEV-specific tokens (labelChipMap,
// xtmhub, widgets, background.code / paperInCard) are kept on top.
const EE_COLOR = FDS.colors.light['--color-filigran-tonic-primary'];

export const THEME_LIGHT_DEFAULT_BACKGROUND = FDS.colors.light['--bg-elevation-default-layer-0'];
const THEME_LIGHT_DEFAULT_BODY_END_GRADIENT = FDS.colors.light['--bg-elevation-default-layer-0-gradient'];
const THEME_LIGHT_DEFAULT_PRIMARY = FDS.scalars['--darkblue-600'];
const THEME_LIGHT_DEFAULT_SECONDARY = EE_COLOR;
const THEME_LIGHT_DEFAULT_ACCENT = FDS.colors.light['--bg-elevation-default-layer-3'];
const THEME_LIGHT_DEFAULT_PAPER = FDS.colors.light['--bg-elevation-default-layer-1'];
// NAV intentionally left as a raw literal — see TOKEN-MAPPING.md "7th item"
// flag: this specific, visibly-notable white -> #f2f2f3 shift is not signed off.
const THEME_LIGHT_DEFAULT_NAV = '#ffffff';
const THEME_LIGHT_DEFAULT_TEXT = '#18191B';
// Modal surface: the design system's layer-2 elevation, the light counterpart of
// the dark modal ground.
export const THEME_LIGHT_DIALOG_BACKGROUND = FDS.colors.light['--bg-elevation-default-layer-2'];

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
  // Header height read from the library's own custom property, so the spacer cannot drift from the bar.
  mixins: { toolbar: { minHeight: 'var(--fds-header-height, 68px)' } },
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
    gradient: { main: EE_COLOR },
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
    xtmhub: { main: EE_COLOR },
    background: {
      default: background || THEME_LIGHT_DEFAULT_BACKGROUND,
      paper: paper || THEME_LIGHT_DEFAULT_PAPER,
      nav: nav || THEME_LIGHT_DEFAULT_NAV,
      accent: accent || THEME_LIGHT_DEFAULT_ACCENT,
      shadow: alpha('#000000', 0.15),
      // the only way for now to know if we should apply the paper color or not
      // fds-migration/TOKEN-MAPPING.md § D — token value, main's custom-paper behaviour kept.
      secondary: paper === THEME_LIGHT_DEFAULT_PAPER
        ? FDS.colors.light['--bg-elevation-highlight-layer-0']
        : (paper ?? FDS.colors.light['--bg-elevation-highlight-layer-0']),
      // Compare the RESOLVED nav (param is null when no custom theme is set) so
      // the default install gets a white drawer instead of darken('#FFFFFF', 0.5)
      // (a mid-grey) - mirrors the dark theme fix.
      drawer: (nav ?? THEME_LIGHT_DEFAULT_NAV) === THEME_LIGHT_DEFAULT_NAV
        ? '#FFFFFF'
        : darken(nav ?? THEME_LIGHT_DEFAULT_NAV, 0.5),
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
      none: FDS.colors.light['--color-feedback-neutral-primary'],
      default: FDS.colors.light['--color-feedback-neutral-primary'],
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
        main: THEME_LIGHT_DEFAULT_BACKGROUND,
        // bg1-bg4/disabled: resolved in § 9 on the matching elevation layer (bgN → layer-(N-1);
        // lib gap-fix lib#52). bg2 had a live consumer (the legacy LeftMenu.tsx separator) when
        // this mapping was arbitrated; that menu is now the design system's Navbar, which owns its
        // own separator colour, so bg2 has no consumer left. The light-mode value was
        // BYTE-IDENTICAL (#ffffff → #ffffff) either way, see § 9 proof table.
        bg1: FDS.colors.light['--bg-elevation-default-layer-0'],
        bg2: FDS.colors.light['--bg-elevation-default-layer-1'],
        bg3: FDS.colors.light['--bg-elevation-default-layer-2'],
        bg4: FDS.colors.light['--bg-elevation-default-layer-3'],
        disabled: FDS.colors.light['--bg-elevation-disabled'],
      },
      entities: {
        allThreats: FDS.colors.light['--color-entities-all-threats'],
        analyses: FDS.colors.light['--color-entities-analyses'],
        arsenal: FDS.colors.light['--color-entities-arsenal'],
        cases: FDS.colors.light['--color-entities-cases'],
        events: FDS.colors.light['--color-entities-events'],
        location: FDS.colors.light['--color-entities-location'],
        observations: FDS.colors.light['--color-entities-observations'],
        techniques: FDS.colors.light['--color-entities-techniques'],
        victimology: FDS.colors.light['--color-entities-victimology'],
      },
      border: {
        main: FDS.colors.light['--border-elevation-default'],
        border1: FDS.colors.light['--border-elevation-subtle'],
        border2: FDS.colors.light['--border-elevation-subtle'],
      },
      gradient: {
        background: 'linear-gradient(100.35deg, #ECECF2 0%, #F7F7F7 100%)',
        ia: 'linear-gradient(90deg, #3C108C 0.67%, #5E1AD5 100.67%)',
        focus: 'linear-gradient(90deg, #0015A8 -3.68%, #00BD94 106.62%)',
      },
      alert: {
        neutral: {
          primary: FDS.colors.light['--color-feedback-neutral-primary'],
          secondary: FDS.colors.light['--color-feedback-neutral-secondary'],
          secondaryTransparency30: FDS.colors.light['--color-feedback-neutral-secondary-transparency-30'],
        },
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
      // fds-migration/TOKEN-MAPPING.md § 4 — grey/darkBlue/turquoise/green/red retokenized on scalar
      // ramps (mode-invariant, hence FDS.scalars — identical values to dark mode's ramp). blue.500/900:
      // resolved in § 9 on --color-feedback-info-secondary-transparency-30 (mode-dependent color token,
      // not a scalar — both keys collapse to the same semi-transparent value; ⚠ semantic change if
      // ever consumed: was two distinct opaque colors, now one alpha overlay. 0 consumers confirmed,
      // lib gap-fix lib#52).
      tertiary: {
        grey: {
          400: '#95969D',
          700: '#494A50',
          800: '#313235',
        },
        blue: {
          500: FDS.colors.light['--color-feedback-info-secondary-transparency-30'],
          900: FDS.colors.light['--color-feedback-info-secondary-transparency-30'],
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
          // A dialog is a layer-2 surface, but a var() inside a custom-property
          // declaration is substituted where it is DECLARED (the root), so the
          // three input aliases keep their layer-0 value however deep the layer
          // class is applied - and layer-0's input colour is the very colour
          // this surface is painted with, which leaves every field inside a
          // dialog looking like it has no background. Declared here once, for
          // every dialog, wrapped or raw. Same mechanism as utils/fdsLayer.ts.
          '--bg-input-default': 'var(--bg-elevation-highlight-layer-2)',
          '--bg-input-disabled': 'var(--bg-elevation-disabled-layer-2)',
          '--bg-input-hover': 'var(--bg-elevation-hover-layer-2)',
          'backgroundImage': 'none',
          'backgroundColor': paper === THEME_LIGHT_DEFAULT_PAPER
            ? THEME_LIGHT_DIALOG_BACKGROUND
            : (paper ?? THEME_LIGHT_DIALOG_BACKGROUND),
          'borderRadius': 4,
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
          'height': INLINE_CONTROL_HEIGHT,
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
    MuiInputLabel: {
      styleOverrides: {
        outlined: {
          // MUI centres the un-shrunk label for its own 56px box; ours is 36px, so
          // its 16px put the label on the bottom edge. 8px centres it in 36px.
          'transform': 'translate(12px, 8px) scale(1)',
          '&.MuiInputLabel-shrink': { transform: 'translate(14px, -9px) scale(0.75)' },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          // The custom property, not the static hex: an outlined field inside a
          // drawer or popover must pick up that surface's own layer.
          'backgroundColor': 'var(--bg-input-default)',
          // Geometry borrowed from the library `Input`; paint only, no behaviour.
          'borderRadius': 'var(--radius-sm)',
          'minHeight': 36,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--border-input-hover)' },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--border-input-focus)' },
          '&.Mui-error .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--border-input-error)' },
          // Transparent with a disabled border, as the library `Input` does.
          '&.Mui-disabled': {
            'backgroundColor': 'transparent',
            '& .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--border-elevation-disabled)' },
          },
        },
        // 8px lands the single-line row on the library's 36px height; MUI's own
        // 16.5px makes a 54px row.
        input: {
          'padding': '8px 8px 8px 12px',
          // The browser draws the clock and calendar glyphs of native date and time
          // fields from the colour scheme, not from the text colour.
          '&[type="time"], &[type="date"], &[type="datetime-local"]': { colorScheme: 'light' },
        },
      },
    },
    // The date picker draws its own outlined input (MUI X), so the same paint as above.
    // The picker draws its own field, so MuiTextField's outlined default never
    // reaches it and the control fell back to the underlined standard variant.
    MuiPickersTextField: { defaultProps: { variant: 'outlined' } },
    MuiPickersOutlinedInput: {
      styleOverrides: {
        root: {
          'backgroundColor': 'var(--bg-input-default)',
          'borderRadius': 'var(--radius-sm)',
          'minHeight': 36,
          'padding': '0 8px 0 12px',
          '& .MuiPickersOutlinedInput-notchedOutline': { borderColor: 'transparent' },
          '&:hover .MuiPickersOutlinedInput-notchedOutline': { borderColor: 'var(--border-input-hover)' },
          '&.Mui-focused .MuiPickersOutlinedInput-notchedOutline': { borderColor: 'var(--border-input-focus)' },
          '&.Mui-error .MuiPickersOutlinedInput-notchedOutline': { borderColor: 'var(--border-input-error)' },
          '&.Mui-disabled': {
            'backgroundColor': 'transparent',
            '& .MuiPickersOutlinedInput-notchedOutline': { borderColor: 'var(--border-elevation-disabled)' },
          },
        },
        sectionsContainer: { padding: '8px 0' },
      },
    },
    MuiTextField: {
      // Every remaining MUI field is outlined, so it can carry the library
      // field background.
      defaultProps: { variant: 'outlined' },
      styleOverrides: {
        root: {
          'color': text_color,
          // Shrink = when at the top of the input in small size.
          '& .MuiFormLabel-root:not(.MuiInputLabel-shrink):not(.Mui-error)': { color: 'var(--text-default-secondary)' },
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
    // An alert's body is text, so it reads in the primary ink like any other
    // text; the severity is carried by the icon and the border, not by a
    // tinted paragraph.
    MuiAlert: {
      styleOverrides: {
        root: { color: text_color },
        message: { color: text_color },
      },
    },
    // Design-system icon buttons are squared (4px radius) - never MUI's
    // default circle/oval ripple.
    MuiIconButton: { styleOverrides: { root: { borderRadius: 4 } } },
    MuiCssBaseline: {
      styleOverrides: {
        // SPACING BETWEEN NEIGHBOURING CONTROLS. Two quiet controls — an icon
        // button or a tertiary button, the ones the library draws with no fill
        // and no border — sit 4px apart; any pair involving a primary or a
        // secondary keeps 8px. The rows themselves declare the 8px gap, so the
        // quiet pair pulls back the difference rather than every row having to
        // know the rule. `bg-transparent` + `border-0` is what both quiet
        // kinds emit and neither of the two loud ones does: a secondary
        // carries a border, a primary a fill.
        'button.bg-transparent.border-0:not([class*="before:bg-"]) + button.bg-transparent.border-0:not([class*="before:bg-"])': { marginLeft: -4 },
        'button.bg-transparent.border-0:not([class*="before:bg-"]) + span:has(> button.bg-transparent.border-0:not([class*="before:bg-"]))': { marginLeft: -4 },
        'span:has(> button.bg-transparent.border-0:not([class*="before:bg-"])) + button.bg-transparent.border-0:not([class*="before:bg-"])': { marginLeft: -4 },
        'span:has(> button.bg-transparent.border-0:not([class*="before:bg-"])) + span:has(> button.bg-transparent.border-0:not([class*="before:bg-"]))': { marginLeft: -4 },
        // Runs that are flush BY CONSTRUCTION are quiet too, and must not pull
        // on each other: a segmented control, a tab bar, a split button, the
        // pagination arrows.
        // Written with the same two classes as the rule above so it is strictly
        // more specific than it — a shorter selector would lose the cascade
        // and the tab bar would pull its own triggers together.
        ':is([role="radiogroup"], [role="tablist"], .MuiTabs-root, .MuiButtonGroup-root, .MuiTablePagination-actions, .MuiPagination-root) button.bg-transparent.border-0:not([class*="before:bg-"]) + button.bg-transparent.border-0:not([class*="before:bg-"])': { marginLeft: 0 },
        ':is([role="radiogroup"], [role="tablist"], .MuiTabs-root, .MuiButtonGroup-root, .MuiTablePagination-actions, .MuiPagination-root) span:has(> button.bg-transparent.border-0:not([class*="before:bg-"])) + span:has(> button.bg-transparent.border-0:not([class*="before:bg-"]))': { marginLeft: 0 },
        'html': {
          scrollbarColor: `${accent || THEME_LIGHT_DEFAULT_ACCENT} ${paper || THEME_LIGHT_DEFAULT_PAPER}`,
          scrollbarWidth: 'thin',
          background: `linear-gradient(100deg, ${background || THEME_LIGHT_DEFAULT_BACKGROUND} 0%, ${getAppBodyGradientEndColor(background)} 100%)`,
          backgroundAttachment: 'fixed',
          backgroundColor: background || THEME_LIGHT_DEFAULT_BACKGROUND,
        },
        'body': {
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
          // The editor is a field like any other: the input surface, a 4px
          // radius and the same transparent-to-hover-to-focus border as an
          // outlined input — not the underlined standard look it kept from
          // before the form fields moved.
          '.w-md-editor': {
            'boxShadow': 'none',
            'background': 'var(--bg-input-default)',
            'borderRadius': 'var(--radius-sm)',
            'border': '1px solid transparent',
            'transition': 'border-color .3s',
            '&:hover': { borderColor: 'var(--border-input-hover)' },
            '&:focus-within': { borderColor: 'var(--border-input-focus)' },
          },
          '.error .w-md-editor': {
            'border': '1px solid var(--border-input-error) !important',
            '&:hover': { border: '1px solid var(--border-input-error) !important' },
            '&:focus-within': { border: '1px solid var(--border-input-error) !important' },
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
        // A column heading names its column: secondary ink, like every other
        // heading of a surface.
        head: ({ theme }) => ({
          borderBottom: '1px solid rgba(0, 0, 0, 0.15)',
          color: theme.palette.text.secondary,
          fontWeight: 400,
        }),
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
          // Design system: chips are square-ish (4px), never pill-shaped
          'borderRadius': 4,
          'color': text_color,
          'textTransform': 'lowercase',
          '&::first-letter': { textTransform: 'uppercase' },
        },
        label: {
          'textTransform': 'lowercase',
          '&::first-letter': { textTransform: 'uppercase' },
          // The label has overflow hidden: a line-height smaller than the font's
          // ascent + descent clips glyphs at the bottom ("g", "p", ...). Chips
          // vertically center their label, so 'normal' is always safe here.
          'lineHeight': 'normal',
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
          '& .MuiFormLabel-root:not(.MuiInputLabel-shrink):not(.Mui-error)': { color: 'var(--text-default-secondary)' },
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
