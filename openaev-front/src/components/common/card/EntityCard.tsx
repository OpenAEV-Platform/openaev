import { Checkbox, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
// fds:keep-mui the library Card (beta) renders a plain <a href>, not a router Link: navigating would reload the whole app
import { Card, CardActionArea, CardContent } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type MouseEvent, type ReactNode } from 'react';
import { Link } from 'react-router';

import { fdsLayerClass } from '../../../utils/fdsLayer';

// Every value below is shared by all the cards views: change it here, never
// in one page card.
export const ENTITY_CARD_HEIGHT = 280;
const CARD_PADDING = 3;
const ICON_SIZE = 56;

// PoC of the Design System 2026 card (Figma: Design_System_2026, Card_Header +
// CardFooter): 48px thumbnail, tags under the header, description on 3 lines,
// an info line, and a footer band holding the status and the ⋮ menu. Each view
// passes the height of its Figma card.
export const ENTITY_CARD_DS2026_HEIGHT = 256;
const DS2026_THUMBNAIL_SIZE = 48;
const DS2026_ICON_SIZE = 24;

interface EntityCardSelection {
  checked: boolean;
  /** Keeps the checkbox shown once a selection has started, not only on hover. */
  alwaysVisible: boolean;
  onToggle: (event: MouseEvent<HTMLElement>) => void;
  /** Accessible name of the checkbox (usually the card title). */
  label: string;
}

interface Props {
  testId: string;
  /** Detail page of the entity: the card is a real router link (ctrl/cmd+click opens a new tab). */
  to?: string;
  /** Used instead of `to` when the card opens something in place (a drawer). */
  onClick?: () => void;
  /** Default layout: framed in a 56px square, a logo `<img>` or an icon glyph. `ds2026`: an icon glyph, centered at 24x24 in the framed 48px thumbnail. */
  icon?: ReactNode;
  /** `ds2026`: a logo image; it fills the whole 48px thumbnail, unframed. Wins over `icon`. */
  image?: {
    src: string;
    alt: string;
  };
  /** Small uppercase label above the title (the entity type). */
  overline?: string;
  title: string;
  /** Clamped to 4 lines. */
  description?: string;
  /** Extra content of the body, under the description. */
  children?: ReactNode;
  /** One line of secondary text at the bottom of the body (e.g. the last update). */
  caption?: string;
  /** Non-interactive indicator on the top right of the header (e.g. who supports a connector). */
  badge?: ReactNode;
  /** Interactive control on the top right (the ⋮ menu). Rendered outside the card link. */
  menu?: ReactNode;
  /** Bulk selection checkbox, laid over the icon. Rendered outside the card link. */
  selection?: EntityCardSelection;
  /** Left side of the footer, usually chips. */
  footerStart?: ReactNode;
  /** Right side of the footer. It sits inside the card link: its handlers must call preventDefault() and stopPropagation(). */
  footerEnd?: ReactNode;
  /** The entity is the one currently opened (e.g. in a drawer). */
  selected?: boolean;
  /** `ds2026`: layout of the Design System 2026 card (PoC). */
  layout?: 'default' | 'ds2026';
  /** Row of chips right under the header. Only rendered by the `ds2026` layout. */
  tags?: ReactNode;
  /** Card height of the `ds2026` layout; every card of a view must share it. `auto`: fits the content (the grid still aligns a row). */
  ds2026Height?: number | 'auto';
  /** `ds2026`: info line under the description, e.g. { label: 'Updated:', value: date }. */
  meta?: {
    label: string;
    value: string;
  };
  /** `ds2026`: false drops the footer band (a view whose Figma card hides its CardFooter, e.g. Findings). */
  footer?: boolean;
}

