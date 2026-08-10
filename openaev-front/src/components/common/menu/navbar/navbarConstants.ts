/**
 * Widths of the navigation rail, mirroring the design system Navbar's own
 * `w-45` / `w-12`. They are shared rather than duplicated because three
 * things must agree to the pixel: the rail itself, the in-flow spacer that
 * holds its place in the shell, and the Header's left offset.
 */
export const NAV_OPEN_WIDTH = 180;
export const NAV_COLLAPSED_WIDTH = 48;

/**
 * The width transition the library applies to the rail
 * (`transition-[width] duration-150`), measured in the browser as
 * `width 0.15s cubic-bezier(0.4, 0, 0.2, 1)`. The spacer replays it so the
 * content never lags behind or races ahead of the rail while it animates.
 */
export const NAV_WIDTH_TRANSITION = 'width 0.15s cubic-bezier(0.4, 0, 0.2, 1)';
