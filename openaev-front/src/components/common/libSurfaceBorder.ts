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
 * ## Why the ALIAS, and why it needs the scope
 *
 * The library exposes three names for this colour:
 *
 * | name | what it is | safe to read? |
 * | --- | --- | --- |
 * | `--border-elevation-subtle-soft-layer-1` | per-layer BASE, undiluted | no — renders opaque |
 * | `--border-elevation-subtle-soft-layer-1-transparency-15` | diluted variant | no — the 15 is in the NAME |
 * | `--border-elevation-subtle-soft` | alias, per-layer | yes |
 *
 * Naming the diluted variant is the trap this file used to fall into. Its
 * percentage is part of its identifier, so changing the opacity renames it —
 * the library has already done this once, 40% to 15%. An unresolvable `var()`
 * invalidates the whole `border` shorthand: measured, the border does not turn
 * a wrong colour, it becomes **`0px none`** and vanishes. No type error, no
 * lint error, no build error, no guard.
 *
 * The BASE carries no percentage but is not the painted colour: measured
 * `rgb(43, 79, 141)` opaque, against the Paper's 15% dilution.
 *
 * The alias carries no percentage AND is what the library's own Paper reads.
 * It is redeclared inside each `.layer-N` block, so it only resolves to layer 1
 * inside that scope — hence `LIB_SURFACE_LAYER`, which every consumer must
 * apply alongside. Without it the alias falls back to `:root`, i.e. layer 0,
 * which today happens to hold the same colour. Iso by accident is not iso: the
 * scope is what makes it iso by construction.
 *
 * `libSurfaceBorder.test.ts` reads the INSTALLED dist and fails if the library
 * stops defining the alias or stops scoping it per layer.
 */
const LIB_SURFACE_BORDER = '1px solid var(--border-elevation-subtle-soft)';

/** Scope class the border above needs. Apply both, never one alone. */
export const LIB_SURFACE_LAYER = 'layer-1';

export default LIB_SURFACE_BORDER;
