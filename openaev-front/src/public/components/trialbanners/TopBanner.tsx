import { ChevronRight } from '@mui/icons-material';
import { type Theme } from '@mui/material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SxProps } from '@mui/system';
import type React from 'react';

import { TOP_BANNER_HEIGHT } from './constants';

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
  const colors = TOPBANNER_COLORS[bannerColor];
  // Trial/license banner is the top-most stripe; system banners stack below it.
  const topOffset = 0;

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
      top: topOffset,
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
            'backgroundColor': theme.palette.common.white,
            'color': theme.palette.common.black,
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
