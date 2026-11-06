import { Checkbox, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
// fds:keep-mui the library Card (beta) renders a plain <a href>, not a router Link: navigating would reload the whole app
import { Card, CardActionArea, CardContent } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type MouseEvent, type ReactNode } from 'react';
import { Link } from 'react-router';

// Every value below is shared by all the cards views: change it here, never
// in one page card.
export const ENTITY_CARD_HEIGHT = 280;
const CARD_PADDING = 3;
const ICON_SIZE = 56;

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
  /** Framed in a 56px square: a logo `<img>` or an icon glyph. */
  icon: ReactNode;
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
}

// The one card of the cards views (Integrations, Threat Arsenal, Reporting,
// Custom dashboards, Security platforms): framed icon, overline, clamped title
// and description, footer, hover lift.
const EntityCard = ({
  testId,
  to,
  onClick,
  icon,
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
}: Props) => {
  const theme = useTheme();

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
    top: theme.spacing(CARD_PADDING),
  } as const;

  return (
    <Card
      variant="outlined"
      data-testid={testId}
      sx={{
        'position': 'relative',
        'height': ENTITY_CARD_HEIGHT,
        'borderRadius': 1,
        'display': 'flex',
        'border': `1px solid ${selected ? theme.palette.primary.main : alpha(theme.palette.text.primary, 0.08)}`,
        // Slightly lighter than the page, so cards pop against the hero. Read
        // from the palette slot this literal was copying, which moved to a
        // library token: the default surface shifts from #0c1524 to #13213e.
        'backgroundColor': theme.palette.mode === 'dark' ? theme.palette.background.secondary : undefined,
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
        }}
      >
        <CardContent
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            padding: CARD_PADDING,
            height: '100%',
            width: '100%',
          }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: theme.spacing(1.5),
            width: '100%',
          }}
          >
            <div style={{
              width: ICON_SIZE,
              height: ICON_SIZE,
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
            <div style={{
              flex: 1,
              minWidth: 0,
              // Room for the menu, which floats over this corner.
              paddingRight: menu ? theme.spacing(4.5) : 0,
            }}
            >
              {overline && (
                <Text
                  as="div"
                  variant="content-compact-medium"
                  className="uppercase"
                  style={{
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
                    variant="title-xs"
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

          <div style={{
            flexGrow: 1,
            overflow: 'hidden',
            width: '100%',
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(1),
          }}
          >
            {description && (
              <Text
                variant="content-base"
                className="text-default-secondary"
                style={{
                  display: '-webkit-box',
                  WebkitLineClamp: 4,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                {description}
              </Text>
            )}
            {children}
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

          {(footerStart || footerEnd) && (
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
      {menu && (
        <div style={{
          ...overIcon,
          right: theme.spacing(CARD_PADDING),
        }}
        >
          {menu}
        </div>
      )}
      {selection && (
        // Laid exactly over the icon frame, so the checkbox takes the place of
        // the icon while it is shown.
        <div
          className="entity-card-selection"
          style={{
            ...overIcon,
            left: theme.spacing(CARD_PADDING),
            width: ICON_SIZE,
            height: ICON_SIZE,
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
