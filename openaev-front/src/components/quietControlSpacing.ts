/**
 * SPACING BETWEEN NEIGHBOURING CONTROLS. Two quiet controls — an icon button or
 * a tertiary button, the ones the library draws with no fill and no border —
 * sit 4px apart; any pair involving a primary or a secondary keeps 8px. The rows
 * themselves declare the 8px gap, so the quiet pair pulls back the difference
 * rather than every row having to know the rule.
 *
 * `border-0` plus either no fill or one of the library's own 10% washes (what an
 * `active` icon button wears at rest) is what both quiet kinds emit and neither of
 * the two loud ones does: a secondary carries a border, a primary a solid fill.
 */
const ACTIVE_FILLS = ['bg-filigran-brand-primary-transparency-10', 'bg-filigran-ia-secondary-transparency-10']
  .map(fill => `[class~="${fill}"]`)
  .join(', ');
// `~=` matches a whole class, so a `hover:`- or `active:`-prefixed one does not count:
// only the fill a pressed control actually wears at rest.
// `:is(button, a)` and not `button`: an icon button rendered `asChild` around a link — the
// top bar's own icons — is one of these controls and was keeping its neighbours at 8px.
const QUIET = `:is(button, a).border-0:not([class*="before:bg-"]):is(.bg-transparent, ${ACTIVE_FILLS})`;
const WRAPPED = `span:has(> ${QUIET})`;

/** Runs that are flush BY CONSTRUCTION are quiet too, and must not pull on each
 * other: a segmented control, a tab bar, a split button, the pagination arrows. */
const FLUSH = ':is([role="radiogroup"], [role="tablist"], .MuiTabs-root, .MuiButtonGroup-root, .MuiTablePagination-actions, .MuiPagination-root)';

const pairs = (left: string, right: string) => `${left} + ${right}`;

const quietControlSpacing: Record<string, { marginLeft: number }> = {};
for (const left of [QUIET, WRAPPED]) {
  for (const right of [QUIET, WRAPPED]) {
    quietControlSpacing[pairs(left, right)] = { marginLeft: -4 };
    // Written with the same classes as the rule above so it is strictly more
    // specific than it — a shorter selector would lose the cascade and the tab
    // bar would pull its own triggers together.
    quietControlSpacing[`${FLUSH} ${pairs(left, right)}`] = { marginLeft: 0 };
  }
}

export default quietControlSpacing;
