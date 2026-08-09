import { alpha, buttonClasses, darken, lighten, type ThemeOptions } from '@mui/material';

import LogoCollapsed from '../static/images/logo_light.png';
import LogoText from '../static/images/logo_text_light.png';
import { hexToRGB } from '../utils/Colors';
import { fileUri } from '../utils/Environment';
import { FDS } from './fds-tokens.generated';
import { FONT_FAMILY_CODE, type LabelColor, LabelColorDict } from './Theme';

// fds-migration/TOKEN-MAPPING.md § C — aligned on OpenCTI's already-validated reference value
// (tonic-primary), not on OpenAEV's own prior literal. See report for full rationale.
// Single lookup on purpose: secondary, EE, gradient.main, xtmhub.main and designSystem.secondary.main
// are deliberately unified on this one token (TOKEN-MAPPING.md § 3.3), so a future key rename only
// needs one update.
const EE_COLOR = FDS.colors.light['--color-filigran-tonic-primary'];

export const THEME_LIGHT_DEFAULT_BACKGROUND = FDS.colors.light['--bg-elevation-default-layer-0'];
// fds-migration/TOKEN-MAPPING.md § ISO OpenCTI — body/html gradient end-stop (was entirely unwired: no
// gradient existed on OpenAEV's body/html before this, ISO'd on OpenCTI's proven two-stop pattern).
const THEME_LIGHT_DEFAULT_BODY_END_GRADIENT = FDS.colors.light['--bg-elevation-default-layer-0-gradient'];
// fds-migration/TOKEN-MAPPING.md § 5 (bug fix) — was wired to the raw scalar '--darkblue-600'
// (#001bdb), which does not resolve to the real brand-primary light value. Fixed to mirror dark
// mode's own pattern (a semantic FDS.colors lookup, not a raw scalar index).
const THEME_LIGHT_DEFAULT_PRIMARY = FDS.colors.light['--color-filigran-brand-primary'];
const THEME_LIGHT_DEFAULT_SECONDARY = EE_COLOR;
const THEME_LIGHT_DEFAULT_ACCENT = FDS.colors.light['--bg-elevation-default-layer-3'];
const THEME_LIGHT_DEFAULT_PAPER = FDS.colors.light['--bg-elevation-default-layer-1'];
// NAV intentionally left as a raw literal — see TOKEN-MAPPING.md "7th item" flag (Sandy hasn't signed
// off on this specific, visibly-notable white -> #f2f2f3 shift yet).
const THEME_LIGHT_DEFAULT_NAV = '#ffffff';
const THEME_LIGHT_DEFAULT_TEXT = '#18191B';
export const THEME_LIGHT_DIALOG_BACKGROUND = '#FFFFFF';

