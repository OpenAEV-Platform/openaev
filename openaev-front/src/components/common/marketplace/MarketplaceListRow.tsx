import { Text } from '@filigran/design-system';
import { Box, Skeleton, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../i18n';
import PlatformIcon from '../../PlatformIcon';

// Same column geometry as the integrations lines view.
const COLUMNS = {
  overline: {
    width: '14%',
    minWidth: 100,
    display: {
      xs: 'none',
      sm: 'flex',
    },
  },
  description: {
    width: '30%',
    display: {
      xs: 'none',
      lg: 'flex',
    },
  },
  details: {
    width: '16%',
    minWidth: 120,
    display: {
      xs: 'none',
      md: 'flex',
    },
  },
  action: {
    width: 240,
    display: 'flex',
    justifyContent: 'flex-end',
  },
} as const;

const cellSx = (column: keyof typeof COLUMNS) => ({
  ...COLUMNS[column],
  flexShrink: 0,
  alignItems: 'center',
  gap: 0.5,
  minWidth: 0,
});

const ellipsis = {
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
} as const;

const rowLayout = {
  display: 'flex',
  alignItems: 'center',
  gap: 1.5,
  paddingInline: 1.5,
} as const;

export const MarketplaceListHeader: FunctionComponent<{ overlineLabel: string }> = ({ overlineLabel }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const headerSx = {
    fontSize: 12,
    fontWeight: 600,
    color: theme.palette.text.secondary,
  };
  return (
    <Box sx={{
      ...rowLayout,
      paddingBlock: 1,
      backgroundColor: alpha(theme.palette.text.primary, 0.02),
      borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
    }}
    >
      <Typography
        component="div"
        sx={{
          ...headerSx,
          flex: 1,
        }}
      >
        {t('Name')}
      </Typography>
      <Typography
        component="div"
        sx={{
          ...headerSx,
          ...cellSx('overline'),
        }}
      >
        {t(overlineLabel)}
      </Typography>
      <Typography
        component="div"
        sx={{
          ...headerSx,
          ...cellSx('description'),
        }}
      >
        {t('Description')}
      </Typography>
      <Box sx={cellSx('details')} />
      <Box sx={cellSx('action')} />
    </Box>
  );
};

export const MarketplaceListRowSkeleton = () => (
  <Box sx={{
    ...rowLayout,
    paddingBlock: 1.25,
  }}
  >
    <Skeleton variant="rounded" width={32} height={32} animation="wave" />
    <Box sx={{ flex: 1 }}>
      <Skeleton variant="text" width="40%" animation="wave" />
      <Skeleton variant="text" width="25%" animation="wave" />
    </Box>
    <Skeleton variant="text" width="12%" animation="wave" />
    <Skeleton variant="text" width="28%" animation="wave" />
  </Box>
);

interface Props {
  icon: ReactNode;
  title: string;
  overline?: string;
  description?: string;
  chips: ReactNode;
  platforms: string[];
  target?: ReactNode;
  caption?: ReactNode;
  footerRight?: ReactNode;
  badge?: ReactNode;
  topLeft?: ReactNode;
  selected?: boolean;
  onClick?: () => void;
  linkTo?: string;
  testId?: string;
}

// List variant of MarketplaceCard, laid out as the integrations lines.
const MarketplaceListRow: FunctionComponent<Props> = ({
  icon,
  title,
  overline,
  description,
  chips,
  platforms,
  target,
  caption,
  footerRight,
  badge,
  topLeft,
  selected = false,
  onClick,
  linkTo,
  testId,
}) => {
  const theme = useTheme();
  const clickable = !!linkTo || !!onClick;
  const linkProps = linkTo
    ? {
        component: Link,
        to: linkTo,
      }
    : { onClick };

  return (
    <Box
      data-testid={testId}
      className="marketplace-list-row"
      {...linkProps}
      sx={{
        ...rowLayout,
        'paddingBlock': 1,
        'color': 'inherit',
        'textDecoration': 'none',
        'cursor': clickable ? 'pointer' : 'default',
        'backgroundColor': selected ? alpha(theme.palette.primary.main, 0.08) : undefined,
        'transition': 'background-color 0.2s ease-in-out',
        '&:hover': clickable ? { backgroundColor: theme.palette.action.hover } : undefined,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        flex: 1,
        minWidth: 0,
      }}
      >
        {topLeft && (
          <Box
            sx={{ display: 'flex' }}
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
            }}
          >
            {topLeft}
          </Box>
        )}
        <Box sx={{
          'width': 32,
          'height': 32,
          'flexShrink': 0,
          'display': 'flex',
          'alignItems': 'center',
          'justifyContent': 'center',
          'overflow': 'hidden',
          'borderRadius': 1,
          'border': `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
          'backgroundColor': alpha(theme.palette.primary.main, 0.08),
          '&:has(img)': {
            border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
            backgroundColor: alpha(theme.palette.text.primary, 0.04),
          },
          '& .MuiSvgIcon-root': { fontSize: 18 },
          '& img': {
            width: '24px !important',
            height: '24px !important',
            objectFit: 'contain',
          },
        }}
        >
          {icon}
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Text
            variant="content-base"
            style={{
              ...ellipsis,
              display: 'block',
              fontWeight: 600,
            }}
          >
            {title}
          </Text>
          {caption && (
            <Text
              variant="content-caption"
              className="text-default-secondary"
              style={{
                ...ellipsis,
                display: 'block',
              }}
            >
              {caption}
            </Text>
          )}
        </Box>
      </Box>
      <Box sx={cellSx('overline')}>
        <Text variant="content-caption" className="text-filigran-brand-primary" style={ellipsis}>
          {overline ?? '-'}
        </Text>
      </Box>
      <Box sx={cellSx('description')}>
        <Text variant="content-caption" className="text-default-secondary" style={ellipsis}>
          {description || '-'}
        </Text>
      </Box>
      <Box sx={cellSx('details')}>
        {badge}
        {chips}
        {target}
        {platforms.slice(0, 4).map(platform => (
          <PlatformIcon key={platform} width={16} platform={platform} tooltip />
        ))}
        {platforms.length > 4 && (
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {`+${platforms.length - 4}`}
          </Typography>
        )}
      </Box>
      <Box
        sx={cellSx('action')}
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
        }}
      >
        {footerRight}
      </Box>
    </Box>
  );
};

export default MarketplaceListRow;
