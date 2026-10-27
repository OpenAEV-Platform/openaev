import {
  Card,
  CardContent,
  CardHeader,
  CardMetaBar,
  CardSubtitle,
  CardTitle,
  Text,
} from '@filigran/design-system';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, type ReactNode } from 'react';
import { useHref, useNavigate } from 'react-router';

import PlatformIcon from '../../PlatformIcon';
import MarketplaceListRow from './MarketplaceListRow';
import { useMarketplaceViewMode } from './MarketplaceViewContext';

interface Props {
  /** Kept for the callers; the cards follow the OpenCTI integrations cards, which have no accent colour. */
  accent?: string;
  icon: ReactNode;
  title: string;
  /** Small label above the title, e.g. the kind of content. */
  overline?: string;
  /** One line under the title, e.g. the author. */
  subtitle?: string;
  /** A few lines under the header. */
  description?: string;
  chips: ReactNode;
  platforms: string[];
  /** Shown with the platforms, e.g. a team target. */
  target?: ReactNode;
  caption?: ReactNode;
  footerRight?: ReactNode;
  badge?: ReactNode;
  topLeft?: ReactNode;
  topLeftVisible?: boolean;
  topRight?: ReactNode;
  selected?: boolean;
  onClick?: () => void;
  /** Makes the card a real link (keeps open-in-new-tab and copy link). */
  linkTo?: string;
  testId?: string;
}

// Marketplace card laid out as the OpenCTI integrations cards, on the design system Card.
const MarketplaceCard: FunctionComponent<Props> = ({
  icon,
  title,
  overline,
  subtitle,
  description,
  chips,
  platforms,
  target,
  caption,
  footerRight,
  badge,
  topLeft,
  topLeftVisible = false,
  topRight,
  selected = false,
  onClick,
  linkTo,
  testId,
}) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const href = useHref(linkTo ?? '.');
  const viewMode = useMarketplaceViewMode();

  // The card link is a plain anchor: a simple click stays in the app, a modified click opens a tab.
  const followLink = (event: MouseEvent<HTMLElement>) => {
    const anchor = (event.target as HTMLElement).closest('a[data-card-link]');
    if (!linkTo || !anchor || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    navigate(linkTo);
  };

  if (viewMode === 'list') {
    return (
      <MarketplaceListRow
        icon={icon}
        title={title}
        overline={overline}
        description={description}
        chips={chips}
        platforms={platforms}
        target={target}
        caption={caption}
        footerRight={footerRight}
        badge={badge}
        topLeft={topLeft}
        selected={selected}
        onClick={onClick}
        linkTo={linkTo}
        testId={testId}
      />
    );
  }

  const interaction = linkTo
    ? { href }
    : onClick && { onClick: () => onClick() };

  return (
    <Box
      data-testid={testId}
      onClickCapture={followLink}
      sx={{
        'height': '100%',
        // The doubled class beats the design system hover rules, which load after and use :has().
        '&& > div': {
          height: 280,
          transition: 'transform 0.3s ease-in-out, border-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out',
        },
        // Same hover as the OpenCTI integrations cards: the surface keeps its colour.
        '&&:hover > div': {
          transform: 'translateY(-2px)',
          borderColor: alpha(theme.palette.primary.main, 0.3),
          boxShadow: `0 0 30px ${alpha(theme.palette.primary.main, 0.12)}`,
          backgroundColor: 'var(--bg-elevation-default-layer-1)',
        },
        '&:hover .marketplace-card-select': { visibility: 'visible' },
        // Title on up to two lines, as the integrations cards.
        '& .marketplace-card-title, & .marketplace-card-title [data-card-link]': { minWidth: 0 },
        '& .marketplace-card-title .truncate': {
          whiteSpace: 'normal',
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
          wordBreak: 'break-word',
          fontSize: 15,
          fontWeight: 600,
          lineHeight: 1.35,
        },
      }}
    >
      <Card {...interaction} elevation={1} selected={selected} gap={16} padding={24}>
        <CardHeader
          icon={(
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              {/* Selection before the icon; its room is kept so nothing moves when it shows. */}
              {topLeft && (
                <Box
                  className="marketplace-card-select"
                  onClick={event => event.stopPropagation()}
                  sx={{
                    display: 'flex',
                    visibility: topLeftVisible ? 'visible' : 'hidden',
                    position: 'relative',
                    zIndex: 1,
                  }}
                >
                  {topLeft}
                </Box>
              )}
              <Box sx={{
                'width': 56,
                'height': 56,
                'display': 'flex',
                'alignItems': 'center',
                'justifyContent': 'center',
                'overflow': 'hidden',
                'borderRadius': 1,
                // As in OpenCTI: a glyph sits on a primary tint, a logo on a neutral frame.
                'border': `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
                'backgroundColor': alpha(theme.palette.primary.main, 0.08),
                '&:has(img)': {
                  border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                  backgroundColor: alpha(theme.palette.text.primary, 0.04),
                },
                '& svg': {
                  width: '28px !important',
                  height: '28px !important',
                  marginTop: '0 !important',
                },
                '& .MuiSvgIcon-root': { fontSize: 28 },
                '& img': {
                  width: '44px !important',
                  height: '44px !important',
                  marginTop: '0 !important',
                  borderRadius: '4px !important',
                  objectFit: 'contain',
                },
              }}
              >
                {icon}
              </Box>
            </Box>
          )}
          action={(
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                position: 'relative',
                zIndex: 1,
              }}
              onClick={event => event.stopPropagation()}
            >
              {topRight}
            </Box>
          )}
        >
          {overline && (
            <Text
              variant="content-caption"
              className="block text-filigran-brand-primary"
              style={{
                fontSize: 12,
                fontWeight: 500,
                marginBottom: 4,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {overline}
            </Text>
          )}
          <CardTitle className="marketplace-card-title">{title}</CardTitle>
          {subtitle && <CardSubtitle>{subtitle}</CardSubtitle>}
        </CardHeader>
        <Box sx={{
          flexGrow: 1,
          overflow: 'hidden',
          minHeight: 0,
        }}
        >
          {description && <CardContent clamp={subtitle || caption ? 3 : 4}>{description}</CardContent>}
        </Box>
        <Box sx={{
          display: 'flex',
          alignItems: 'flex-end',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 1,
        }}
        >
          <Box sx={{
            'display': 'flex',
            'alignItems': 'center',
            'gap': 1.5,
            'flexWrap': 'wrap',
            'minWidth': 0,
            '& > div': {
              display: 'flex',
              alignItems: 'center',
              flexWrap: 'wrap',
              minWidth: 0,
              gap: 0.5,
            },
            '& > div:empty': { display: 'none' },
          }}
          >
            <div>
              {badge}
              {chips}
            </div>
            <div>
              {target}
              {platforms.slice(0, 4).map(platform => (
                <PlatformIcon key={platform} width={16} platform={platform} tooltip />
              ))}
              {platforms.length > 4 && (
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {`+${platforms.length - 4}`}
                </Typography>
              )}
            </div>
          </Box>
          {footerRight && (
            <Box sx={{
              flexShrink: 0,
              marginLeft: 'auto',
              position: 'relative',
              zIndex: 1,
            }}
            >
              {footerRight}
            </Box>
          )}
        </Box>
        {caption && (
          <CardMetaBar>
            <Text variant="content-caption" className="text-default-secondary">{caption}</Text>
          </CardMetaBar>
        )}
      </Card>
    </Box>
  );
};

export default MarketplaceCard;
