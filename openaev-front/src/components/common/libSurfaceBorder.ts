/**
 * Border of the design-system `Paper`, for surfaces that stay on MUI.
 *
 * Some container surfaces cannot be converted: the surface IS the control — a
 * real link or a click target — and swapping it for the library `Paper` would
 * cost the navigation (cmd-click, open in a new tab) while the library has no
 * `Card` yet. Leaving them untouched next to converted panels makes a screen
 * look half-migrated, because the two borders differ.
 *
 * The arbitration is: **when the surface is the control, align the border
 * instead of converting.** The screen reads as one, and no behaviour is lost.
 *
 * This is the CSS variable the library's own Paper paints, so it follows the
 * theme — and a customer theme override — without any product-side branching.
 */
const LIB_SURFACE_BORDER = '1px solid var(--border-elevation-subtle-soft-layer-1-transparency-15)';

export default LIB_SURFACE_BORDER;