// The one card of the cards views (Integrations, Threat Arsenal, Reporting,
// Custom dashboards, Security platforms): framed icon, overline, clamped title
// and description, footer, hover lift.
const EntityCard = ({
  testId,
  to,
  onClick,
  icon,
  image,
  overline,
  title,
  description,
  children,
  caption,
  badge,
  menu,
  selection,
  footerStart,
  footerEnd,
  selected = false,
  layout = 'default',
  tags,
  ds2026Height = ENTITY_CARD_DS2026_HEIGHT,
  meta,
  footer = true,
}: Props) => {
  const theme = useTheme();
  const ds2026 = layout === 'ds2026';
  const iconSize = ds2026 ? DS2026_THUMBNAIL_SIZE : ICON_SIZE;
  const cardPaddingX = CARD_PADDING;
  // DS 2026 with a footer band: 16px top and bottom. Without it (Findings),
  // the Figma body padding: 24px on every side.
  const cardPaddingY = ds2026 && footer ? 2 : CARD_PADDING;
  const defaultBackground = theme.palette.mode === 'dark' ? theme.palette.background.secondary : undefined;
  const cardBackground = ds2026 ? 'var(--bg-elevation-default)' : defaultBackground;

  // A Link with an empty `to` would still render href="" (surprising
  // navigation for assistive tech / open-in-new-tab); render a plain action
  // area when the card has no detail page.
  const actionAreaProps = () => {
    if (to != null) {
      return {
        component: Link,
        to,
      };
    }
    if (onClick) {
      return { onClick };
    }
    return { disabled: true };
  };

  // Above the absolute action area, at the place of the icon frame.
  const overIcon = {
    position: 'absolute',
    top: theme.spacing(cardPaddingY),
  } as const;

  return (
    <Card
      variant="outlined"
      data-testid={testId}
      // DS 2026: the body is a layer-1 surface, its footer band a layer-2 one.
      className={ds2026 ? fdsLayerClass(1) : undefined}
      sx={{
        'position': 'relative',
        'height': ds2026 ? ds2026Height : ENTITY_CARD_HEIGHT,
        'borderRadius': 1,
        'display': 'flex',
        'flexDirection': ds2026 ? 'column' : 'row',
        'border': `1px solid ${selected ? theme.palette.primary.main : alpha(theme.palette.text.primary, 0.08)}`,
        // Slightly lighter than the page, so cards pop against the hero. Read
        // from the palette slot this literal was copying, which moved to a
        // library token: the default surface shifts from #0c1524 to #13213e.
        'backgroundColor': cardBackground,
        'transition': 'transform 0.3s ease-in-out, border-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out',
        // On the card itself, so it still applies when the action area is
        // disabled (cards without a detail page) and when the pointer is on
        // the menu or the checkbox, which sit outside the action area.
        '&:hover': {
          transform: 'translateY(-2px)',
          borderColor: alpha(theme.palette.primary.main, 0.3),
          boxShadow: `0 0 30px ${alpha(theme.palette.primary.main, 0.12)}`,
        },
        // The checkbox shows on hover, and stays once a selection has started.
        '& .entity-card-selection': {
          opacity: selection && (selection.alwaysVisible || selection.checked) ? 1 : 0,
          transition: theme.transitions.create('opacity'),
        },
        '&:hover .entity-card-selection': { opacity: 1 },
      }}
    >
      <CardActionArea
        {...actionAreaProps()}
        sx={{
          display: 'flex',
          alignItems: 'stretch',
          flex: ds2026 ? 1 : undefined,
          minHeight: 0,
        }}
      >
        <CardContent
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            paddingX: cardPaddingX,
            paddingY: cardPaddingY,
            height: '100%',
            width: '100%',
          }}
        >
          <div style={{
            display: 'flex',
            alignItems: ds2026 ? 'center' : 'flex-start',
            gap: theme.spacing(ds2026 ? 2 : 1.5),
            width: '100%',
          }}
          >
            {ds2026 && image && (
              // DS 2026 thumbnail, image: the logo fills the 48px square.
              <img
                src={image.src}
                alt={image.alt}
                style={{
                  width: iconSize,
                  height: iconSize,
                  flexShrink: 0,
                  objectFit: 'contain',
                  borderRadius: 4,
                }}
              />
            )}
            {ds2026 && !image && (
              // DS 2026 thumbnail, icon: a 24px glyph centered in a framed 48px square.
              <div
                className="bg-elevation-highlight border border-elevation-subtle-soft"
                style={{
                  width: iconSize,
                  height: iconSize,
                  flexShrink: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: 4,
                  color: theme.palette.text.primary,
                }}
              >
                <div style={{
                  width: DS2026_ICON_SIZE,
                  height: DS2026_ICON_SIZE,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  overflow: 'hidden',
                }}
                >
                  {icon}
                </div>
              </div>
            )}
            {!ds2026 && (
              <div style={{
                width: iconSize,
                height: iconSize,
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: theme.shape.borderRadius,
                color: theme.palette.primary.main,
                border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                backgroundColor: alpha(theme.palette.text.primary, 0.04),
              }}
              >
                {icon}
              </div>
            )}
            <div style={{
              flex: 1,
              minWidth: 0,
              // Room for the menu (default) or the checkbox (ds2026), which
              // float over this corner.
              paddingRight: (menu && !ds2026) || (selection && ds2026) ? theme.spacing(4.5) : 0,
            }}
            >
              {overline && (
                <Text
                  as="div"
                  variant={ds2026 ? 'content-compact' : 'content-compact-medium'}
                  className={ds2026 ? 'text-filigran-brand-primary uppercase' : 'uppercase'}
                  style={ds2026
                    ? undefined
                    : {
                        color: theme.palette.primary.main,
                        letterSpacing: '0.06em',
                        marginBottom: theme.spacing(0.5),
                      }}
                >
                  {overline}
                </Text>
              )}
              <Tooltip>
                <TooltipTrigger asChild>
                  <Text
                    as="div"
                    variant={ds2026 ? 'title-sm' : 'title-xs'}
                    className="break-words"
                    style={{
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                    }}
                  >
                    {title}
                  </Text>
                </TooltipTrigger>
                {title && <TooltipContent side="bottom" align="start">{title}</TooltipContent>}
              </Tooltip>
            </div>
            {badge}
          </div>

          {ds2026 && tags && (
            <div style={{
              width: '100%',
              overflow: 'hidden',
            }}
            >
              {tags}
            </div>
          )}

          <div style={{
            flexGrow: 1,
            overflow: 'hidden',
            width: '100%',
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(ds2026 ? 2 : 1),
          }}
          >
            {description && (
              <Text
                variant="content-base"
                className="text-default-secondary"
                style={{
                  display: '-webkit-box',
                  WebkitLineClamp: ds2026 ? 3 : 4,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                {description}
              </Text>
            )}
            {children}
            {ds2026 && meta && (
              <div style={{
                display: 'flex',
                alignItems: 'baseline',
                gap: theme.spacing(1),
                minWidth: 0,
              }}
              >
                <Text variant="content-compact" className="text-default-secondary" style={{ flexShrink: 0 }}>
                  {meta.label}
                </Text>
                <Text variant="content-compact" className="text-default-primary truncate">
                  {meta.value}
                </Text>
              </div>
            )}
            {caption && (
              <Text
                variant="content-compact"
                className="text-default-secondary"
                style={{ marginTop: 'auto' }}
              >
                {caption}
              </Text>
            )}
          </div>

          {!ds2026 && (footerStart || footerEnd) && (
            <div style={{
              display: 'flex',
              alignItems: 'flex-end',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: theme.spacing(1),
              width: '100%',
            }}
            >
              {footerStart}
              {footerEnd && (
                <div style={{
                  flexShrink: 0,
                  marginLeft: 'auto',
                }}
                >
                  {footerEnd}
                </div>
              )}
            </div>
          )}
        </CardContent>
      </CardActionArea>

      {/* Interactive controls are siblings of the action area, never inside
          it: the menu opens dialogs in a portal, and React would bubble their
          clicks up to the card link. */}
      {ds2026 && footer && (
        // The CardFooter of the Design System 2026: status on the left, the
        // ⋮ menu on the right, on a band separated from the body.
        <div
          className={`${fdsLayerClass(2)} bg-elevation-default border-t border-elevation-subtle-soft`}
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: theme.spacing(1),
            minHeight: 56,
            padding: theme.spacing(2, cardPaddingX),
          }}
        >
          <div style={{ minWidth: 0 }}>
            {footerStart}
          </div>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
            flexShrink: 0,
          }}
          >
            {footerEnd}
            {menu}
          </div>
        </div>
      )}
      {menu && !ds2026 && (
        <div style={{
          ...overIcon,
          right: theme.spacing(CARD_PADDING),
        }}
        >
          {menu}
        </div>
      )}
      {selection && ds2026 && (
        // DS 2026: the checkbox sits on the top right corner of the header.
        <div
          className="entity-card-selection"
          style={{
            position: 'absolute',
            top: theme.spacing(cardPaddingY),
            right: theme.spacing(cardPaddingX),
            display: 'flex',
          }}
          onClick={(event) => {
            event.stopPropagation();
            selection.onToggle(event);
          }}
        >
          <Checkbox checked={selection.checked} aria-label={selection.label} />
        </div>
      )}
      {selection && !ds2026 && (
        // Laid exactly over the icon frame, so the checkbox takes the place of
        // the icon while it is shown.
        <div
          className="entity-card-selection"
          style={{
            ...overIcon,
            left: theme.spacing(cardPaddingX),
            width: iconSize,
            height: iconSize,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: theme.shape.borderRadius,
            backgroundColor: theme.palette.background.paper,
            border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
          }}
          onClick={(event) => {
            event.stopPropagation();
            selection.onToggle(event);
          }}
        >
          <Checkbox checked={selection.checked} aria-label={selection.label} />
        </div>
      )}
    </Card>
  );
};

export default EntityCard;
