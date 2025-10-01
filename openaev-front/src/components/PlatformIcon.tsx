import { type PaletteMode, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import browserDark from '../static/images/platforms/browser-dark.png';
import browserLight from '../static/images/platforms/browser-light.png';
import internalDark from '../static/images/platforms/internal-dark.png';
import internalLight from '../static/images/platforms/internal-light.png';
import linuxDark from '../static/images/platforms/linux-dark.png';
import linuxLight from '../static/images/platforms/linux-light.png';
import macosDark from '../static/images/platforms/macos-dark.png';
import macosLight from '../static/images/platforms/macos-light.png';
import serviceDark from '../static/images/platforms/service-dark.png';
import serviceLight from '../static/images/platforms/service-light.png';
import unknownDark from '../static/images/platforms/unknown-dark.png';
import unknownLight from '../static/images/platforms/unknown-light.png';
import windowsDark from '../static/images/platforms/windows-dark.png';
import windowsLight from '../static/images/platforms/windows-light.png';

interface PlatformIconProps {
  platform: string;
  width?: number;
  borderRadius?: number;
  tooltip?: boolean;
  marginRight?: string;
}

const platformIcons: Record<PlatformIconProps['platform'], Record<PaletteMode, string>> = {
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
  Browser: {
    dark: browserDark,
    light: browserLight,
  },
  Service: {
    dark: serviceDark,
    light: serviceLight,
  },
  Internal: {
    dark: internalDark,
    light: internalLight,
  },
  Unknown: {
    dark: unknownDark,
    light: unknownLight,
  },
};

const renderIcon = (platform: string, width: number | undefined = 40, borderRadius: number | undefined = 0, marginRight: string | undefined = '') => {
  const theme = useTheme();
  const { mode } = theme.palette;
  const src = platformIcons[platform]?.[mode] || platformIcons.Unknown[mode];
  const height = 'fit-content';
  return (
    <img
      style={{
        width,
        borderRadius,
        marginRight,
        height,
      }}
      src={src}
      alt={platform}
    />
  );
};
const PlatformIcon: FunctionComponent<PlatformIconProps> = ({ platform, width, borderRadius, marginRight, tooltip = false }) => {
  if (tooltip) {
    return (
      <Tooltip title={platform}>
        {renderIcon(platform, width, borderRadius, marginRight)}
      </Tooltip>
    );
  }
  return renderIcon(platform, width, borderRadius, marginRight);
};

export default PlatformIcon;
