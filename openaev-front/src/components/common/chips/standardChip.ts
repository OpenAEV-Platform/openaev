/**
 * The single chip look of @filigran/design-system 1.13 (filigran-design-system #436, #439),
 * for the hand-drawn and MUI neutral chips that are not the design-system Chip: the neutral
 * tone fill and a 1px hairline at 30% of the neutral tone ink. There is no pastel variant
 * any more.
 */
export const STANDARD_CHIP_FILL = 'var(--color-feedback-neutral-secondary-transparency-30)';

/** The 1px hairline of a neutral chip: the neutral tone ink at 30%, as the design-system Chip. */
export const STANDARD_CHIP_EDGE = '1px solid color-mix(in srgb, var(--color-feedback-neutral-tertiary) 30%, transparent)';

/**
 * Edge of a chip that still waits for its value (an incomplete filter): the neutral tone
 * ink in full, no fill, so it reads apart from a complete chip.
 */
export const PENDING_CHIP_EDGE = '1px solid var(--color-feedback-neutral-tertiary)';

/** Style of a hand-drawn or MUI neutral chip. Use it with `variant="outlined"` on an MUI Chip. */
export const neutralChipSx = () => ({
  border: STANDARD_CHIP_EDGE,
  backgroundColor: STANDARD_CHIP_FILL,
});
