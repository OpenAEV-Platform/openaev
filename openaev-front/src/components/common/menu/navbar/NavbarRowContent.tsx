import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';

/**
 * `NavbarItem` / `NavbarSubmenuItem` document that `asChild` ignores their own
 * `icon` / `showIcon` / `chevron` props: Radix's `Slot` cannot inject wrapper
 * elements inside an arbitrary child, so the row's internals have to be
 * composed by the consumer. Every row here IS a router `Link` (so Ctrl/⌘-click
 * and "open in new tab" work), which means every row goes through `asChild` and
 * therefore has to re-declare the anatomy the library would otherwise own.
 *
 * The class names below are the library's own — see `NavbarItem.tsx` /
 * `NavbarSubmenu.tsx` — reproduced here rather than invented. Reported upstream:
 * see fds-migration/LIBRARY-FEEDBACK.md, "NavbarItem has no link destination".
 */

const iconSpanStyle = { display: 'inline-flex' } as const;

interface RowProps {
  icon?: ReactElement;
  label: ReactNode;
  /** Ancestor `Navbar` collapse state — the library computes this internally. */
  collapsed: boolean;
}

/** Anatomy of a `NavbarItem` row (leading icon + truncating label). */
export const NavbarItemContent: FunctionComponent<RowProps> = ({ icon, label, collapsed }) => (
  <>
    {icon && (
      <span
        className={`inline-flex shrink-0 ${collapsed ? 'text-default-primary mr-0.5' : 'text-default-secondary'}`}
        style={iconSpanStyle}
        aria-hidden="true"
      >
        {icon}
      </span>
    )}
    <span className={`flex-1 truncate text-left ${collapsed ? 'sr-only' : ''}`}>{label}</span>
  </>
);

/** Anatomy of a `NavbarSubmenuItem` row — never hides its label when collapsed. */
export const NavbarSubmenuItemContent: FunctionComponent<Omit<RowProps, 'collapsed'>> = ({ icon, label }) => (
  <>
    {icon && (
      <span className="inline-flex shrink-0 text-default-secondary" style={iconSpanStyle} aria-hidden="true">
        {icon}
      </span>
    )}
    <span className="flex-1 truncate text-left">{label}</span>
  </>
);
