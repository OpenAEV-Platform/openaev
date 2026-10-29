import { buttonVariants, iconButtonVariants, searchFieldVariants } from '@filigran/design-system';
import { expect } from 'vitest';

/**
 * Scope rule (designer, checkpoint round 2): wherever the library ships a
 * component, the pilot uses it. These helpers make that rule assertable
 * instead of reviewable by eye.
 *
 * They assert on BOTH sides, and both halves matter:
 *
 *  - positively, the element carries the class the library's own variant
 *    function produces, so the control is styled by the library's contract
 *    rather than by a look-alike;
 *  - negatively, the element carries no MUI-generated class, because a MUI
 *    control that happens to be painted the same way still brings MUI's
 *    focus, hover and selected states - which is precisely the designer
 *    feedback this rule exists to remove.
 *
 * The single exception the designer maintained is icon GLYPHS, which stay on
 * MUI for now (same arbitration as the Navbar pilot): a `<svg>` descendant
 * carrying a MuiSvgIcon class is therefore not a violation.
 */

/**
 * ANY MUI symbol counts, not a hand-kept list of them.
 *
 * The list this replaced had to be extended three times — `Chip` at review
 * #7305, `Badge` once the library shipped one, progress after that — and each
 * time the control had been sitting in the bar for weeks, compliant-looking
 * because nothing asked about it. An allowlist of what is FORBIDDEN can only
 * ever be behind. So the rule is inverted: every `Mui<Symbol>-` class is a
 * violation unless it is explicitly allowed below.
 */
const ANY_MUI_CLASS = /\bMui[A-Z][A-Za-z]*-/;

/** Icon glyphs stay MUI — the designer's standing exception since the Navbar pilot. */
const MUI_GLYPH_CLASS = /\bMuiSvgIcon-/;

/**
 * The one tolerated surface, dated and conditioned.
 *
 * 2026-08-13 — the bulk-operations panel is a MUI `Popover`, because the library
 * has none: PR #105 added a Popover primitive but kept it internal (it backs
 * `Combobox`), and `require()` on the built entry point returns `undefined` for
 * `Popover`/`PopoverTrigger`/`PopoverContent`. The whole panel subtree is exempt,
 * not just the paper: its rows are laid out by MUI `Box`, which only exists
 * because the surface does.
 *
 * REMOVAL CONDITION: the day the library exports a `Popover`, delete this and the
 * guard reddens on the panel until it is rebuilt. See
 * fds-migration/LIBRARY-FEEDBACK.md #22 — the only item still open there.
 */
const EXEMPT_SUBTREE_SELECTOR = '[class*="MuiPopover-"]';

const isInsideExemptSubtree = (element: Element): boolean => Boolean(element.closest(EXEMPT_SUBTREE_SELECTOR));

/** Every MUI symbol on the element itself, minus the glyph exception. */
const muiControlClassesOf = (element: Element): string[] => String(element.getAttribute('class') ?? '')
  .split(/\s+/)
  .filter(cls => ANY_MUI_CLASS.test(cls))
  .filter(cls => !MUI_GLYPH_CLASS.test(cls));

/**
 * Asserts the element is styled by the library and by no MUI control class.
 *
 * Only the STATE classes are required, not the full variant output. The
 * library composes its classes through tailwind-merge, so a legitimate prop
 * can drop one: `fullWidth` replaces the field's `w-55` with `w-full`, and a
 * Radix `asChild` trigger merges its own base over `bg-transparent`. Requiring
 * every class would fail on correct code.
 *
 * The state classes - focus ring, hover, active, disabled - are the ones that
 * cannot be dropped without changing behaviour, and they are exactly what the
 * designer asked to see matching the library's documentation. A MUI control
 * painted to look right carries none of them, so the assertion still fails on
 * a look-alike.
 */
const STATE_CLASS = /^(focus-visible:|focus-within:|hover:|active:|disabled:|data-\[disabled\]:)/;

export const expectLibraryStyled = (
  element: Element | null | undefined,
  expectedClass: string,
  what: string,
) => {
  expect(element, `${what}: element not found`).toBeTruthy();
  const actual = String(element!.getAttribute('class') ?? '').split(/\s+/).filter(Boolean);

  const required = expectedClass.split(/\s+/).filter(cls => STATE_CLASS.test(cls));
  expect(required.length, `${what}: no state class to assert on`).toBeGreaterThan(0);
  const missing = required.filter(cls => !actual.includes(cls));
  expect(missing, `${what}: missing library state classes`).toEqual([]);

  expect(muiControlClassesOf(element!), `${what}: still carries MUI control classes`).toEqual([]);
};

export const expectLibraryIconButton = (
  element: Element | null | undefined,
  what: string,
  variants: Parameters<typeof iconButtonVariants>[0] = { priority: 'tertiary' },
) => expectLibraryStyled(element, iconButtonVariants(variants), what);

export const expectLibraryButton = (
  element: Element | null | undefined,
  what: string,
  variants: Parameters<typeof buttonVariants>[0] = {},
) => expectLibraryStyled(element, buttonVariants(variants), what);

export const expectLibrarySearchField = (element: Element | null | undefined, what: string) =>
  expectLibraryStyled(element, searchFieldVariants({}), what);

/** No MUI control anywhere inside the subtree (icon glyphs excepted). */
export const expectNoMuiControls = (root: Element, what: string) => {
  const offenders = Array.from(root.querySelectorAll('*'))
    .filter(el => muiControlClassesOf(el).length > 0)
    .filter(el => !isInsideExemptSubtree(el))
    .map(el => `${el.tagName.toLowerCase()}.${muiControlClassesOf(el)[0]}`);
  expect(offenders, `${what}: MUI controls still present`).toEqual([]);
};
