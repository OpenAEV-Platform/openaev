import { ChevronRight } from '@mui/icons-material';
import { type Theme } from '@mui/material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SxProps } from '@mui/system';
import type React from 'react';

import type { LoggedHelper } from '../../../actions/helper';
import { useHelper } from '../../../store';
import { isNotEmptyField } from '../../../utils/utils';

export const TOP_BANNER_HEIGHT = 30;
export const SYSTEM_BANNER_HEIGHT = 20;

const TOPBANNER_COLORS = {
  gradient_blue: {
    from: '#7dd3fc',
    to: '#5eead4',
  },
  gradient_yellow: {
    from: '#fde68a',
    to: '#f59e0b',
  },
  gradient_green: {
    from: '#6ee7b7',
    to: '#fef08a',
  },
  red: {
    from: '#d0021b',
    to: '#d0021b',
  },
  yellow: {
    from: '#ffecb3',
    to: '#ffecb3',
  },
} as const;

export type TopBannerColor = keyof typeof TOPBANNER_COLORS;

interface TopBannerProps {
  bannerText: React.ReactNode;
  bannerColor?: TopBannerColor;
  buttonText?: React.ReactNode;
  buttonStyle?: SxProps<Theme>;
  onButtonClick?: () => void;
}

const TopBanner = ({ bannerText, bannerColor = 'gradient_blue', buttonText, buttonStyle, onButtonClick }: TopBannerProps) => {
  const theme = useTheme();
  const { settings } = useHelper((helper: LoggedHelper) => {
    return { settings: helper.getPlatformSettings() };
  });
  const colors = TOPBANNER_COLORS[bannerColor];

  const platformBannerLevel = settings?.platform_banner_level;
  const platformBannerText = settings?.platform_banner_text;
  const isPlatformBannerActivated = isNotEmptyField(platformBannerLevel) && isNotEmptyField(platformBannerText);

  return (
    <div style={{
      position: 'fixed',
      zIndex: 1202,
      color: '#000000',
      width: '100%',
      padding: theme.spacing(0.5),
      borderRadius: 0,
      backgroundImage: `linear-gradient(to right, ${colors.from}, ${colors.to})`,
      justifyContent: 'center',
      display: 'flex',
      top: isPlatformBannerActivated ? SYSTEM_BANNER_HEIGHT : 0,
      height: TOP_BANNER_HEIGHT,
    }}
    >
      <span>
        {bannerText}
      </span>
      { buttonText && (
        <Button
          variant="contained"
          onClick={onButtonClick}
          sx={{
            'marginLeft': theme.spacing(1),
            'padding': theme.spacing('1px', '6px'),
            'fontSize': '0.8rem',
            'textTransform': 'none',
            'lineHeight': 1.2,
            'background-color': '#007399',
            'color': 'white',
            '& .MuiButton-endIcon': { marginLeft: theme.spacing('2px') },
            ...buttonStyle,
          }}
          endIcon={<ChevronRight />}
        >
          {buttonText}
        </Button>
      )}
    </div>
  );
};

export default TopBanner;
