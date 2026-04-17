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
    card: { paper: string };
    labelChipMap: Map<string, LabelColor>;
    dangerZone: PaletteColor;
    gradient: PaletteColor;
    border: {
      primary: string;
      secondary: string;
      pagination: string;
      lightBackground?: string;
      paper?: string;
    };
    pagination: PaletteColor;
    warn: PaletteColor;
  }
  interface PaletteOptions {
    chip: PaletteColorOptions;
    ee: PaletteColorOptions;
    ai: PaletteColorOptions;
    labelChipMap: Map<string, LabelColor>;
    xtmhub: PaletteColorOptions;
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
    dangerZone?: PaletteColorOptions;
    gradient?: PaletteColorOptions;
    border?: {
      primary: string;
      secondary: string;
      pagination: string;
      lightBackground?: string;
      paper?: string;
    };
    pagination?: PaletteColorOptions;
    warn?: PaletteColorOptions;
  }
  interface Theme {
    logo: string | undefined;
    logo_collapsed: string | undefined;
    borderRadius: number;
  }
  interface ThemeOptions {
    logo: string | undefined;
    logo_collapsed: string | undefined;
    borderRadius: number;
  }
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
