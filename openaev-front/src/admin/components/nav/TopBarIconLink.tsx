import { iconButtonVariants } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

/**
 * An icon button that is really a LINK.
 *
 * Why this exists. The library's `IconButton` renders a hard `<button>` and
 * accepts no `asChild`, so it cannot carry a navigation target. Four controls
 * in the top bar are genuine links — three router routes and one external XTM
 * One URL — and turning them into buttons with an `onClick` would silently
 * drop middle-click, ⌘/Ctrl-click "open in new tab", "copy link address" and
 * the browser's own status-bar preview. The designer's rule is
 * iso-functionality, so the anchor stays.
 *
 * What keeps this honest: the styling is not re-implemented. `iconButtonVariants`
 * is the library's own exported variant function — the very function
 * `IconButton` calls — so focus, hover and disabled states are the library's,
 * not a look-alike. `active` replays the one class `IconButton` adds on top of
 * the variants for its active state, which is why it is spelled out here
 * rather than guessed.
 *
 * Removal condition: when the library's `IconButton` accepts `asChild` (see
 * LIBRARY-FEEDBACK.md #21), delete this file and wrap `<Link>`/`<a>` in
 * `<IconButton asChild>` directly.
 */

/** The class `IconButton` adds for `active` under its default variant. */
const ACTIVE_CLASS = 'bg-filigran-brand-primary-transparency';

interface TopBarIconLinkProps {
  /** Required accessible label — icon-only controls have no visible text. */
  'aria-label': string;
  /** The glyph. Wrapped in an aria-hidden span, exactly as IconButton does. */
  'icon': ReactNode;
  /** Internal router route. Mutually exclusive with `href`. */
  'to'?: string;
  /** External URL, opened in a new tab. Mutually exclusive with `to`. */
  'href'?: string;
  /** Current-page state, mirroring IconButton's `active`. */
  'active'?: boolean;
  /**
   * Glyph colour, as a CSS value. Applied INLINE rather than as a class - see
   * the cascade-layer note below. Defaults to the library's brand token, the
   * colour `iconButtonVariants` would apply on a <button>.
   */
  'color'?: string;
  'id'?: string;
}

const TopBarIconLink: FunctionComponent<TopBarIconLinkProps> = ({
  'aria-label': ariaLabel,
  icon,
  to,
  href,
  active,
  color = 'var(--color-filigran-brand-primary)',
  id,
}) => {
  const classes = [
    // `tertiary` is the top bar's anatomy: transparent box, brand-coloured
    // glyph, brand-transparency hover - what the hand-rolled `sx` produced.
    // The default priority is `primary`, a FILLED brand button, so leaving it
    // out would repaint every icon in the bar solid blue.
    iconButtonVariants({ priority: 'tertiary' }),
    active ? ACTIVE_CLASS : '',
  ].filter(Boolean).join(' ');

  // The colour is INLINE, and it has to be. The library ships its utilities
  // inside a CSS cascade layer, and layered rules lose to UNLAYERED ones no
  // matter how specific they are. MUI's CssBaseline injects an unlayered
  // `body a { color: ... }`, so on an anchor the library's own
  // `text-filigran-brand-primary` is silently overridden - measured: the same
  // class list rendered blue on a <button> and white on an <a>. An inline
  // declaration is not in a layer, so it wins. See LIBRARY-FEEDBACK.md #24.
  const style = { color };

  // `aria-hidden` on the glyph, the label on the control: the same split the
  // library's IconButton enforces, so screen readers announce one name.
  const content = <span className="inline-flex shrink-0" aria-hidden="true">{icon}</span>;

  if (href) {
    return (
      <a
        id={id}
        aria-label={ariaLabel}
        className={classes}
        style={style}
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        {...(active !== undefined && { 'aria-current': active ? 'page' : undefined })}
      >
        {content}
      </a>
    );
  }

  return (
    <Link
      id={id}
      aria-label={ariaLabel}
      className={classes}
      style={style}
      to={to ?? ''}
      {...(active !== undefined && { 'aria-current': active ? 'page' : undefined })}
    >
      {content}
    </Link>
  );
};

export default TopBarIconLink;
