import { alpha, buttonClasses, darken, lighten, type ThemeOptions } from '@mui/material';

import LogoCollapsed from '../static/images/logo_dark.png';
import LogoText from '../static/images/logo_text_dark.png';
import { hexToRGB } from '../utils/Colors';
import { fileUri } from '../utils/Environment';
import { FDS } from './fds-tokens.generated';
import { FONT_FAMILY_CODE, type LabelColor, LabelColorDict } from './Theme';

// fds-migration/TOKEN-MAPPING.md § B/C — recalibrated on FDS tokens (see report for old→new rationale).
// Single lookup on purpose: secondary, EE, gradient.main, xtmhub.main and designSystem.secondary.main
// are deliberately unified on this one token (TOKEN-MAPPING.md § 3.3), so a future key rename only
// needs one update.
const EE_COLOR = FDS.colors.dark['--color-filigran-tonic-primary'];

export const THEME_DARK_DEFAULT_BACKGROUND = FDS.colors.dark['--bg-elevation-default-layer-0'];
// fds-migration/TOKEN-MAPPING.md § ISO OpenCTI — body/html gradient end-stop (was entirely unwired: no
// gradient existed on OpenAEV's body/html before this, ISO'd on OpenCTI's proven two-stop pattern).
const THEME_DARK_DEFAULT_BODY_END_GRADIENT = FDS.colors.dark['--bg-elevation-default-layer-0-gradient'];
const THEME_DARK_DEFAULT_PRIMARY = FDS.colors.dark['--color-filigran-brand-primary'];
const THEME_DARK_DEFAULT_SECONDARY = EE_COLOR;
const THEME_DARK_DEFAULT_ACCENT = FDS.colors.dark['--bg-elevation-default-layer-3'];
const THEME_DARK_DEFAULT_PAPER = FDS.colors.dark['--bg-elevation-default-layer-1'];
const THEME_DARK_DEFAULT_NAV = FDS.colors.dark['--bg-elevation-heading-layer-0'];
// #6813 additions, non-color, kept as-is (no FDS text/dialog-bg token family exists yet).
const THEME_DARK_DEFAULT_TEXT = '#F2F2F3';
export const THEME_DARK_DIALOG_BACKGROUND = '#0F1D34';