// Same derivation as OpenCTI's ThemeLight.ts: a custom (DB-overridden) background still gets a live
// gradient end-stop via lighten(), since no field lets an admin author that end-stop directly today.
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
  // Top header height: taken from the design system's own custom property so
  // the spacer under the fixed header can never drift from the bar itself.
  // The fallback repeats the library default and applies only if its
  // stylesheet failed to load. MUI passes this string through to CSS.
  mixins: { toolbar: { minHeight: 'var(--fds-header-height, 68px)' } },
  palette: {
    mode: 'light',
    common: {
      white: '#ffffff',
      black: '#000000',
      grey: FDS.scalars['--gray-700'],
      lightGrey: FDS.scalars['--gray-300'],
    },
    // fds-migration/TOKEN-MAPPING.md § 1 — error split by mode (was reusing the identical dark-mode
    // reds in light theme, byte-for-byte, before this fix). secondary/tertiary invert intensity role
    // between modes: dark's "dark" shade is feedback-*-secondary, light's "dark" shade is
    // feedback-*-tertiary.
    error: {
      main: FDS.colors.light['--color-feedback-error-primary'],
      dark: FDS.colors.light['--color-feedback-error-tertiary'],
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
      // fds-migration/TOKEN-MAPPING.md § 4 — retokenized (exact scalar match: --darkblue-300 ===
      // --color-filigran-brand-secondary in light mode). Dark-mode's primary.light equivalent is
      // now resolved too (§ 9, lib gap-fix lib#52) — this scalar reference is kept as-is since it's
      // already exact, no change needed here.
      light: primary ? alpha(primary, 0.08) : FDS.scalars['--darkblue-300'],
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
      // fds-migration/TOKEN-MAPPING.md § 1 — AI colors retokenized (was hardcoded). Light mode flips
      // secondary/tertiary relative to dark mode (confirmed via OpenCTI's own wiring): dark mode's
      // "light" tier is ia-secondary/"dark" tier is ia-tertiary, light mode is the reverse.
      main: FDS.colors.light['--color-filigran-ia-primary'],
      light: FDS.colors.light['--color-filigran-ia-tertiary'],
      dark: FDS.colors.light['--color-filigran-ia-secondary'],
      contrastText: '#000000',
      background: 'rgba(221, 225, 254, 0.94)',
    },
    ee: {
      main: EE_COLOR,
      background: hexToRGB(EE_COLOR, 0.2),
      lightBackground: hexToRGB(EE_COLOR, 0.08),
      // fds-migration/TOKEN-MAPPING.md § 1 (bug fix) — was hardcoded to '#F2F2F3', a dark-mode-
      // appropriate pale text color mistakenly reused here (likely copy-paste from ThemeDark.ts in
      // #6813). Corrected to the light-appropriate dark text, mirroring dark mode's own pattern.
      contrastText: THEME_LIGHT_DEFAULT_TEXT,
    },
    xtmhub: { main: EE_COLOR },
    background: {
      default: background || THEME_LIGHT_DEFAULT_BACKGROUND,
      paper: paper || THEME_LIGHT_DEFAULT_PAPER,
      nav: nav || THEME_LIGHT_DEFAULT_NAV,
      // fds-migration/TOKEN-MAPPING.md — pre-existing design-system/current decision, kept as-is:
      // background.accent is conceptually distinct from THEME_LIGHT_DEFAULT_ACCENT (used below for
      // background.code/scrollbars) and has no FDS token match on either side (cross-product gap
      // with OpenCTI, both still hardcode this specific field).
      accent: accent || '#d3eaff',
      shadow: alpha('#000000', 0.15),
      // fds-migration/TOKEN-MAPPING.md § 3 (Option B) — custom-theme ternary preserved as-is (the
      // per-install paper override still works); only the ternary's DEFAULT branch is retokenized
      // (was a hardcoded literal in #6813).
      secondary: paper === THEME_LIGHT_DEFAULT_PAPER
        ? FDS.colors.light['--bg-elevation-highlight-layer-0']
        : (paper ?? FDS.colors.light['--bg-elevation-highlight-layer-0']),
      // Compare the RESOLVED nav (param is null when no custom theme is set) so
      // the default install gets a white drawer instead of darken('#FFFFFF', 0.5)
      // (a mid-grey) - mirrors the dark theme fix.
      // fds-migration/TOKEN-MAPPING.md § 3 (Option B) — default branch reuses PAPER's own FDS token
      // (exact match to #6813's hardcoded '#ffffff'; ternary/override behavior unchanged).
      drawer: (nav ?? THEME_LIGHT_DEFAULT_NAV) === THEME_LIGHT_DEFAULT_NAV
        ? THEME_LIGHT_DEFAULT_PAPER
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
      // fds-migration/TOKEN-MAPPING.md § 1 — retokenized (was hardcoded, wrong value vs Figma).
      disabled: FDS.colors.light['--text-default-disabled'],
    },
    leftBar: {
      header: { itemBackground: '#ECECF2' },
      popoverItem: '#ECECF2',
      hover: '#0015A81A',
      text: '#18191B',
    },
    // fds-migration/TOKEN-MAPPING.md § 4 — critical/high/medium/low/info mapped to the closest FDS
    // feedback token (not 1:1, mirrors OpenCTI's own wiring). none/default: resolved in § 9 on
    // --color-feedback-neutral-primary (both keys collapse to the same neutral value — lib gap-fix
    // lib#52).
    severity: {
      critical: FDS.colors.light['--color-feedback-error-primary'],
      high: FDS.colors.light['--color-feedback-warning-primary'],
      medium: FDS.colors.light['--color-feedback-alert-primary'],
      low: FDS.colors.light['--color-feedback-success-primary'],
      info: FDS.colors.light['--color-feedback-info-primary'],
      none: FDS.colors.light['--color-feedback-neutral-primary'],
      default: FDS.colors.light['--color-feedback-neutral-primary'],
    },
    designSystem: {
      primary: {
        main: FDS.colors.light['--color-filigran-brand-primary'],
        light: FDS.colors.light['--color-filigran-brand-secondary'],
        dark: FDS.colors.light['--color-filigran-brand-tertiary'],
      },
      // fds-migration/TOKEN-MAPPING.md § 5 gap (residual) — only `.main` matches tonic-primary
      // exactly; no FDS tonic-secondary/-tertiary token matches these two light-mode values (unlike
      // dark mode, an exact match there), so `.light`/`.dark` stay hardcoded, backlogged.
      secondary: {
        main: EE_COLOR,
        light: '#74E9CA',
        dark: '#0A8268',
      },
      // fds-migration/TOKEN-MAPPING.md § 4 — no dedicated "destructive" family in FDS, feedback-error
      // is the closest match (mirrors OpenCTI's own wiring). Light mode keeps secondary/tertiary in
      // the SAME order as `main` (opposite of dark mode's inversion — confirmed via OpenCTI).
      destructive: {
        main: FDS.colors.light['--color-feedback-error-primary'],
        light: FDS.colors.light['--color-feedback-error-secondary'],
        dark: FDS.colors.light['--color-feedback-error-tertiary'],
      },
      // "filigran-ia" family (corrected from OpenCTI's phantom '-main' key, see palette.ai above).
      ia: {
        main: FDS.colors.light['--color-filigran-ia-primary'],
        light: FDS.colors.light['--color-filigran-ia-tertiary'],
        dark: FDS.colors.light['--color-filigran-ia-secondary'],
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
      // fds-migration/TOKEN-MAPPING.md § 9 — resolved (lib gap-fix lib#52): main on
      // --border-elevation-default, border1/border2 both collapse onto --border-elevation-subtle
      // (0 consumers, confirmed by grep before and after; OpenCTI leaves these unmapped still —
      // documented divergence, not a regression here).
      border: {
        main: FDS.colors.light['--border-elevation-default'],
        border1: FDS.colors.light['--border-elevation-subtle'],
        border2: FDS.colors.light['--border-elevation-subtle'],
      },
      gradient: {
        // fds-migration/TOKEN-MAPPING.md backlog — OpenAEV's bridge has no '--gradient-background'
        // key (only '--gradient-default', a different angle/stops); OpenCTI's bridge has the exact
        // key. Left as-is pending either a bridge regen or explicit sign-off on the visual delta.
        background: 'linear-gradient(100.35deg, #ECECF2 0%, #F7F7F7 100%)',
        ia: FDS.gradients.light['--gradient-ia'],
        focus: FDS.gradients.light['--gradient-focus'],
      },
      // fds-migration/TOKEN-MAPPING.md § 4 — info/success/alert/warning/error retokenized on
      // matching feedback-* tokens (mirrors OpenCTI's own wiring exactly).
      alert: {
        info: {
          primary: FDS.colors.light['--color-feedback-info-primary'],
          secondary: FDS.colors.light['--color-feedback-info-secondary'],
        },
        success: {
          primary: FDS.colors.light['--color-feedback-success-primary'],
          secondary: FDS.colors.light['--color-feedback-success-secondary'],
          tertiary: FDS.colors.light['--color-feedback-success-tertiary'],
        },
        alert: {
          primary: FDS.colors.light['--color-feedback-alert-primary'],
          secondary: FDS.colors.light['--color-feedback-alert-secondary'],
        },
        warning: {
          primary: FDS.colors.light['--color-feedback-warning-primary'],
          secondary: FDS.colors.light['--color-feedback-warning-secondary'],
        },
        error: {
          primary: FDS.colors.light['--color-feedback-error-primary'],
          secondary: FDS.colors.light['--color-feedback-error-secondary'],
        },
      },
      // fds-migration/TOKEN-MAPPING.md § 4 — grey/darkBlue/turquoise/green/red retokenized on scalar
      // ramps (mode-invariant, hence FDS.scalars — identical values to dark mode's ramp). blue.500/900:
      // resolved in § 9 on --color-feedback-info-secondary-transparency (mode-dependent color token,
      // not a scalar — both keys collapse to the same semi-transparent value; ⚠ semantic change if
      // ever consumed: was two distinct opaque colors, now one alpha overlay. 0 consumers confirmed,
      // lib gap-fix lib#52).
      tertiary: {
        grey: {
          400: FDS.scalars['--gray-400'],
          700: FDS.scalars['--gray-700'],
          800: FDS.scalars['--gray-800'],
        },
        blue: {
          500: FDS.colors.light['--color-feedback-info-secondary-transparency'],
          900: FDS.colors.light['--color-feedback-info-secondary-transparency'],
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
          // fds-migration/TOKEN-MAPPING.md § ISO OpenCTI — two-stop body/html gradient (was a flat fill).
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
