import { Button } from '@filigran/design-system';
import { ChevronRight } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { type ComponentProps } from 'react';

import type { LoggedHelper } from '../../../actions/helper';
import { useHelper } from '../../../store';
import { isNotEmptyField } from '../../../utils/utils';
import { SYSTEM_BANNER_HEIGHT, TOP_BANNER_HEIGHT } from './constants';

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

// The library declares the token union for `Button`'s colour override but does
// not export it (LIBRARY-FEEDBACK #61), so it is read off the prop itself.
export type BannerButtonColor = NonNullable<ComponentProps<typeof Button>['color']>;

interface TopBannerProps {
  bannerText: React.ReactNode;
  bannerColor?: TopBannerColor;
  buttonText?: React.ReactNode;
  /** Primitive token the action button is filled with, matching the band it sits on. */
  buttonColor?: BannerButtonColor;
  onButtonClick?: () => void;
}

const TopBanner = ({ bannerText, bannerColor = 'gradient_blue', buttonText, buttonColor, onButtonClick }: TopBannerProps) => {
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
      color: 'var(--text-negative-primary)',
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
          size="sm"
          color={buttonColor}
          onClick={onButtonClick}
          endIcon={<ChevronRight fontSize="small" />}
          style={{ marginLeft: theme.spacing(1) }}
        >
          {buttonText}
        </Button>
      )}
    </div>
  );
};

export default TopBanner;
