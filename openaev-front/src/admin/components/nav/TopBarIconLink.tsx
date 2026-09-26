import { iconButtonVariants } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

// FDS-WORKAROUND #21: icon button that is really a link, library variants reused — remove when `IconButton` accepts `asChild` — see fds-migration/LIBRARY-FEEDBACK.md

/** The background `IconButton` paints for `active`, as the library's own token. */
const SELECTED_BACKGROUND = 'var(--color-filigran-brand-primary-transparency-10)';

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
  /** Forwarded to the anchor so a wrapping `Badge` can describe the control itself. */
  'aria-describedby'?: string;
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
  'aria-describedby': ariaDescribedBy,
}) => {
  // `tertiary` is the bar's anatomy; the default `primary` is a FILLED brand button.
  const classes = iconButtonVariants({ priority: 'tertiary' });

  // FDS-WORKAROUND #24: colour and selected background inline, layered utilities lose here — see fds-migration/LIBRARY-FEEDBACK.md
  const style = {
    color,
    ...(active && { backgroundColor: SELECTED_BACKGROUND }),
  };

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
        aria-describedby={ariaDescribedBy}
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
      aria-describedby={ariaDescribedBy}
      {...(active !== undefined && { 'aria-current': active ? 'page' : undefined })}
    >
      {content}
    </Link>
  );
};

export default TopBarIconLink;
