/**
 * A translucent version of a colour, computed in CSS rather than in JS.
 *
 * The status and criticality ladders read from design-system tokens, so the
 * value handed in is often `var(--…)`. MUI's `alpha()` parses its argument in
 * JavaScript and throws on a custom property ("Unsupported `var(--…)` color"),
 * which takes the whole component down with it. `color-mix` resolves the
 * variable at paint time instead, and accepts a plain hex just the same.
 */
// eslint-disable-next-line import/prefer-default-export -- a named helper reads clearer at the call sites
export const tint = (colour: string, percent: number): string =>
  `color-mix(in srgb, ${colour} ${percent}%, transparent)`;
