import { type PaletteColorOptions } from '@mui/material';

declare module '@mui/material/IconButton' {
  interface IconButtonPropsColorOverrides { ee: true }
}

declare module '@mui/material/Button' {
  interface ButtonPropsColorOverrides { ee: true }
}

declare module '@mui/material/Button' {
  interface ChipPropsColorOverrides { ee: true }
}

declare module '@mui/material/SvgIcon' {
  interface SvgIconPropsColorOverrides { ee: true }
}

declare module '@mui/material/styles' {
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
    card: { paper: string };
  }
  interface PaletteOptions {
    chip: PaletteColorOptions;
    ee: PaletteColorOptions;
    ai: PaletteColorOptions;
    xtmhub: PaletteColorOptions;
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
