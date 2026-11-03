import {
  Card,
  CardContent,
  CardHeader,
  CardMetaBar,
  CardSubtitle,
  CardTitle,
  Text,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@filigran/design-system';
import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, type ReactNode, useRef, useState } from 'react';
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
  /**
   * Figma Card (8467:4560) parts the code library does not ship yet (DS 1.3.0): a full-bleed
   * CardBanner at the top for the one thing to act on, a tags row under the header, and CardInfo
   * label / value pairs under the description. Optional: cards without them are unchanged.
   */
  banner?: ReactNode;
  tags?: ReactNode;
  info?: {
    label: string;
    value: string;
  }[];
  /** Fixed card height in the grid (cards of one grid align); taller when the extra parts are used. */
  height?: number | '100%';
  /** A row of its own just above the footer (e.g. an approval state), so every footer reads the same. */
  statusRow?: ReactNode;
  /** Left of the bottom strip (CardMetaBar), in place of the caption: e.g. statuses, as the Figma Card footerStart. */
  metaStart?: ReactNode;
  platforms: string[];
  /** Shown with the platforms, e.g. a team target. */
  target?: ReactNode;
  caption?: ReactNode;
  footerRight?: ReactNode;
  /** The card's main action (Install), at the right of the caption bar. */
  action?: ReactNode;
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

const isOverClampedText = (element: HTMLElement | null | undefined, { clientX, clientY }: MouseEvent) => {
  if (!element || element.scrollHeight <= element.clientHeight + 1) return false;
  const { left, right, top, bottom } = element.getBoundingClientRect();
  return clientX >= left && clientX <= right && clientY >= top && clientY <= bottom;
};

// Marketplace card laid out as the OpenCTI integrations cards, on the design system Card.
const MarketplaceCard: FunctionComponent<Props> = ({
  icon,
  title,
  overline,
  subtitle,
  description,
  chips,
  banner,
  tags,
  info,
  height = 280,
  statusRow,
  metaStart,
  platforms,
  target,
  caption,
  footerRight,
  action,
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
  const titleRef = useRef<HTMLDivElement>(null);
  const descriptionRef = useRef<HTMLDivElement>(null);
  const [fullText, setFullText] = useState<'title' | 'description' | null>(null);

  // The card link covers the text, so the hover is read on the card.
  const showFullText = (event: MouseEvent<HTMLElement>) => {
    if (isOverClampedText(titleRef.current?.querySelector<HTMLElement>('.truncate'), event)) setFullText('title');
    else if (isOverClampedText(descriptionRef.current, event)) setFullText('description');
    else setFullText(null);
  };

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
        footerRight={(footerRight || action) && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            minWidth: 0,
          }}
          >
            {/* Status chips give way (truncate) before the buttons do. */}
            <div style={{
              display: 'flex',
              minWidth: 0,
              overflow: 'hidden',
            }}
            >
              {footerRight}
            </div>
            <div style={{
              display: 'flex',
              flexShrink: 0,
            }}
            >
              {action}
            </div>
          </div>
        )}
        badge={badge}
        topLeft={topLeft}
        selected={selected}
        onClick={onClick}
        linkTo={linkTo}
        testId={testId}
      />
    );
  }

  // The description takes the room the other parts leave: three lines beside the info row, one
  // less for a banner and one less for a tags row, never under one, so cards of a grid stay level.
  let descriptionLines = subtitle || caption ? 3 : 4;
  if (info?.length) descriptionLines = Math.max(1, 3 - (banner ? 1 : 0) - (tags ? 1 : 0));
  const interaction = linkTo
    ? { href }
    : onClick && { onClick: () => onClick() };

  return (
    <Box
      data-testid={testId}
      onClickCapture={followLink}
      onMouseMove={showFullText}
      onMouseLeave={() => setFullText(null)}
      sx={{
        'height': '100%',
        // The doubled class beats the design system hover rules, which load after and use :has().
        '&& > div': {
          height,
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
        {banner && (
          // Full bleed, like CardMetaBar at the bottom: the card's 24px padding is taken back.
          <div style={{
            margin: '-24px -24px 0',
            position: 'relative',
            zIndex: 1,
          }}
          >
            {banner}
          </div>
        )}
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
          // No empty slot: without a top-right control the header text runs to the card's edge.
          action={topRight && (
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
          <Tooltip open={fullText === 'title'}>
            <TooltipTrigger asChild>
              <div ref={titleRef} style={{ minWidth: 0 }}>
                <CardTitle className="marketplace-card-title">{title}</CardTitle>
              </div>
            </TooltipTrigger>
            <TooltipContent>{title}</TooltipContent>
          </Tooltip>
          {subtitle && <CardSubtitle>{subtitle}</CardSubtitle>}
        </CardHeader>
        {tags && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 4,
            minWidth: 0,
          }}
          >
            {tags}
          </div>
        )}
        <Box sx={{
          flexGrow: 1,
          overflow: 'hidden',
          minHeight: 0,
        }}
        >
          {description && (
            <Tooltip open={fullText === 'description'}>
              <TooltipTrigger asChild>
                <CardContent ref={descriptionRef} clamp={descriptionLines}>{description}</CardContent>
              </TooltipTrigger>
              <TooltipContent>{description}</TooltipContent>
            </Tooltip>
          )}
          {info && info.length > 0 && (
            <dl style={{
              display: 'grid',
              gridTemplateColumns: `repeat(${info.length}, minmax(0, 1fr))`,
              gap: 8,
              margin: description ? '16px 0 0' : 0,
            }}
            >
              {info.map(item => (
                <div key={item.label} style={{ minWidth: 0 }}>
                  <Text as="dt" variant="content-caption" className="text-default-secondary">{item.label}</Text>
                  <Text
                    as="dd"
                    variant="content-compact"
                    style={{
                      margin: 0,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {item.value}
                  </Text>
                </div>
              ))}
            </dl>
          )}
        </Box>
        {statusRow && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            minWidth: 0,
          }}
          >
            {statusRow}
          </div>
        )}
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
                <Text variant="content-caption" className="text-default-secondary">
                  {`+${platforms.length - 4}`}
                </Text>
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
        {(caption || action || metaStart) && (
          <CardMetaBar style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
          }}
          >
            {metaStart
              ? (
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    minWidth: 0,
                    flex: 1,
                    position: 'relative',
                    zIndex: 1,
                  }}
                  >
                    {metaStart}
                  </div>
                )
              : <Text variant="content-caption" className="text-default-secondary">{caption}</Text>}
            {action && (
              <div style={{
                flexShrink: 0,
                position: 'relative',
                zIndex: 1,
              }}
              >
                {action}
              </div>
            )}
          </CardMetaBar>
        )}
      </Card>
    </Box>
  );
};

export default MarketplaceCard;
