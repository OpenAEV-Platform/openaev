import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardInfo,
  CardInfoGroup,
  CardOverview,
  CardTitle,
  Checkbox,
  Thumbnail,
} from '@filigran/design-system';
import { type MouseEvent, type ReactNode, useState } from 'react';
import { Link } from 'react-router';

// Height of the loading placeholders. A card itself fits its content, and the
// grid gives every card of a page the height of the tallest one (CardGrid).
export const ENTITY_CARD_HEIGHT = 256;

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
  /** An icon glyph, centered at 24px in the 48px thumbnail. */
  icon?: ReactNode;
  /** A logo image; it fills the 48px thumbnail. Wins over `icon`. */
  image?: {
    src: string;
    alt: string;
  };
  /** The line above the title (the entity type). */
  overline?: string;
  title: string;
  /** Clamped to 3 lines. */
  description?: string;
  /** Extra content of the body, under the description. */
  children?: ReactNode;
  /** The ⋮ menu, at the end of the footer. */
  menu?: ReactNode;
  /** Bulk selection checkbox, the header's single action. */
  selection?: EntityCardSelection;
  /** Start of the footer: status and metadata. */
  footerStart?: ReactNode;
  /** End of the footer, before the menu: actions. */
  footerEnd?: ReactNode;
  /** The entity is the one currently opened (e.g. in a drawer). */
  selected?: boolean;
  /** Row of chips right under the header. */
  tags?: ReactNode;
  /** Info line under the description, e.g. { label: 'Updated:', value: date }. */
  meta?: {
    label: string;
    value: string;
  };
  /** false drops the footer (a view whose Figma card hides its CardFooter, e.g. Findings). */
  footer?: boolean;
}

// The one card of the cards views (Integrations, Threat Arsenal, Reporting,
// Custom dashboards, Security platforms, Findings): the library Card v2
// (Figma Design_System_2026, Card_Header + CardFooter) filled from the props.
// The library owns the surface, the padding, the hover, focus and selected
// states and the single link the whole card resolves to; controls in the
// header and the footer stay separate tab stops above it.
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
  menu,
  selection,
  footerStart,
  footerEnd,
  selected = false,
  tags,
  meta,
  footer = true,
}: Props) => {
  const [hovered, setHovered] = useState(false);
  const showSelection = selection != null && (hovered || selection.alwaysVisible || selection.checked);

  const thumbnail = image
    ? (
        <Thumbnail elevation={2}>
          <img
            src={image.src}
            alt={image.alt}
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'contain',
            }}
          />
        </Thumbnail>
      )
    : icon && <Thumbnail elevation={2}>{icon}</Thumbnail>;

  return (
    <Card
      data-testid={testId}
      // A router link keeps the app loaded; a card without a detail page
      // opens something in place, or is a plain surface.
      to={to}
      linkComponent={to != null ? Link : undefined}
      onClick={to == null ? onClick : undefined}
      selected={selected}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <CardHeader
        icon={thumbnail || undefined}
        action={selection && (
          // Shown on hover, and kept once a selection has started. Faded, not
          // hidden, so it stays reachable from the keyboard.
          <div
            style={{
              display: 'flex',
              opacity: showSelection ? 1 : 0,
            }}
            onClick={(event) => {
              event.stopPropagation();
              selection.onToggle(event);
            }}
          >
            <Checkbox checked={selection.checked} aria-label={selection.label} />
          </div>
        )}
      >
        {overline && <CardOverview>{overline}</CardOverview>}
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      {tags}
      {description && <CardDescription>{description}</CardDescription>}
      {children}
      {meta && (
        <CardInfoGroup>
          <CardInfo label={meta.label} value={meta.value} />
        </CardInfoGroup>
      )}
      {footer && (
        <CardFooter
          start={footerStart}
          end={(footerEnd || menu) && (
            <>
              {footerEnd}
              {menu}
            </>
          )}
        />
      )}
    </Card>
  );
};

export default EntityCard;
