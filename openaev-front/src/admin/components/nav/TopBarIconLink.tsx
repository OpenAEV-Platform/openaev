import { iconButtonVariants } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

// FDS-WORKAROUND #21: icon button that is really a link, library variants reused — remove when `IconButton` accepts `asChild` — see fds-migration/LIBRARY-FEEDBACK.md

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
  /** Glyph colour, as a CSS value. Applied inline (see below); defaults to the library's brand token. */
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
    // `tertiary` is the bar's anatomy; the default `primary` is a FILLED brand button.
    iconButtonVariants({ priority: 'tertiary' }),
    active ? ACTIVE_CLASS : '',
  ].filter(Boolean).join(' ');

  // FDS-WORKAROUND #24: colour inline, MUI's unlayered `body a` beats the layered utility — remove when layering wins — see fds-migration/LIBRARY-FEEDBACK.md
  const style = { color };

  // `aria-hidden` on the glyph, label on the control, as IconButton does.
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
