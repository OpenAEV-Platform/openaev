import { type PaletteColorOptions } from '@mui/material';

declare module '@mui/material/IconButton' {
  interface IconButtonPropsColorOverrides {
    ee: true;
    dangerZone: true;
  }
}

declare module '@mui/material/Button' {
  interface ButtonPropsColorOverrides {
    ee: true;
    dangerZone: true;
    pagination: true;
  }
}

declare module '@mui/material/ButtonGroup' {
  interface ButtonGroupPropsColorOverrides { pagination: true }
}

declare module '@mui/material/Chip' {
  interface ChipPropsColorOverrides { ee: true }
}

declare module '@mui/material/SvgIcon' {
  interface SvgIconPropsColorOverrides { ee: true }
}

declare module '@mui/material/Fab' {
  interface FabPropsColorOverrides { dangerZone: true }
}

declare module '@mui/material/Alert' {
  interface AlertPropsColorOverrides {
    dangerZone: true;
    secondary: true;
    ee: true;
  }
}

declare module '@mui/material/styles' {
  interface CommonColors {
    grey: string;
    lightGrey: string;
  }
  interface TypeBackground {
    nav: string;
    accent: string;
    shadow: string;
    secondary: string;
    disabled: string;
    gradient: {
      start: string;
      end: string;
    };
    drawer: string;
    code: string;
    paperInCard: string;
  }
  interface PaletteColor {
    background: string;
    lightBackground: string;
  }
  interface SimplePaletteColorOptions {
    background?: string;
    lightBackground?: string;
  }
  interface Palette {
    chip: PaletteColor;
    ee: PaletteColor;
    ai: PaletteColor;
    xtmhub: PaletteColor;
    widgets: {
      securityDomains: {
        colors: {
          success: string;
          intermediate: string;
          warning: string;
          failed: string;
          pending: string;
          unknown: string;
        };
      };
    };
    labelChipMap: Map<string, LabelColor>;
    dangerZone: PaletteColor;
    gradient: PaletteColor;
    border: {
      primary: string;
      secondary: string;
      pagination: string;
      main: string;
      lightBackground?: string;
      paper?: string;
    };
    pagination: PaletteColor;
    warn: PaletteColor;
    leftBar: {
      header: { itemBackground: string };
      popoverItem: string;
      hover: string;
      text: string;
    };
    severity: {
      critical: string;
      high: string;
      medium: string;
      low: string;
      info: string;
      none: string;
      default: string;
    };
    designSystem: DesignSystemPalette;
  }

  interface PaletteOptions {
    chip?: PaletteColorOptions;
    ee?: PaletteColorOptions;
    ai?: PaletteColorOptions;
    labelChipMap?: Map<string, LabelColor>;
    xtmhub?: PaletteColorOptions;
    widgets?: {
      securityDomains: {
        colors: {
          success: string;
          intermediate: string;
          warning: string;
          failed: string;
          pending: string;
          unknown: string;
        };
      };
    };
    dangerZone?: PaletteColorOptions;
    gradient?: PaletteColorOptions;
    border?: {
      primary: string;
      secondary: string;
      pagination: string;
      main?: string;
      lightBackground?: string;
      paper?: string;
    };
    pagination?: PaletteColorOptions;
    warn?: PaletteColorOptions;
    leftBar?: {
      header: { itemBackground: string };
      popoverItem: string;
      hover: string;
      text: string;
    };
    severity?: {
      critical: string;
      high: string;
      medium: string;
      low: string;
      info: string;
      none: string;
      default: string;
    };
    designSystem?: DesignSystemPalette;
  }

  interface TypeText {
    light: string;
    tertiary: string;
  }

  interface Theme {
    logo: string | undefined;
    logo_collapsed: string | undefined;
    borderRadius: number;
    button: {
      sizes: {
        default: SizeConfig;
        small: SizeConfig;
      };
    };
    tag: { overflowColor: string };
  }

  interface ThemeOptions {
    logo?: string | null;
    logo_collapsed?: string | null;
    borderRadius?: number;
    button?: {
      sizes?: {
        default?: SizeConfig;
        small?: SizeConfig;
      };
    };
    tag?: { overflowColor?: string };
  }
}

export interface SizeConfig {
  height: string;
  padding: string;
  minWidth: string;
  width: string;
  fontSize: string;
  fontWeight: number;
  lineHeight: string;
  iconSize: string;
}

export interface LabelColor {
  backgroundColor: string;
  color: string;
}

export const LabelColorDict = {
  Red: 'RED',
  Green: 'GREEN',
  Orange: 'ORANGE',
} as const;

export const FONT_FAMILY_CODE = 'Consolas, monaco, monospace';

// === Design System Palette Types ===

type MainPalette = {
  main: string;
  light: string;
  dark: string;
};

type BackgroundPalette = {
  main: string;
  bg1: string;
  bg2: string;
  bg3: string;
  bg4: string;
  disabled: string;
};

type BorderPalette = {
  main: string;
  border1: string;
  border2: string;
};

type GradientPalette = {
  background: string;
  ia: string;
  focus: string;
};

type AlertType = {
  primary: string;
  secondary: string;
};

type AlertPalette = {
  info: AlertType;
  success: AlertType & { tertiary: string };
  alert: AlertType;
  warning: AlertType;
  error: AlertType;
};

type ScaleLevels = 50 | 100 | 150 | 200 | 300 | 400 | 500 | 600 | 700 | 800 | 900 | 1000;
type ColorScale = Partial<Record<ScaleLevels, string>>;
type TertiaryPalette = Record<string, ColorScale>;

export type DesignSystemPalette = {
  primary: MainPalette;
  secondary: MainPalette;
  destructive: MainPalette;
  ia: MainPalette;
  background: BackgroundPalette;
  border: BorderPalette;
  gradient: GradientPalette;
  alert: AlertPalette;
  tertiary: TertiaryPalette;
};

// Re-export Theme type for convenience
export type { Theme } from '@mui/material/styles';
