import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';

/**
 * Why this file exists, and why it is product-side rather than in the library:
 * `NavbarItem` / `NavbarSubmenuItem` ignore their own `icon` / `showIcon` /
 * `chevron` props under `asChild`, because Radix's `Slot` cannot inject wrapper
 * elements into an arbitrary child. Every row here IS a router `Link`, so that
 * Ctrl/⌘-click and "open in new tab" work — which forces `asChild` on every
 * row, which in turn forces the consumer to re-declare the row anatomy.
 *
 * The class names below are the library's own, copied from `NavbarItem.tsx` /
 * `NavbarSubmenu.tsx`, not invented. They are kept in this one file so the debt
 * stays countable and disappears in one move when the library gains a link
 * destination — see fds-migration/LIBRARY-FEEDBACK.md, "NavbarItem has no link
 * destination".
 */

const iconSpanStyle = { display: 'inline-flex' } as const;

interface RowProps {
  icon?: ReactElement;
  label: ReactNode;
  /** Ancestor `Navbar` collapse state — not derivable here, the library
   *  computes it internally and does not expose it to `asChild` children. */
  collapsed: boolean;
}

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