// Same derivation as OpenCTI's ThemeDark.ts: a custom (DB-overridden) background still gets a live
// gradient end-stop via lighten(), since no field lets an admin author that end-stop directly today.
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
  // Header height read from the library's own custom property, so the spacer cannot drift from the bar.
  mixins: { toolbar: { minHeight: 'var(--fds-header-height, 68px)' } },
  palette: {
    mode: 'dark',
    common: {
      white: '#ffffff',
      black: '#000000',
      grey: FDS.scalars['--gray-400'],
      lightGrey: FDS.scalars['--gray-150'],
    },
    // fds-migration/TOKEN-MAPPING.md § 1 — error split by mode (was reusing the same dark-appropriate
    // reds in light theme). secondary/tertiary invert intensity role between modes: dark's "dark"
    // shade is feedback-*-secondary, light's "dark" shade is feedback-*-tertiary.
    error: {
      main: FDS.colors.dark['--color-feedback-error-primary'],
      dark: FDS.colors.dark['--color-feedback-error-secondary'],
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
    warning: { main: '#ffa726' },
    primary: {
      main: primary || THEME_DARK_DEFAULT_PRIMARY,
      // fds-migration/TOKEN-MAPPING.md § 9 — resolved (lib gap-fix lib#52) on
      // --color-filigran-brand-secondary. ≈approximate per the guide (small delta: was #B2ECFF,
      // token resolves #A8E7FF — R/G ~4% darker, B unchanged). 0 consumers confirmed for this root
      // property (designSystem.primary.light is a different, already-wired property).
      light: primary ? alpha(primary, 0.08) : FDS.colors.dark['--color-filigran-brand-secondary'],
    },
    secondary: { main: secondary || THEME_DARK_DEFAULT_SECONDARY },
    gradient: { main: EE_COLOR },
    border: {
      primary: hexToRGB(primary || THEME_DARK_DEFAULT_PRIMARY, 0.3),
      // fds-migration/TOKEN-MAPPING.md § 1 — borders retokenized on Figma's default elevation border
      // (was a hardcoded literal in #6813; no exact hex match, this is a deliberate design decision).
      secondary: FDS.colors.dark['--border-elevation-default'],
      pagination: hexToRGB('#ffffff', 0.5),
      paper: hexToRGB('#ffffff', 0.12),
      main: FDS.colors.dark['--border-elevation-default'],
    },
    pagination: { main: '#ffffff' },
    chip: { main: '#ffffff' },
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
      // fds-migration/TOKEN-MAPPING.md § 1 — AI colors retokenized (was hardcoded; OpenCTI's own
      // wiring references a phantom '--color-filigran-ia-main' key removed from the current bridge,
      // corrected here to '-primary').
      main: FDS.colors.dark['--color-filigran-ia-primary'],
      light: FDS.colors.dark['--color-filigran-ia-secondary'],
      dark: FDS.colors.dark['--color-filigran-ia-tertiary'],
      contrastText: '#000000',
      background: 'rgba(28, 47, 73, 0.94)',
    },
    ee: {
      main: EE_COLOR,
      contrastText: THEME_DARK_DEFAULT_TEXT,
      background: hexToRGB(EE_COLOR, 0.2),
      lightBackground: hexToRGB(EE_COLOR, 0.08),
    },
    xtmhub: { main: EE_COLOR },
    background: {
      default: background || THEME_DARK_DEFAULT_BACKGROUND,
      paper: paper || THEME_DARK_DEFAULT_PAPER,
      nav: nav || THEME_DARK_DEFAULT_NAV,
      accent: accent || THEME_DARK_DEFAULT_ACCENT,
      shadow: 'rgba(200, 200, 200, 0.15)',
      // fds-migration/TOKEN-MAPPING.md § 3 (Option B) — custom-theme ternary preserved as-is (the
      // per-install paper override still works); only the ternary's DEFAULT branch is retokenized
      // (was a hardcoded literal in #6813). DragAndDropImportDialog.tsx consumes this directly and
      // must never see it resolve to undefined.
      secondary: paper === THEME_DARK_DEFAULT_PAPER
        ? FDS.colors.dark['--bg-elevation-highlight-layer-0']
        : (paper ?? FDS.colors.dark['--bg-elevation-highlight-layer-0']),
      // Compare the RESOLVED nav (param is null when no custom theme is set), so
      // the default install gets the lighter drawer surface instead of
      // darken(nav, 0.5) - the latter made every drawer body near-black.
      // fds-migration/TOKEN-MAPPING.md § 3 (Option B) — default branch reuses PAPER's own FDS token
      // (closest elevation match to #6813's hardcoded '#0f1d34'; ternary/override behavior unchanged).
      drawer: (nav ?? THEME_DARK_DEFAULT_NAV) === THEME_DARK_DEFAULT_NAV
        ? THEME_DARK_DEFAULT_PAPER
        : darken(nav ?? THEME_DARK_DEFAULT_NAV, 0.5),
      disabled: '#363B46',
      gradient: {
        start: background || THEME_DARK_DEFAULT_BACKGROUND,
        end: getAppBodyGradientEndColor(background),
      },
      code: accent || THEME_DARK_DEFAULT_ACCENT,
      paperInCard: paper || THEME_DARK_DEFAULT_PAPER,
    },
    // NOTE: unlike OpenCTI we deliberately keep MUI's muted text.secondary:
    // OpenAEV components use `text.secondary` pervasively for muted labels,
    // while OpenCTI reserves muting for `text.tertiary`.
    text: {
      tertiary: '#848592',
      light: '#AFB0B6',
      // fds-migration/TOKEN-MAPPING.md § 1 — retokenized (was hardcoded, wrong value vs Figma).
      disabled: FDS.colors.dark['--text-default-disabled'],
    },
    leftBar: {
      header: { itemBackground: '#253348' },
      popoverItem: '#070D19',
      hover: '#253348',
      text: '#F2F2F3',
    },
    // fds-migration/TOKEN-MAPPING.md § 4 — critical/high/medium/low/info mapped to the closest FDS
    // feedback token (not 1:1, mirrors OpenCTI's own wiring). none/default: resolved in § 9 on
    // --color-feedback-neutral-primary (both keys collapse to the same neutral value — lib gap-fix
    // lib#52).
    severity: {
      critical: FDS.colors.dark['--color-feedback-error-primary'],
      high: FDS.colors.dark['--color-feedback-warning-primary'],
      medium: FDS.colors.dark['--color-feedback-alert-primary'],
      low: FDS.colors.dark['--color-feedback-success-primary'],
      info: FDS.colors.dark['--color-feedback-info-primary'],
      none: FDS.colors.dark['--color-feedback-neutral-primary'],
      default: FDS.colors.dark['--color-feedback-neutral-primary'],
    },
    designSystem: {
      // "filigran-brand" family: light/dark are the -secondary/-tertiary tiers of `main`.
      primary: {
        main: FDS.colors.dark['--color-filigran-brand-primary'],
        light: FDS.colors.dark['--color-filigran-brand-secondary'],
        dark: FDS.colors.dark['--color-filigran-brand-tertiary'],
      },
      // "filigran-tonic" family - same EE_COLOR family as gradient.main/xtmhub (rule 2 reunification).
      secondary: {
        main: EE_COLOR,
        light: FDS.colors.dark['--color-filigran-tonic-secondary'],
        dark: FDS.colors.dark['--color-filigran-tonic-tertiary'],
      },
      // No dedicated "destructive" family in FDS - feedback-error is the closest match (mirrors
      // OpenCTI's own wiring).
      destructive: {
        main: FDS.colors.dark['--color-feedback-error-primary'],
        light: FDS.colors.dark['--color-feedback-error-tertiary'],
        dark: FDS.colors.dark['--color-feedback-error-secondary'],
      },
      // "filigran-ia" family (corrected from OpenCTI's phantom '-main' key, see palette.ai above).
      ia: {
        main: FDS.colors.dark['--color-filigran-ia-primary'],
        light: FDS.colors.dark['--color-filigran-ia-secondary'],
        dark: FDS.colors.dark['--color-filigran-ia-tertiary'],
      },
      background: {
        main: THEME_DARK_DEFAULT_BACKGROUND,
        // bg1-bg4/disabled: resolved in § 9 on the matching elevation layer (bgN → layer-(N-1);
        // lib gap-fix lib#52). bg2 had a live consumer (the legacy LeftMenu.tsx separator) when
        // this mapping was arbitrated; that menu is now the design system's Navbar, which owns its
        // own separator colour, so bg2 has no consumer left. The delta was confirmed imperceptible
        // either way, see § 9 proof table.
        bg1: FDS.colors.dark['--bg-elevation-default-layer-0'],
        bg2: FDS.colors.dark['--bg-elevation-default-layer-1'],
        bg3: FDS.colors.dark['--bg-elevation-default-layer-2'],
        bg4: FDS.colors.dark['--bg-elevation-default-layer-3'],
        disabled: FDS.colors.dark['--bg-elevation-disabled'],
      },
      // fds-migration/TOKEN-MAPPING.md § 9 — resolved (lib gap-fix lib#52): main on
      // --border-elevation-default, border1/border2 both collapse onto --border-elevation-subtle
      // (0 consumers, confirmed by grep before and after).
      border: {
        main: FDS.colors.dark['--border-elevation-default'],
        border1: FDS.colors.dark['--border-elevation-subtle'],
        border2: FDS.colors.dark['--border-elevation-subtle'],
      },
      gradient: {
        // No FDS gradient exactly named "background" in this bridge (only '--gradient-default', a
        // different angle/stops) - left as-is pending confirmation this is an acceptable swap
        // (fds-migration/TOKEN-MAPPING.md backlog).
        background: 'linear-gradient(100.35deg, #070D19 0%, #08101d 100%)',
        ia: FDS.gradients.dark['--gradient-ia'],
        focus: FDS.gradients.dark['--gradient-focus'],
      },
      // fds-migration/TOKEN-MAPPING.md § 4 — info/success/alert/warning/error retokenized on
      // matching feedback-* tokens (mirrors OpenCTI's own wiring exactly).
      alert: {
        info: {
          primary: FDS.colors.dark['--color-feedback-info-primary'],
          secondary: FDS.colors.dark['--color-feedback-info-secondary'],
        },
        success: {
          primary: FDS.colors.dark['--color-feedback-success-primary'],
          secondary: FDS.colors.dark['--color-feedback-success-secondary'],
          tertiary: FDS.colors.dark['--color-feedback-success-tertiary'],
        },
        alert: {
          primary: FDS.colors.dark['--color-feedback-alert-primary'],
          secondary: FDS.colors.dark['--color-feedback-alert-secondary'],
        },
        warning: {
          primary: FDS.colors.dark['--color-feedback-warning-primary'],
          secondary: FDS.colors.dark['--color-feedback-warning-secondary'],
        },
        error: {
          primary: FDS.colors.dark['--color-feedback-error-primary'],
          secondary: FDS.colors.dark['--color-feedback-error-secondary'],
        },
      },
      // fds-migration/TOKEN-MAPPING.md § 4 — grey/darkBlue/turquoise/green/red retokenized on scalar
      // ramps (mode-invariant, hence FDS.scalars). blue.500/900: resolved in § 9 on
      // --color-feedback-info-secondary-transparency-30 (mode-dependent color token, not a scalar —
      // both keys collapse to the same semi-transparent value; ⚠ semantic change if ever consumed:
      // was two distinct opaque colors, now one alpha overlay. 0 consumers confirmed, lib gap-fix
      // lib#52).
      tertiary: {
        grey: {
          400: FDS.scalars['--gray-400'],
          700: FDS.scalars['--gray-700'],
          800: FDS.scalars['--gray-800'],
        },
        blue: {
          500: FDS.colors.dark['--color-feedback-info-secondary-transparency-30'],
          900: FDS.colors.dark['--color-feedback-info-secondary-transparency-30'],
        },
        darkBlue: {
          300: FDS.scalars['--darkblue-300'],
          500: FDS.scalars['--darkblue-500'],
        },
        turquoise: {
          600: FDS.scalars['--turquoise-600'],
          800: FDS.scalars['--turquoise-800'],
        },
        green: {
          400: FDS.scalars['--green-400'],
          600: FDS.scalars['--green-600'],
          800: FDS.scalars['--green-800'],
        },
        red: {
          100: FDS.scalars['--red-100'],
          200: FDS.scalars['--red-200'],
          400: FDS.scalars['--red-400'],
          500: FDS.scalars['--red-500'],
          600: FDS.scalars['--red-600'],
          700: FDS.scalars['--red-700'],
        },
        orange: {
          400: FDS.scalars['--orange-400'],
          500: FDS.scalars['--orange-500'],
        },
        yellow: { 400: FDS.scalars['--yellow-400'] },
      },
    },
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
  },
  tag: { overflowColor: primary || THEME_DARK_DEFAULT_PRIMARY },
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
            'borderColor': hexToRGB('#ffffff', 0.15),
            'padding': 7,
            'minWidth': 0,
            '&:hover': {
              borderColor: hexToRGB('#ffffff', 0.15),
              backgroundColor: hexToRGB('#ffffff', 0.05),
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
          backgroundColor: paper === THEME_DARK_DEFAULT_PAPER
            ? THEME_DARK_DIALOG_BACKGROUND
            : (paper ?? THEME_DARK_DIALOG_BACKGROUND),
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
          // Shrink = when at the top of the input in small size.
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
    MuiPaper: { styleOverrides: { root: { color: text_color } } },
    // Design-system icon buttons are squared (4px radius) - never MUI's
    // default circle/oval ripple.
    MuiIconButton: { styleOverrides: { root: { borderRadius: 4 } } },
    MuiCssBaseline: {
      styleOverrides: {
        html: {
          scrollbarColor: `${background || THEME_DARK_DEFAULT_BACKGROUND} ${accent || THEME_DARK_DEFAULT_ACCENT}`,
          scrollbarWidth: 'thin',
          // fds-migration/TOKEN-MAPPING.md § ISO OpenCTI — two-stop body/html gradient (was a flat fill).
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
            fontFamily: FONT_FAMILY_CODE,
            color: `${text_color} !important`,
            background: `${accent || THEME_DARK_DEFAULT_ACCENT} !important`,
            borderRadius: 4,
          },
          'pre.light': {
            fontFamily: FONT_FAMILY_CODE,
            background: `${nav || THEME_DARK_DEFAULT_NAV} !important`,
            borderRadius: 4,
          },
          'code': {
            fontFamily: FONT_FAMILY_CODE,
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
            backgroundColor: hexToRGB(primary || THEME_DARK_DEFAULT_PRIMARY, 0.24),
          },
          '&.Mui-selected:hover': {
            boxShadow: `2px 0 ${primary || THEME_DARK_DEFAULT_PRIMARY} inset`,
            backgroundColor: hexToRGB(primary || THEME_DARK_DEFAULT_PRIMARY, 0.32),
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
          '& .MuiFormLabel-root:not(.MuiInputLabel-shrink):not(.Mui-error)': { color: '#AFB0B6' },
          '& .MuiOutlinedInput-root': {
            // the only way for now to know if we should apply the paper color or not
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
