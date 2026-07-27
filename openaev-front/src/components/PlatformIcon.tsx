import {
  AndroidOutlined,
  DevicesOtherOutlined,
  LanOutlined,
  MiscellaneousServicesOutlined,
  PhoneIphoneOutlined,
  PublicOutlined,
  type SvgIconComponent,
  ViewInArOutlined,
} from '@mui/icons-material';
import { type PaletteMode, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import linuxDark from '../static/images/platforms/linux-dark.png';
import linuxLight from '../static/images/platforms/linux-light.png';
import macosDark from '../static/images/platforms/macos-dark.png';
import macosLight from '../static/images/platforms/macos-light.png';
import windowsDark from '../static/images/platforms/windows-dark.png';
import windowsLight from '../static/images/platforms/windows-light.png';

interface PlatformIconProps {
  platform: string;
  width?: number;
  borderRadius?: number;
  tooltip?: boolean;
  marginRight?: string;
}

// OS platforms keep their recognizable brand logos.
const brandIcons: Record<string, Record<PaletteMode, string>> = {
  Windows: {
    dark: windowsDark,
    light: windowsLight,
  },
  Linux: {
    dark: linuxDark,
    light: linuxLight,
  },
  MacOS: {
    dark: macosDark,
    light: macosLight,
  },
};

// Non-OS platforms use clean, theme-aware MUI glyphs. The previous bitmaps read poorly at small
// sizes and looked inconsistent next to the crisp OS logos, so they are replaced by vector icons
// that inherit the current text color. There is deliberately NO "Unknown" entry: an unknown or
// missing platform renders nothing (see below) rather than a pointless, oddly-sized question mark.
const glyphIcons: Record<string, SvgIconComponent> = {
  Browser: PublicOutlined,
  Service: MiscellaneousServicesOutlined,
  Internal: LanOutlined,
  Container: ViewInArOutlined,
  Generic: DevicesOtherOutlined,
  iOS: PhoneIphoneOutlined,
  Android: AndroidOutlined,
};

/**
 * Whether PlatformIcon will actually render a glyph for this platform. Callers
 * with their own fallback (e.g. TargetIcon's asset category glyph) should test
 * this instead of rendering an empty spot for "Unknown" platforms.
 */
// eslint-disable-next-line react-refresh/only-export-components
export const hasPlatformIcon = (platform?: string): boolean =>
  !!platform && (platform in brandIcons || platform in glyphIcons);

const renderIcon = (platform: string, width: number | undefined = 40, borderRadius: number | undefined = 0, marginRight: string | undefined = '') => {
  const theme = useTheme();
  const { mode } = theme.palette;
  const brand = brandIcons[platform]?.[mode];
  if (brand) {
    return (
      <img
        style={{
          width,
          borderRadius,
          marginRight,
          height: 'fit-content',
        }}
        src={brand}
        alt={platform}
      />
    );
  }
  const Glyph = glyphIcons[platform];
  // Unknown / empty / unrecognized platform: render nothing. Callers that need a placeholder for an
  // empty platform list should render a neutral "-" (see PlatformIconGroup).
  if (!Glyph) {
    return null;
  }
  return (
    <Glyph
      style={{
        fontSize: width,
        marginRight,
        color: theme.palette.text?.secondary,
      }}
    />
  );
};

const PlatformIcon: FunctionComponent<PlatformIconProps> = ({ platform, width, borderRadius, marginRight, tooltip = false }) => {
  const rendered = renderIcon(platform, width, borderRadius, marginRight);
  if (!rendered) {
    return null;
  }
  if (tooltip) {
    return <Tooltip title={platform}>{rendered}</Tooltip>;
  }
  return rendered;
};

export default PlatformIcon;
