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

// This list grows every time a control turns out to have been invisible to the
// rule rather than compliant with it: `Chip` was added at review #7305 (the bar's
// "EE DEV LICENSE" tag had survived several checkpoints), `Badge` once the
// library shipped one (#114) and the two MUI badges in the bar became debt with
// a replacement rather than a filed gap.
const MUI_CONTROL_CLASS = /\bMui(ButtonBase|Button|IconButton|Chip|Badge|CircularProgress|LinearProgress|TextField|InputBase|OutlinedInput|FormControl|Menu|MenuItem|Tooltip|Divider)-/;

/**
 * Controls that ARE detected above but are tolerated for now, each with the
 * condition that retires the exemption. Listing them here rather than leaving
 * them out of `MUI_CONTROL_CLASS` is deliberate: the day the condition is met,
 * deleting one entry turns the guard red and the migration becomes mandatory
 * instead of optional.
 */
const EXEMPT_MUI_CONTROLS: {
  pattern: RegExp;
  until: string;
}[] = [
  {
    // Added 2026-08-13 (Sandy, R2). The running-operations spinner in the bar and
    // the per-operation bars in its panel. The library ships no progress
    // component at pin 8798cbb — see fds-migration/LIBRARY-FEEDBACK.md #22.
    pattern: /\bMui(CircularProgress|LinearProgress)-/,
    until: 'the library ships a Progress component (LIBRARY-FEEDBACK #22)',
  },
];

/**
 * Every MUI class on the element itself, ignoring icon glyphs (the standing
 * exception) and the dated exemptions above.
 */
const muiControlClassesOf = (element: Element): string[] => String(element.getAttribute('class') ?? '')
  .split(/\s+/)
  .filter(cls => MUI_CONTROL_CLASS.test(cls))
  .filter(cls => !EXEMPT_MUI_CONTROLS.some(exempt => exempt.pattern.test(cls)));

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
    .map(el => `${el.tagName.toLowerCase()}.${muiControlClassesOf(el)[0]}`);
  expect(offenders, `${what}: MUI controls still present`).toEqual([]);
};
